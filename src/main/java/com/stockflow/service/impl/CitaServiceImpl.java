package com.stockflow.service.impl;

import com.stockflow.dto.*;
import com.stockflow.entity.*;
import com.stockflow.exception.BadRequestException;
import com.stockflow.exception.ResourceNotFoundException;
import com.stockflow.repository.*;
import com.stockflow.service.CitaService;
import com.stockflow.service.TenantService;
import com.stockflow.util.TenantContext;
import org.springframework.data.jpa.domain.Specification;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Duration;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Set;

@Slf4j
@Service
@RequiredArgsConstructor
public class CitaServiceImpl implements CitaService {

    /** Duración máxima permitida para una cita (8 horas). */
    private static final long MAX_DURACION_HORAS = 8L;

    /** Transiciones de estado válidas: de estado actual → estados destino permitidos. */
    private static final java.util.Map<EstadoCita, Set<EstadoCita>> TRANSICIONES = java.util.Map.of(
            EstadoCita.PENDIENTE,    Set.of(EstadoCita.CONFIRMADA, EstadoCita.CANCELADA, EstadoCita.NO_ASISTIO),
            EstadoCita.CONFIRMADA,   Set.of(EstadoCita.EN_ATENCION, EstadoCita.CANCELADA, EstadoCita.NO_ASISTIO),
            EstadoCita.EN_ATENCION,  Set.of(EstadoCita.ATENDIDA, EstadoCita.CANCELADA),
            EstadoCita.ATENDIDA,     Set.of(),
            EstadoCita.CANCELADA,    Set.of(EstadoCita.PENDIENTE),
            EstadoCita.NO_ASISTIO,   Set.of(EstadoCita.PENDIENTE)
    );

    private final CitaRepository            citaRepository;
    private final ClienteRepository         clienteRepository;
    private final MascotaRepository         mascotaRepository;
    private final UsuarioTenantRepository   usuarioTenantRepository;
    private final SucursalRepository        sucursalRepository;
    private final TenantService             tenantService;

    // ── Crear ────────────────────────────────────────────────────────────────

    @Override
    @Transactional
    public CitaDTO crear(CrearCitaRequestDTO dto) {
        String tenantId = TenantContext.getCurrentTenant();
        tenantService.requireRubro(tenantId, "VETERINARIA");

        // Validaciones de negocio primero
        validarFechas(dto.getFechaInicio(), dto.getFechaFin(), true);
        validarCliente(dto.getClienteId(), tenantId);
        validarMascotaDelTenant(dto.getMascotaId(), tenantId);

        if (dto.getUsuarioTenantId() != null) {
            validarProfesionalParaTipo(dto.getUsuarioTenantId(), tenantId, dto.getTipo());
            validarSolapamiento(tenantId, dto.getUsuarioTenantId(), dto.getFechaInicio(), dto.getFechaFin(), -1L);
        }

        // Resolver el UsuarioTenant del usuario autenticado — política fail-closed
        Long usuarioId = TenantContext.getCurrentUserId();
        UsuarioTenant creadoPorUT = usuarioTenantRepository
                .findByUsuarioIdAndTenantIdAndActivoTrue(usuarioId, tenantId)
                .orElseThrow(() -> new AccessDeniedException(
                        "El usuario autenticado no tiene membresía activa en el tenant actual"));
        Long creadoPorId = creadoPorUT.getId();

        // Determinar la sucursal efectiva; para VETERINARIO/GROOMER se fuerza la de su membresía
        Authentication authCrear = SecurityContextHolder.getContext().getAuthentication();
        Long efectivaSucursalId = dto.getSucursalId();
        if (!hasAuthority(authCrear, "ROLE_ADMIN") &&
                (hasAuthority(authCrear, "ROLE_VETERINARIO") || hasAuthority(authCrear, "ROLE_GROOMER"))) {
            if (creadoPorUT.getSucursalId() != null) {
                if (efectivaSucursalId != null && !creadoPorUT.getSucursalId().equals(efectivaSucursalId)) {
                    throw new AccessDeniedException("Solo puedes crear citas para tu propia sucursal");
                }
                efectivaSucursalId = creadoPorUT.getSucursalId();
            }
        }
        if (efectivaSucursalId != null) {
            validarSucursal(efectivaSucursalId, tenantId);
        }

        log.info("Creando cita para tenant: {} por usuarioTenant: {}", tenantId, creadoPorId);

        Cita cita = Cita.builder()
                .tenantId(tenantId)
                .sucursalId(efectivaSucursalId)
                .clienteId(dto.getClienteId())
                .mascotaId(dto.getMascotaId())
                .usuarioTenantId(dto.getUsuarioTenantId())
                .creadoPorId(creadoPorId)
                .fechaInicio(dto.getFechaInicio())
                .fechaFin(dto.getFechaFin())
                .tipo(dto.getTipo())
                .motivo(dto.getMotivo())
                .estado(EstadoCita.PENDIENTE)
                .observaciones(dto.getObservaciones())
                .createdAt(LocalDateTime.now())
                .updatedAt(LocalDateTime.now())
                .build();

        return toDTO(citaRepository.save(cita));
    }

    // ── Obtener por ID ────────────────────────────────────────────────────────

    @Override
    public CitaDTO obtenerPorId(Long id) {
        String tenantId = TenantContext.getCurrentTenant();
        tenantService.requireRubro(tenantId, "VETERINARIA");
        Cita cita = citaRepository.findByIdAndTenantId(id, tenantId)
                .orElseThrow(() -> new ResourceNotFoundException("Cita no encontrada (id=" + id + ")"));

        // D5: scope por rol — VETERINARIO y GROOMER solo ven sus propias citas
        Authentication auth = SecurityContextHolder.getContext().getAuthentication();
        if (!hasAuthority(auth, "ROLE_ADMIN")) {
            boolean scopeVet    = hasAuthority(auth, "ROLE_VETERINARIO");
            boolean scopeGroomer = hasAuthority(auth, "ROLE_GROOMER");
            if (scopeVet || scopeGroomer) {
                UsuarioTenant yo = resolverMembresiaActual(tenantId);
                if (!yo.getId().equals(cita.getUsuarioTenantId())) {
                    throw new ResourceNotFoundException("Cita no encontrada (id=" + id + ")");
                }
                if (scopeGroomer && cita.getTipo() != TipoCita.PELUQUERIA) {
                    throw new ResourceNotFoundException("Cita no encontrada (id=" + id + ")");
                }
                if (yo.getSucursalId() != null && !yo.getSucursalId().equals(cita.getSucursalId())) {
                    throw new ResourceNotFoundException("Cita no encontrada (id=" + id + ")");
                }
            }
        }
        return toDTO(cita);
    }

    // ── Listar paginado ───────────────────────────────────────────────────────

    @Override
    public CitaPageResponseDTO listar(int page, int size, EstadoCita estado,
                                      LocalDateTime fechaDesde, LocalDateTime fechaHasta,
                                      Long clienteId, Long mascotaId,
                                      Long usuarioTenantId, Long sucursalId, String q) {
        String tenantId = TenantContext.getCurrentTenant();
        tenantService.requireRubro(tenantId, "VETERINARIA");

        String busqueda = (q == null || q.isBlank()) ? null : q.trim();
        PageRequest pageable = PageRequest.of(page, size, Sort.by("fechaInicio").ascending());

        // D5: scope por rol — forzar filtro según identidad del usuario autenticado
        Authentication auth       = SecurityContextHolder.getContext().getAuthentication();
        boolean isAdmin           = hasAuthority(auth, "ROLE_ADMIN");
        boolean isVeterinario     = hasAuthority(auth, "ROLE_VETERINARIO");
        boolean isGroomer         = hasAuthority(auth, "ROLE_GROOMER");

        Long efectivoUsuarioTenantId = usuarioTenantId;
        Long efectivoSucursalId      = sucursalId;
        Specification<Cita> tipoSpec = null;

        if (!isAdmin && (isVeterinario || isGroomer)) {
            UsuarioTenant yo = resolverMembresiaActual(tenantId);
            efectivoUsuarioTenantId = yo.getId();
            if (isGroomer) {
                tipoSpec = (root, query, cb) -> cb.equal(root.get("tipo"), TipoCita.PELUQUERIA);
            }
            if (yo.getSucursalId() != null) {
                efectivoSucursalId = yo.getSucursalId();
            }
        }

        Specification<Cita> spec = CitaSpecification.build(
                tenantId, estado, fechaDesde, fechaHasta,
                clienteId, mascotaId, efectivoUsuarioTenantId, efectivoSucursalId, busqueda);
        if (tipoSpec != null) {
            spec = spec.and(tipoSpec);
        }
        Page<Cita> pageResult = citaRepository.findAll(spec, pageable);

        List<CitaResumenDTO> content = pageResult.getContent().stream()
                .map(this::toResumenDTO)
                .toList();

        return CitaPageResponseDTO.builder()
                .content(content)
                .page(page)
                .size(size)
                .totalElements(pageResult.getTotalElements())
                .totalPages(pageResult.getTotalPages())
                .build();
    }

    // ── Actualizar / Reprogramar ──────────────────────────────────────────────

    @Override
    @Transactional
    public CitaDTO actualizar(Long id, ActualizarCitaRequestDTO dto) {
        String tenantId = TenantContext.getCurrentTenant();
        tenantService.requireRubro(tenantId, "VETERINARIA");
        UsuarioTenant usuarioActual = resolverMembresiaActual(tenantId); // D2: fail-closed

        Cita cita = citaRepository.findByIdAndTenantId(id, tenantId)
                .orElseThrow(() -> new ResourceNotFoundException("Cita no encontrada (id=" + id + ")"));

        // No se puede editar una cita ya finalizada (ATENDIDA)
        if (cita.getEstado() == EstadoCita.ATENDIDA) {
            throw new BadRequestException("No se puede editar una cita con estado ATENDIDA");
        }

        // Scope: VETERINARIO y GROOMER solo pueden editar sus propias citas en su sucursal
        Authentication authAct = SecurityContextHolder.getContext().getAuthentication();
        if (!hasAuthority(authAct, "ROLE_ADMIN") &&
                (hasAuthority(authAct, "ROLE_VETERINARIO") || hasAuthority(authAct, "ROLE_GROOMER"))) {
            if (!usuarioActual.getId().equals(cita.getUsuarioTenantId())) {
                throw new AccessDeniedException("Solo puedes editar citas que tengas asignadas");
            }
            if (usuarioActual.getSucursalId() != null &&
                    !usuarioActual.getSucursalId().equals(cita.getSucursalId())) {
                throw new AccessDeniedException("No puedes modificar citas de otra sucursal");
            }
        }

        // Validar fechas — en actualización se permiten fechas pasadas si la cita ya empezó
        boolean esNueva = cita.getEstado() == EstadoCita.PENDIENTE || cita.getEstado() == EstadoCita.CONFIRMADA;
        validarFechas(dto.getFechaInicio(), dto.getFechaFin(), esNueva);

        Long profesionalId = dto.getUsuarioTenantId();
        if (profesionalId != null) {
            validarProfesionalParaTipo(profesionalId, tenantId, dto.getTipo());
            validarSolapamiento(tenantId, profesionalId, dto.getFechaInicio(), dto.getFechaFin(), id);
        }

        // Sucursal efectiva: para VETERINARIO/GROOMER se fuerza la de su membresía; si no se
        // envía una nueva, se mantiene la sucursal ya guardada en la cita.
        Long efectivaSucursalId = (dto.getSucursalId() != null) ? dto.getSucursalId() : cita.getSucursalId();
        if (!hasAuthority(authAct, "ROLE_ADMIN") &&
                (hasAuthority(authAct, "ROLE_VETERINARIO") || hasAuthority(authAct, "ROLE_GROOMER"))) {
            if (usuarioActual.getSucursalId() != null) {
                efectivaSucursalId = usuarioActual.getSucursalId();
            }
        }
        if (efectivaSucursalId != null) {
            validarSucursal(efectivaSucursalId, tenantId);
        }

        cita.setUsuarioTenantId(profesionalId);
        cita.setSucursalId(efectivaSucursalId);
        cita.setFechaInicio(dto.getFechaInicio());
        cita.setFechaFin(dto.getFechaFin());
        cita.setTipo(dto.getTipo());
        cita.setMotivo(dto.getMotivo());
        cita.setObservaciones(dto.getObservaciones());
        cita.setUpdatedAt(LocalDateTime.now());

        return toDTO(citaRepository.save(cita));
    }

    // ── Cambiar estado ────────────────────────────────────────────────────────

    @Override
    @Transactional
    public CitaDTO cambiarEstado(Long id, CambiarEstadoCitaRequestDTO dto) {
        String tenantId = TenantContext.getCurrentTenant();
        tenantService.requireRubro(tenantId, "VETERINARIA");

        // D2: fail-closed — usuario debe tener membresía activa para cambiar estado
        UsuarioTenant usuarioActual = resolverMembresiaActual(tenantId);

        Cita cita = citaRepository.findByIdAndTenantId(id, tenantId)
                .orElseThrow(() -> new ResourceNotFoundException("Cita no encontrada (id=" + id + ")"));

        EstadoCita estadoActual = cita.getEstado();
        EstadoCita estadoNuevo  = dto.getNuevoEstado();

        // Validar transición primero (independiente de permisos)
        Set<EstadoCita> permitidos = TRANSICIONES.getOrDefault(estadoActual, Set.of());
        if (!permitidos.contains(estadoNuevo)) {
            throw new BadRequestException(
                    "Transición no permitida: " + estadoActual + " → " + estadoNuevo +
                    ". Transiciones válidas desde " + estadoActual + ": " + permitidos);
        }

        Authentication auth   = SecurityContextHolder.getContext().getAuthentication();
        boolean isAdmin       = hasAuthority(auth, "ROLE_ADMIN");
        boolean isVeterinario = hasAuthority(auth, "ROLE_VETERINARIO");

        // D3: cancelar requiere permiso específico adicional a CAMBIAR_ESTADO_CITA
        if (estadoNuevo == EstadoCita.CANCELADA) {
            if (!isAdmin && !hasAuthority(auth, "PERM_CANCELAR_CITA")) {
                throw new AccessDeniedException(
                        "No tienes permiso para cancelar citas (requiere CANCELAR_CITA)");
            }
        }

        // D3: reabrir (CANCELADA/NO_ASISTIO → PENDIENTE) requiere el mismo permiso
        if (estadoNuevo == EstadoCita.PENDIENTE) {
            if (!isAdmin && !hasAuthority(auth, "PERM_CANCELAR_CITA")) {
                throw new AccessDeniedException(
                        "No tienes permiso para reabrir citas (requiere CANCELAR_CITA)");
            }
        }

        // D1: VETERINARIO solo puede iniciar (EN_ATENCION) o finalizar (ATENDIDA) sus propias citas
        if (!isAdmin && isVeterinario) {
            if (estadoNuevo != EstadoCita.EN_ATENCION && estadoNuevo != EstadoCita.ATENDIDA) {
                throw new AccessDeniedException(
                        "VETERINARIO solo puede iniciar (EN_ATENCION) o finalizar (ATENDIDA) citas asignadas");
            }
            if (!usuarioActual.getId().equals(cita.getUsuarioTenantId())) {
                throw new AccessDeniedException(
                        "VETERINARIO solo puede gestionar citas que tenga asignadas");
            }
        }

        // D3: EN_ATENCION — solo el profesional asignado o ADMIN
        if (estadoNuevo == EstadoCita.EN_ATENCION && !isAdmin) {
            if (!usuarioActual.getId().equals(cita.getUsuarioTenantId())) {
                throw new AccessDeniedException(
                        "Solo el profesional asignado puede iniciar la atención (EN_ATENCION)");
            }
        }

        // D3: ATENDIDA — solo el profesional asignado o ADMIN
        if (estadoNuevo == EstadoCita.ATENDIDA && !isAdmin) {
            if (!usuarioActual.getId().equals(cita.getUsuarioTenantId())) {
                throw new AccessDeniedException(
                        "Solo el profesional asignado puede marcar la cita como atendida (ATENDIDA)");
            }
        }

        cita.setEstado(estadoNuevo);
        if (dto.getObservaciones() != null && !dto.getObservaciones().isBlank()) {
            cita.setObservaciones(dto.getObservaciones());
        }
        cita.setUpdatedAt(LocalDateTime.now());

        log.info("Cita {} → estado {} → {}", id, estadoActual, estadoNuevo);
        return toDTO(citaRepository.save(cita));
    }

    // ── Profesionales elegibles ───────────────────────────────────────────────

    @Override
    public List<ProfesionalElegibleDTO> listarProfesionalesElegibles(Long sucursalId, TipoCita tipo) {
        String tenantId = TenantContext.getCurrentTenant();
        tenantService.requireRubro(tenantId, "VETERINARIA");

        String rolNombre = (tipo == TipoCita.PELUQUERIA) ? "GROOMER" : "VETERINARIO";

        return usuarioTenantRepository
                .findProfesionalesElegibles(tenantId, rolNombre, sucursalId)
                .stream()
                .map(ut -> ProfesionalElegibleDTO.builder()
                        .usuarioTenantId(ut.getId())
                        .nombre(ut.getUsuario() != null ? ut.getUsuario().getNombre() : "(sin nombre)")
                        .rolNombre(ut.getRol().getNombre())
                        .sucursalId(ut.getSucursalId())
                        .build())
                .toList();
    }

    // ── Validaciones privadas ─────────────────────────────────────────────────

    private void validarFechas(LocalDateTime inicio, LocalDateTime fin, boolean validarFutura) {
        if (!fin.isAfter(inicio)) {
            throw new BadRequestException("La fechaFin debe ser posterior a la fechaInicio");
        }
        long horas = Duration.between(inicio, fin).toHours();
        if (horas > MAX_DURACION_HORAS) {
            throw new BadRequestException(
                    "La duración máxima de una cita es " + MAX_DURACION_HORAS + " horas");
        }
        if (validarFutura && inicio.isBefore(LocalDateTime.now())) {
            throw new BadRequestException("No se pueden crear citas en fechas pasadas");
        }
    }

    private void validarCliente(Long clienteId, String tenantId) {
        clienteRepository.findByIdAndTenantId(clienteId, tenantId)
                .orElseThrow(() -> new ResourceNotFoundException(
                        "Cliente no encontrado en este tenant (id=" + clienteId + ")"));
    }

    private void validarMascotaDelTenant(Long mascotaId, String tenantId) {
        mascotaRepository.findByIdAndTenantId(mascotaId, tenantId)
                .orElseThrow(() -> new ResourceNotFoundException(
                        "Mascota no encontrada en este tenant (id=" + mascotaId + ")"));
    }

    // D4: verifica que el profesional exista en el tenant, tenga membresía activa
    //     y que su rol sea compatible con el tipo de cita.
    private void validarProfesional(Long usuarioTenantId, String tenantId) {
        validarProfesionalParaTipo(usuarioTenantId, tenantId, null);
    }

    private void validarProfesionalParaTipo(Long usuarioTenantId, String tenantId, TipoCita tipo) {
        // Necesitamos cargar el rol → usar findById con JOIN FETCH cuando sea posible;
        // para el path sin tipo llamamos a la versión simple.
        UsuarioTenant prof = usuarioTenantRepository.findById(usuarioTenantId)
                .orElseThrow(() -> new ResourceNotFoundException(
                        "Profesional no encontrado (id=" + usuarioTenantId + ")"));
        if (!tenantId.equals(prof.getTenantId())) {
            throw new ResourceNotFoundException(
                    "Profesional no encontrado en este tenant (id=" + usuarioTenantId + ")");
        }
        if (!Boolean.TRUE.equals(prof.getActivo())) {
            throw new BadRequestException(
                    "El profesional asignado no tiene una membresía activa en este tenant");
        }
        if (tipo != null && prof.getRol() != null) {
            String rolNombre = prof.getRol().getNombre();
            if (tipo == TipoCita.PELUQUERIA && !"GROOMER".equals(rolNombre)) {
                throw new BadRequestException(
                        "Una cita de tipo PELUQUERIA requiere un profesional con rol GROOMER");
            }
            if (tipo != TipoCita.PELUQUERIA && "GROOMER".equals(rolNombre)) {
                throw new BadRequestException(
                        "Un GROOMER solo puede ser asignado a citas de tipo PELUQUERIA");
            }
        }
    }

    private void validarSolapamiento(String tenantId, Long profesionalId,
                                     LocalDateTime inicio, LocalDateTime fin, Long excludeId) {
        List<Cita> solapadas = citaRepository.findSolapamiento(
                tenantId, profesionalId, inicio, fin, excludeId);
        if (!solapadas.isEmpty()) {
            throw new BadRequestException(
                    "El profesional ya tiene una cita activa en ese rango de horario");
        }
    }

    private void validarSucursal(Long sucursalId, String tenantId) {
        Sucursal s = sucursalRepository.findByIdAndTenantId(sucursalId, tenantId)
                .orElseThrow(() -> new ResourceNotFoundException(
                        "Sucursal no encontrada en este tenant (id=" + sucursalId + ")"));
        if (!Boolean.TRUE.equals(s.getActivo())) {
            throw new BadRequestException(
                    "La sucursal seleccionada no está activa (id=" + sucursalId + ")");
        }
        if (Boolean.TRUE.equals(s.getBloqueadaPorPlan())) {
            throw new BadRequestException(
                    "La sucursal está bloqueada por el plan actual (id=" + sucursalId + ")");
        }
    }

    // ── Helpers de seguridad ─────────────────────────────────────────────────

    /** Fail-closed: el usuario autenticado debe tener membresía activa en el tenant actual. */
    private UsuarioTenant resolverMembresiaActual(String tenantId) {
        Long usuarioId = TenantContext.getCurrentUserId();
        return usuarioTenantRepository
                .findByUsuarioIdAndTenantIdAndActivoTrue(usuarioId, tenantId)
                .orElseThrow(() -> new AccessDeniedException(
                        "El usuario autenticado no tiene membresía activa en el tenant actual"));
    }

    private boolean hasAuthority(Authentication auth, String authority) {
        return auth != null && auth.getAuthorities().stream()
                .anyMatch(a -> authority.equals(a.getAuthority()));
    }

    // ── Helpers de mapeo ─────────────────────────────────────────────────────

    private CitaDTO toDTO(Cita c) {
        // Obtenemos nombres de cliente y mascota para la respuesta completa
        String clienteNombre = clienteRepository.findById(c.getClienteId())
                .map(Cliente::getNombre).orElse(null);
        Mascota mascota = mascotaRepository.findById(c.getMascotaId()).orElse(null);

        String profesionalNombre = null;
        if (c.getUsuarioTenantId() != null) {
            profesionalNombre = usuarioTenantRepository.findById(c.getUsuarioTenantId())
                    .map(ut -> ut.getUsuario() != null ? ut.getUsuario().getNombre() : null)
                    .orElse(null);
        }

        return CitaDTO.builder()
                .id(c.getId())
                .tenantId(c.getTenantId())
                .sucursalId(c.getSucursalId())
                .clienteId(c.getClienteId())
                .clienteNombre(clienteNombre)
                .mascotaId(c.getMascotaId())
                .mascotaNombre(mascota != null ? mascota.getNombre() : null)
                .mascotaEspecie(mascota != null ? mascota.getEspecie() : null)
                .usuarioTenantId(c.getUsuarioTenantId())
                .profesionalNombre(profesionalNombre)
                .creadoPorId(c.getCreadoPorId())
                .fechaInicio(c.getFechaInicio())
                .fechaFin(c.getFechaFin())
                .tipo(c.getTipo())
                .motivo(c.getMotivo())
                .estado(c.getEstado())
                .observaciones(c.getObservaciones())
                .createdAt(c.getCreatedAt())
                .updatedAt(c.getUpdatedAt())
                .build();
    }

    private CitaResumenDTO toResumenDTO(Cita c) {
        String clienteNombre = clienteRepository.findById(c.getClienteId())
                .map(Cliente::getNombre).orElse(null);
        Mascota mascota = mascotaRepository.findById(c.getMascotaId()).orElse(null);

        String profesionalNombre = null;
        if (c.getUsuarioTenantId() != null) {
            profesionalNombre = usuarioTenantRepository.findById(c.getUsuarioTenantId())
                    .map(ut -> ut.getUsuario() != null ? ut.getUsuario().getNombre() : null)
                    .orElse(null);
        }

        return CitaResumenDTO.builder()
                .id(c.getId())
                .sucursalId(c.getSucursalId())
                .clienteId(c.getClienteId())
                .clienteNombre(clienteNombre)
                .mascotaId(c.getMascotaId())
                .mascotaNombre(mascota != null ? mascota.getNombre() : null)
                .mascotaEspecie(mascota != null ? mascota.getEspecie() : null)
                .usuarioTenantId(c.getUsuarioTenantId())
                .profesionalNombre(profesionalNombre)
                .fechaInicio(c.getFechaInicio())
                .fechaFin(c.getFechaFin())
                .tipo(c.getTipo())
                .estado(c.getEstado())
                .motivo(c.getMotivo())
                .build();
    }

}
