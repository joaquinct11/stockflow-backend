package com.stockflow.controller;

import com.stockflow.dto.SuscripcionDTO;
import com.stockflow.dto.SuscripcionEstadoResponseDTO;
import com.stockflow.entity.Suscripcion;
import com.stockflow.entity.Usuario;
import com.stockflow.exception.BadRequestException;
import com.stockflow.exception.ResourceNotFoundException;
import com.stockflow.mapper.SuscripcionMapper;
import com.stockflow.repository.SuscripcionRepository;
import com.stockflow.service.CulqiService;
import com.stockflow.service.EmailService;
import com.stockflow.service.SuscripcionService;
import com.stockflow.service.UsuarioService;
import com.stockflow.config.properties.CulqiProperties;
import com.stockflow.util.TenantContext;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;
import jakarta.validation.Valid;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

@Slf4j
@RestController
@RequestMapping("/suscripciones")
@RequiredArgsConstructor
public class SuscripcionController {

    private final SuscripcionService suscripcionService;
    private final UsuarioService usuarioService;
    private final SuscripcionMapper suscripcionMapper;
    private final SuscripcionRepository suscripcionRepository;
    private final CulqiService culqiService;
    private final EmailService emailService;
    private final CulqiProperties culqiProperties;

    /**
     * Obtiene suscripciones del tenant actual
     */
    @GetMapping
    @PreAuthorize("hasRole('ADMIN') or hasAuthority('PERM_VER_SUSCRIPCIONES')")
    public ResponseEntity<List<SuscripcionDTO>> obtenerTodas() {
        String tenantId = TenantContext.getCurrentTenant();
        log.info("Obteniendo suscripciones para tenant: {}", tenantId);

        return ResponseEntity.ok(
                suscripcionMapper.toDTOList(suscripcionService.obtenerSuscripcionesPorTenant(tenantId))
        );
    }

    @GetMapping("/{id}")
    @PreAuthorize("hasRole('ADMIN') or hasAuthority('PERM_VER_SUSCRIPCIONES')")
    public ResponseEntity<SuscripcionDTO> obtenerPorId(@PathVariable Long id) {
        return suscripcionService.obtenerSuscripcionPorId(id)
                .map(suscripcionMapper::toDTO)
                .map(ResponseEntity::ok)
                .orElse(ResponseEntity.notFound().build());
    }

    @GetMapping("/usuario/{usuarioId}")
    @PreAuthorize("hasRole('ADMIN') or hasAuthority('PERM_VER_SUSCRIPCIONES')")
    public ResponseEntity<SuscripcionDTO> obtenerPorUsuario(@PathVariable Long usuarioId) {
        log.info("Obteniendo suscripción del usuario: {}", usuarioId);
        return suscripcionService.obtenerSuscripcionPorUsuario(usuarioId)
                .map(suscripcionMapper::toDTO)
                .map(ResponseEntity::ok)
                .orElse(ResponseEntity.notFound().build());
    }

    @GetMapping("/estado/{estado}")
    @PreAuthorize("hasRole('ADMIN') or hasAuthority('PERM_VER_SUSCRIPCIONES')")
    public ResponseEntity<List<SuscripcionDTO>> obtenerPorEstado(@PathVariable String estado) {
        String tenantId = TenantContext.getCurrentTenant();
        log.info("Obteniendo suscripciones con estado: {} para tenant: {}", estado, tenantId);

        return ResponseEntity.ok(
                suscripcionMapper.toDTOList(suscripcionService.obtenerSuscripcionesPorEstadoYTenant(estado, tenantId))
        );
    }

    /**
     * Crea una suscripción manualmente (uso administrativo).
     */
    @PostMapping
    @PreAuthorize("hasRole('ADMIN') or hasAuthority('PERM_CREAR_SUSCRIPCION')")
    public ResponseEntity<SuscripcionDTO> crear(@Valid @RequestBody SuscripcionDTO suscripcionDTO) {
        String tenantId = TenantContext.getCurrentTenant();
        log.info("Creando suscripción para tenant: {}", tenantId);

        // Validar usuario principal
        Usuario usuario = usuarioService.obtenerUsuarioPorId(suscripcionDTO.getUsuarioPrincipalId())
                .orElseThrow(() -> new ResourceNotFoundException("Usuario no encontrado"));

        // Validar que no exista otra suscripción activa
        suscripcionService.obtenerSuscripcionPorUsuario(suscripcionDTO.getUsuarioPrincipalId())
                .ifPresent(suscripcion -> {
                    if ("ACTIVA".equals(suscripcion.getEstado())) {
                        throw new BadRequestException("El usuario ya tiene una suscripción activa");
                    }
                });

        // Validar plan
        if (!"BASICO".equals(suscripcionDTO.getPlanId())) {
            throw new BadRequestException("Plan no válido. Use: BASICO");
        }

        // Crear suscripción usando mapper
        Suscripcion suscripcion = suscripcionMapper.toEntity(suscripcionDTO);
        suscripcion.setUsuarioPrincipal(usuario);
        suscripcion.setEstado("ACTIVA");
        suscripcion.setTenantId(tenantId);

        Suscripcion suscripcionCreada = suscripcionService.crearSuscripcion(suscripcion);

        log.info("Suscripción creada: Plan {} para tenant {}", suscripcionCreada.getPlanId(), tenantId);

        return ResponseEntity.status(HttpStatus.CREATED)
                .body(suscripcionMapper.toDTO(suscripcionCreada));
    }

    /**
     * Cancela suscripción por ID — cancela en Culqi si tiene preapprovalId (sxn_xxx).
     */
    @PatchMapping("/{id}/cancelar")
    @PreAuthorize("hasRole('ADMIN') or hasAuthority('PERM_CAMBIAR_ESTADO_SUSCRIPCION')")
    public ResponseEntity<SuscripcionDTO> cancelar(@PathVariable Long id) {
        log.info("Cancelando suscripción ID: {}", id);
        cancelarSuscripcionInterno(id);
        return suscripcionService.obtenerSuscripcionPorId(id)
                .map(suscripcionMapper::toDTO)
                .map(ResponseEntity::ok)
                .orElseThrow(() -> new ResourceNotFoundException("Suscripción no encontrada"));
    }

    /**
     * Cancela la suscripción del usuario autenticado.
     */
    @PatchMapping("/cancelar")
    @PreAuthorize("hasRole('ADMIN') or hasAuthority('PERM_CAMBIAR_ESTADO_SUSCRIPCION')")
    public ResponseEntity<Void> cancelarMiSuscripcion() {
        String tenantId = TenantContext.getCurrentTenant();
        Long usuarioId = TenantContext.getCurrentUserId();
        log.info("Cancelando suscripción para tenant={}, usuario={}", tenantId, usuarioId);

        Suscripcion suscripcion = suscripcionRepository
                .findByTenantIdAndUsuarioPrincipalId(tenantId, usuarioId)
                .orElseThrow(() -> new ResourceNotFoundException("No existe suscripción para este usuario"));

        if ("CANCELADA".equals(suscripcion.getEstado()) || "CANCELACION_PENDIENTE".equals(suscripcion.getEstado())) {
            throw new BadRequestException("La suscripción ya está cancelada o tiene una cancelación pendiente");
        }

        cancelarEnCulqiSiAplica(suscripcion);

        // Conservar acceso hasta que finalice el período ya pagado.
        // Si no hay currentPeriodEnd (suscripción manual), cancelar inmediatamente.
        if (suscripcion.getCurrentPeriodEnd() != null &&
                suscripcion.getCurrentPeriodEnd().isAfter(LocalDateTime.now())) {
            suscripcion.setEstado("CANCELACION_PENDIENTE");
        } else {
            suscripcion.setEstado("CANCELADA");
        }
        suscripcion.setFechaCancelacion(LocalDateTime.now());
        suscripcionRepository.save(suscripcion);
        enviarEmailSuscripcionSiAplica(suscripcion, suscripcion.getEstado());
        log.info("Suscripción marcada como {} para tenant={}, usuario={}", suscripcion.getEstado(), tenantId, usuarioId);

        return ResponseEntity.noContent().build();
    }

    /**
     * Obtiene el estado de suscripción del usuario autenticado (sin llamar a Culqi).
     */
    @GetMapping("/estado")
    @PreAuthorize("isAuthenticated()")
    public ResponseEntity<SuscripcionEstadoResponseDTO> obtenerEstado() {
        String tenantId = TenantContext.getCurrentTenant();
        Long usuarioId = TenantContext.getCurrentUserId();
        log.info("Consultando estado de suscripción para tenant {}, usuario {}", tenantId, usuarioId);

        Optional<Suscripcion> optSuscripcion = suscripcionRepository
                .findByTenantIdAndUsuarioPrincipalId(tenantId, usuarioId);

        if (optSuscripcion.isEmpty()) {
            optSuscripcion = suscripcionRepository.findFirstByTenantIdOrderByIdDesc(tenantId);
        }

        if (optSuscripcion.isEmpty()) {
            log.info("No existe suscripción para tenant={}, usuario={}", tenantId, usuarioId);
            return ResponseEntity.ok(SuscripcionEstadoResponseDTO.builder()
                    .estado("SIN_SUSCRIPCION")
                    .build());
        }

        Suscripcion s = optSuscripcion.get();

        // Lazy-flip: si la cancelación pendiente ya expiró, marcar como CANCELADA
        if ("CANCELACION_PENDIENTE".equals(s.getEstado()) &&
                s.getCurrentPeriodEnd() != null &&
                s.getCurrentPeriodEnd().isBefore(LocalDateTime.now())) {
            s.setEstado("CANCELADA");
            suscripcionRepository.save(s);
            log.info("Cancelación pendiente expirada — suscripción marcada como CANCELADA para tenant={}", s.getTenantId());
        }

        BigDecimal precioDisplay = s.getPrecioMensual();
        if (s.getPlanId() != null) {
            String pid = s.getPlanId().toUpperCase();
            if (pid.contains("PRO"))    precioDisplay = culqiProperties.getPrecioPro();
            else if (pid.contains("BASICO")) precioDisplay = culqiProperties.getPrecioBasico();
        }

        return ResponseEntity.ok(SuscripcionEstadoResponseDTO.builder()
                .estado(s.getEstado())
                .planId(s.getPlanId())
                .preapprovalId(s.getPreapprovalId())
                .fechaProximoCobro(s.getFechaProximoCobro())
                .precioMensual(precioDisplay)
                .currentPeriodEnd(s.getCurrentPeriodEnd())
                .build());
    }

    @PatchMapping("/{id}/activar")
    @PreAuthorize("hasRole('ADMIN') or hasAuthority('PERM_CAMBIAR_ESTADO_SUSCRIPCION')")
    public ResponseEntity<SuscripcionDTO> activar(@PathVariable Long id) {
        log.info("Activando suscripción ID: {}", id);
        Suscripcion suscripcionActivada = suscripcionService.activarSuscripcion(id);
        return ResponseEntity.ok(suscripcionMapper.toDTO(suscripcionActivada));
    }

    @DeleteMapping("/{id}")
    @PreAuthorize("hasRole('ADMIN') or hasAuthority('PERM_ELIMINAR_SUSCRIPCION')")
    public ResponseEntity<Void> eliminar(@PathVariable Long id) {
        log.info("Eliminando suscripción ID: {}", id);
        suscripcionService.eliminarSuscripcion(id);
        return ResponseEntity.noContent().build();
    }

    // ── Helpers privados ────────────────────────────────────────────────────────

    private void cancelarSuscripcionInterno(Long suscripcionId) {
        Suscripcion suscripcion = suscripcionRepository.findById(suscripcionId)
                .orElseThrow(() -> new ResourceNotFoundException("Suscripción no encontrada"));

        if ("CANCELADA".equals(suscripcion.getEstado()) || "CANCELACION_PENDIENTE".equals(suscripcion.getEstado())) {
            throw new BadRequestException("La suscripción ya está cancelada o tiene una cancelación pendiente");
        }

        cancelarEnCulqiSiAplica(suscripcion);

        // Admin cancel: respetar también el período pagado
        if (suscripcion.getCurrentPeriodEnd() != null &&
                suscripcion.getCurrentPeriodEnd().isAfter(LocalDateTime.now())) {
            suscripcion.setEstado("CANCELACION_PENDIENTE");
        } else {
            suscripcion.setEstado("CANCELADA");
        }
        suscripcion.setFechaCancelacion(LocalDateTime.now());
        suscripcionRepository.save(suscripcion);
        enviarEmailSuscripcionSiAplica(suscripcion, suscripcion.getEstado());
        log.info("Suscripción {} marcada como {} para tenant={}", suscripcionId, suscripcion.getEstado(), suscripcion.getTenantId());
    }

    /**
     * Cancela la suscripción en Culqi si tiene preapprovalId (sxn_test_xxx / sxn_live_xxx).
     * Errores 404 se ignoran (ya cancelada en Culqi). Otros errores bloquean la cancelación local.
     */
    private void cancelarEnCulqiSiAplica(Suscripcion suscripcion) {
        if (suscripcion.getPreapprovalId() == null || suscripcion.getPreapprovalId().isBlank()) {
            return;
        }
        try {
            culqiService.cancelarSuscripcion(suscripcion.getPreapprovalId());
            log.info("Suscripción {} cancelada en Culqi", suscripcion.getPreapprovalId());
        } catch (BadRequestException e) {
            String msg = e.getMessage() != null ? e.getMessage() : "";
            if (msg.contains("404") || msg.toLowerCase().contains("no encontrad")) {
                log.warn("Suscripción {} no encontrada en Culqi (ya cancelada o inexistente) — continuando",
                        suscripcion.getPreapprovalId());
                return;
            }
            log.error("Error cancelando suscripción {} en Culqi — cancelación local BLOQUEADA",
                    suscripcion.getPreapprovalId(), e);
            throw new BadRequestException(
                    "No se pudo cancelar la suscripción en Culqi. " +
                    "Intente nuevamente o contacte a soporte. Detalle: " + msg);
        } catch (Exception e) {
            log.error("Error inesperado cancelando suscripción {} en Culqi",
                    suscripcion.getPreapprovalId(), e);
            throw new BadRequestException("Error de comunicación con Culqi al cancelar. Intente nuevamente.");
        }
    }

    private void enviarEmailSuscripcionSiAplica(Suscripcion suscripcion, String estado) {
        if (suscripcion.getUsuarioPrincipal() == null) return;
        try {
            emailService.enviarEmailSuscripcion(
                    suscripcion.getUsuarioPrincipal().getEmail(),
                    suscripcion.getUsuarioPrincipal().getNombre(),
                    estado,
                    suscripcion.getPlanId()
            );
        } catch (Exception e) {
            log.warn("No se pudo enviar email de suscripción ({}): {}", estado, e.getMessage());
        }
    }
}
