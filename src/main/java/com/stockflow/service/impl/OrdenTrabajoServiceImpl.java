package com.stockflow.service.impl;

import com.stockflow.dto.ActualizarEstadoOtRequestDTO;
import com.stockflow.dto.ActualizarOtRequestDTO;
import com.stockflow.dto.AsignarTecnicoOtRequestDTO;
import com.stockflow.dto.CierreTecnicoOtRequestDTO;
import com.stockflow.dto.CrearOtRequestDTO;
import com.stockflow.dto.EntregarOtRequestDTO;
import com.stockflow.dto.FacturarOtRequestDTO;
import com.stockflow.dto.OrdenTrabajoDTO;
import com.stockflow.dto.OtPageResponseDTO;
import com.stockflow.dto.OtResumenDTO;
import com.stockflow.entity.Caja;
import com.stockflow.entity.Cliente;
import com.stockflow.entity.DetalleVenta;
import com.stockflow.entity.EquipoCliente;
import com.stockflow.entity.OrdenTrabajo;
import com.stockflow.entity.OtConsumoMaterial;
import com.stockflow.entity.OtHistorialEstado;
import com.stockflow.entity.Producto;
import com.stockflow.entity.Sucursal;
import com.stockflow.entity.Usuario;
import com.stockflow.entity.Venta;
import com.stockflow.exception.BadRequestException;
import com.stockflow.exception.ResourceNotFoundException;
import com.stockflow.mapper.OrdenTrabajoMapper;
import com.stockflow.repository.CajaRepository;
import com.stockflow.repository.ClienteRepository;
import com.stockflow.repository.EquipoClienteRepository;
import com.stockflow.repository.OrdenTrabajoRepository;
import com.stockflow.repository.OtConsumoClasificacionRepository;
import com.stockflow.repository.OtConsumoDevolucionRepository;
import com.stockflow.repository.OtConsumoMaterialRepository;
import com.stockflow.repository.OtHistorialEstadoRepository;
import com.stockflow.repository.ProductoRepository;
import com.stockflow.repository.SucursalRepository;
import com.stockflow.repository.UsuarioRepository;
import com.stockflow.repository.UsuarioTenantRepository;
import com.stockflow.service.OrdenTrabajoService;
import com.stockflow.service.OtNumeracionService;
import com.stockflow.service.TenantService;
import com.stockflow.service.VentaService;
import com.stockflow.util.TenantContext;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import org.springframework.security.core.context.SecurityContextHolder;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
public class OrdenTrabajoServiceImpl implements OrdenTrabajoService {

    private static final String RUBRO = "EMPRESA_SERVICIOS";

    private static final Set<String> ESTADOS_VALIDOS = Set.of(
            "ABIERTA", "EN_PROCESO", "DIAGNOSTICADA",
            "PENDIENTE_REPUESTO", "CERRADA_TECN",
            "FACTURADA", "ENTREGADA", "CANCELADA"
    );

    private static final Set<String> ESTADOS_CIERRE_TECNICO = Set.of(
            "EN_PROCESO", "DIAGNOSTICADA", "PENDIENTE_REPUESTO"
    );

    /**
     * Transiciones permitidas vía actualizarEstado().
     * FACTURADA y ENTREGADA solo se alcanzan por sus flujos específicos (no por este mapa).
     * CANCELADA es alcanzable desde cualquier estado no terminal.
     */
    private static final Map<String, Set<String>> TRANSICIONES_VALIDAS = Map.of(
            "ABIERTA",             Set.of("EN_PROCESO", "DIAGNOSTICADA", "CANCELADA"),
            "EN_PROCESO",          Set.of("ABIERTA", "DIAGNOSTICADA", "PENDIENTE_REPUESTO", "CERRADA_TECN", "CANCELADA"),
            "DIAGNOSTICADA",       Set.of("EN_PROCESO", "PENDIENTE_REPUESTO", "CERRADA_TECN", "CANCELADA"),
            "PENDIENTE_REPUESTO",  Set.of("EN_PROCESO", "DIAGNOSTICADA", "CANCELADA"),
            "CERRADA_TECN",        Set.of("EN_PROCESO", "CANCELADA"),
            "FACTURADA",           Set.of(),   // solo anularFacturacionOt() puede salir de FACTURADA
            "ENTREGADA",           Set.of(),   // estado terminal
            "CANCELADA",           Set.of()    // estado terminal
    );

    private static final Set<String> PRIORIDADES_VALIDAS = Set.of(
            "BAJA", "NORMAL", "ALTA", "URGENTE"
    );

    private final OrdenTrabajoRepository otRepository;
    private final OtHistorialEstadoRepository historialRepository;
    private final EquipoClienteRepository equipoRepository;
    private final ClienteRepository clienteRepository;
    private final SucursalRepository sucursalRepository;
    private final UsuarioRepository usuarioRepository;
    private final OtNumeracionService otNumeracionService;
    private final UsuarioTenantRepository usuarioTenantRepository;
    private final OtConsumoMaterialRepository consumoRepository;
    private final OtConsumoDevolucionRepository devolucionRepository;
    private final OtConsumoClasificacionRepository clasificacionRepository;
    private final ProductoRepository productoRepository;
    private final OrdenTrabajoMapper mapper;
    private final TenantService tenantService;
    private final VentaService ventaService;
    private final CajaRepository cajaRepository;

    @Override
    public OtPageResponseDTO listar(int page, int size, String estado, Long equipoClienteId, Long clienteId, String search) {
        String tenantId = TenantContext.getCurrentTenant();
        tenantService.requireRubro(tenantId, RUBRO);

        String s = (search == null) ? "" : search.trim().toLowerCase();
        String estadoParam = (estado == null || estado.isBlank()) ? null : estado.toUpperCase();

        PageRequest pageable = PageRequest.of(page, size);

        boolean verTodas = callerHasAuthority("PERM_VER_TODAS_OT");

        if (verTodas) {
            Page<OtResumenDTO> pageResult = otRepository.findPaginado(
                    tenantId, estadoParam, equipoClienteId, clienteId, s, pageable);
            return OtPageResponseDTO.builder()
                    .content(pageResult.getContent())
                    .page(page)
                    .size(size)
                    .totalElements(pageResult.getTotalElements())
                    .totalPages(pageResult.getTotalPages())
                    .resumen(OtPageResponseDTO.ResumenDTO.builder()
                            .total(otRepository.countTotal(tenantId))
                            .abiertas(otRepository.countByEstado(tenantId, "ABIERTA"))
                            .enProceso(otRepository.countByEstado(tenantId, "EN_PROCESO"))
                            .cerradas(otRepository.countByEstado(tenantId, "CERRADA_TECN"))
                            .build())
                    .build();
        }

        // TECNICO_SERVICIO: solo ve OTs asignadas a él
        Long tecnicoId = TenantContext.getCurrentUserId();
        Page<OtResumenDTO> pageResult = otRepository.findPaginadoByTecnico(
                tenantId, tecnicoId, estadoParam, equipoClienteId, clienteId, s, pageable);
        return OtPageResponseDTO.builder()
                .content(pageResult.getContent())
                .page(page)
                .size(size)
                .totalElements(pageResult.getTotalElements())
                .totalPages(pageResult.getTotalPages())
                .resumen(OtPageResponseDTO.ResumenDTO.builder()
                        .total(otRepository.countTotalByTecnico(tenantId, tecnicoId))
                        .abiertas(otRepository.countByEstadoAndTecnico(tenantId, tecnicoId, "ABIERTA"))
                        .enProceso(otRepository.countByEstadoAndTecnico(tenantId, tecnicoId, "EN_PROCESO"))
                        .cerradas(otRepository.countByEstadoAndTecnico(tenantId, tecnicoId, "CERRADA_TECN"))
                        .build())
                .build();
    }

    @Override
    public OrdenTrabajoDTO obtenerPorId(Long id) {
        String tenantId = TenantContext.getCurrentTenant();
        tenantService.requireRubro(tenantId, RUBRO);

        OrdenTrabajo ot = findOrThrow(id, tenantId);
        assertTecnicoAccess(ot);
        OrdenTrabajoDTO dto = mapper.toDTO(ot);

        List<OrdenTrabajoDTO.HistorialEstadoDTO> historial = historialRepository
                .findByOrdenTrabajo_IdAndTenantIdOrderByCreatedAtAsc(id, tenantId)
                .stream()
                .map(mapper::toHistorialDTO)
                .collect(Collectors.toList());
        dto.setHistorial(historial);

        return dto;
    }

    @Override
    @Transactional
    public OrdenTrabajoDTO crear(CrearOtRequestDTO dto) {
        String tenantId = TenantContext.getCurrentTenant();
        Long userId = TenantContext.getCurrentUserId();
        tenantService.requireRubro(tenantId, RUBRO);

        // Validar y cargar entidades referenciadas
        EquipoCliente equipo = equipoRepository.findByIdAndTenantId(dto.getEquipoClienteId(), tenantId)
                .orElseThrow(() -> new ResourceNotFoundException("Equipo no encontrado"));

        if (!equipo.getActivo()) {
            throw new BadRequestException("El equipo seleccionado no está activo");
        }

        Cliente cliente = clienteRepository.findByIdAndTenantId(dto.getClienteId(), tenantId)
                .orElseThrow(() -> new ResourceNotFoundException("Cliente no encontrado"));

        // Equipo debe pertenecer al cliente indicado
        if (!equipo.getCliente().getId().equals(cliente.getId())) {
            throw new BadRequestException("El equipo no pertenece al cliente indicado");
        }

        Sucursal sucursal = sucursalRepository.findByIdAndTenantId(dto.getSucursalId(), tenantId)
                .orElseThrow(() -> new ResourceNotFoundException("Sucursal no encontrada"));

        if (!sucursal.getActivo()) {
            throw new BadRequestException("La sucursal seleccionada no está activa");
        }

        Usuario tecnico = null;
        if (dto.getTecnicoId() != null) {
            tecnico = usuarioRepository.findById(dto.getTecnicoId())
                    .orElseThrow(() -> new ResourceNotFoundException("Técnico no encontrado"));
            if (!tecnico.getActivo()) {
                throw new BadRequestException("El técnico seleccionado no está activo");
            }
        }

        String prioridad = resolvePrioridad(dto.getPrioridad());

        // Generar número OT (REQUIRES_NEW — TX independiente)
        short anio = (short) LocalDateTime.now(ZoneId.of("America/Lima")).getYear();
        String numeroOt = otNumeracionService.generarNumeroOt(tenantId, anio);

        OrdenTrabajo ot = OrdenTrabajo.builder()
                .tenantId(tenantId)
                .numeroOt(numeroOt)
                .anio(anio)
                .equipo(equipo)
                .cliente(cliente)
                .sucursal(sucursal)
                .tecnico(tecnico)
                .estado("ABIERTA")
                .prioridad(prioridad)
                .descripcionProblema(dto.getDescripcionProblema().trim())
                .observaciones(dto.getObservaciones())
                .createdAt(LocalDateTime.now())
                .createdBy(userId)
                .build();

        ot = otRepository.save(ot);

        // Registrar estado inicial en historial
        registrarHistorial(ot, null, "ABIERTA", userId, null);

        return toFullDTO(ot);
    }

    @Override
    @Transactional
    public OrdenTrabajoDTO actualizar(Long id, ActualizarOtRequestDTO dto) {
        String tenantId = TenantContext.getCurrentTenant();
        Long userId = TenantContext.getCurrentUserId();
        tenantService.requireRubro(tenantId, RUBRO);

        OrdenTrabajo ot = findOrThrow(id, tenantId);

        if (dto.getPrioridad() != null) {
            ot.setPrioridad(resolvePrioridad(dto.getPrioridad()));
        }
        if (dto.getDescripcionProblema() != null && !dto.getDescripcionProblema().isBlank()) {
            ot.setDescripcionProblema(dto.getDescripcionProblema().trim());
        }
        if (dto.getDiagnostico() != null) {
            ot.setDiagnostico(dto.getDiagnostico());
        }
        if (dto.getTrabajoRealizado() != null) {
            ot.setTrabajoRealizado(dto.getTrabajoRealizado());
        }
        if (dto.getObservaciones() != null) {
            ot.setObservaciones(dto.getObservaciones());
        }

        ot.setUpdatedAt(LocalDateTime.now());
        ot.setUpdatedBy(userId);

        return toFullDTO(otRepository.save(ot));
    }

    @Override
    @Transactional
    public OrdenTrabajoDTO actualizarEstado(Long id, ActualizarEstadoOtRequestDTO dto) {
        String tenantId = TenantContext.getCurrentTenant();
        Long userId = TenantContext.getCurrentUserId();
        tenantService.requireRubro(tenantId, RUBRO);

        String nuevoEstado = dto.getEstado().trim().toUpperCase();
        if (!ESTADOS_VALIDOS.contains(nuevoEstado)) {
            throw new BadRequestException("Estado inválido: " + nuevoEstado);
        }
        // FACTURADA solo se alcanza mediante facturarOt(); ENTREGADA tendrá su propio flujo.
        if ("FACTURADA".equals(nuevoEstado)) {
            throw new BadRequestException("El estado FACTURADA solo puede establecerse mediante el proceso de facturación de OT");
        }
        if ("ENTREGADA".equals(nuevoEstado)) {
            throw new BadRequestException("El estado ENTREGADA solo puede establecerse mediante el proceso de entrega de OT");
        }

        OrdenTrabajo ot = findOrThrow(id, tenantId);
        assertTecnicoAccess(ot);

        String estadoAnterior = ot.getEstado();
        if (estadoAnterior.equals(nuevoEstado)) {
            return toFullDTO(ot);
        }

        // Validar que la transición esté permitida desde el estado actual
        Set<String> destinos = TRANSICIONES_VALIDAS.getOrDefault(estadoAnterior, Set.of());
        if (!destinos.contains(nuevoEstado)) {
            throw new BadRequestException(
                    "Transición de estado inválida: " + estadoAnterior + " → " + nuevoEstado
            );
        }

        ot.setEstado(nuevoEstado);
        ot.setUpdatedAt(LocalDateTime.now());
        ot.setUpdatedBy(userId);
        ot = otRepository.save(ot);

        registrarHistorial(ot, estadoAnterior, nuevoEstado, userId, dto.getObservacion());

        return toFullDTO(ot);
    }

    @Override
    @Transactional
    public OrdenTrabajoDTO asignarTecnico(Long id, AsignarTecnicoOtRequestDTO dto) {
        String tenantId = TenantContext.getCurrentTenant();
        Long userId = TenantContext.getCurrentUserId();
        tenantService.requireRubro(tenantId, RUBRO);

        OrdenTrabajo ot = findOrThrow(id, tenantId);
        Usuario tecnicoAnterior = ot.getTecnico();

        Usuario tecnicoNuevo = null;
        if (dto.getTecnicoId() != null) {
            tecnicoNuevo = usuarioRepository.findById(dto.getTecnicoId())
                    .orElseThrow(() -> new ResourceNotFoundException("Técnico no encontrado"));

            if (!tecnicoNuevo.getActivo()) {
                throw new BadRequestException("El técnico seleccionado no está activo");
            }

            // Verificar que el técnico pertenezca al tenant actual
            if (!usuarioTenantRepository.existsByUsuarioIdAndTenantIdAndActivoTrue(dto.getTecnicoId(), tenantId)) {
                throw new BadRequestException("El técnico no pertenece a este tenant o no está activo en él");
            }
        }

        ot.setTecnico(tecnicoNuevo);
        ot.setUpdatedAt(LocalDateTime.now());
        ot.setUpdatedBy(userId);
        ot = otRepository.save(ot);

        // Registrar en historial solo si el técnico realmente cambió
        Long idAnterior = tecnicoAnterior != null ? tecnicoAnterior.getId() : null;
        Long idNuevo = tecnicoNuevo != null ? tecnicoNuevo.getId() : null;
        if (!java.util.Objects.equals(idAnterior, idNuevo)) {
            registrarHistorial(ot, ot.getEstado(), ot.getEstado(), userId,
                    "Técnico asignado: " + (tecnicoNuevo != null ? tecnicoNuevo.getNombre() : "ninguno") +
                    (dto.getObservacion() != null ? " — " + dto.getObservacion() : ""));
        }

        return toFullDTO(ot);
    }

    @Override
    @Transactional
    public OrdenTrabajoDTO cierreTecnico(Long id, CierreTecnicoOtRequestDTO req) {
        String tenantId = TenantContext.getCurrentTenant();
        Long userId     = TenantContext.getCurrentUserId();
        tenantService.requireRubro(tenantId, RUBRO);

        // Validar campos obligatorios en el servicio (complementa @Valid del controller)
        if (req.getDiagnosticoFinal() == null || req.getDiagnosticoFinal().isBlank()) {
            throw new BadRequestException("El diagnóstico final es obligatorio");
        }
        if (req.getSolucionAplicada() == null || req.getSolucionAplicada().isBlank()) {
            throw new BadRequestException("La solución aplicada es obligatoria");
        }

        // Lock OT
        OrdenTrabajo ot = otRepository.findByIdAndTenantIdForUpdate(id, tenantId)
                .orElseThrow(() -> new ResourceNotFoundException("Orden de trabajo no encontrada"));
        assertTecnicoAccess(ot);

        // Validar estado actual
        if (!ESTADOS_CIERRE_TECNICO.contains(ot.getEstado())) {
            throw new BadRequestException(
                    "No se puede cerrar técnicamente una OT en estado: " + ot.getEstado()
                    + ". Estados permitidos: EN_PROCESO, DIAGNOSTICADA, PENDIENTE_REPUESTO");
        }

        // Validar que todos los consumos físicos estén regularizados
        List<OtConsumoMaterial> consumos =
                consumoRepository.findByOrdenTrabajo_IdAndTenantIdOrderByCreatedAtAsc(id, tenantId);

        if (!consumos.isEmpty()) {
            // Cargar tipos de producto en batch
            Set<Long> productoIds = consumos.stream()
                    .map(OtConsumoMaterial::getProductoId)
                    .collect(Collectors.toSet());
            Map<Long, String> tipoByProductoId = productoRepository.findAllById(productoIds)
                    .stream()
                    .collect(Collectors.toMap(Producto::getId, p -> p.getTipo() != null ? p.getTipo() : ""));

            // Cargar cantidades devueltas y clasificadas en batch
            List<Long> consumoIds = consumos.stream()
                    .map(OtConsumoMaterial::getId)
                    .collect(Collectors.toList());

            Map<Long, Integer> devueltaMap = devolucionRepository
                    .sumCantidadDevueltaGroupByConsumoId(consumoIds)
                    .stream()
                    .collect(Collectors.toMap(r -> (Long) r[0], r -> ((Number) r[1]).intValue()));

            Map<Long, Integer> clasificadaMap = clasificacionRepository
                    .sumCantidadClasificadaGroupByConsumoId(consumoIds)
                    .stream()
                    .collect(Collectors.toMap(r -> (Long) r[0], r -> ((Number) r[1]).intValue()));

            List<String> pendientes = new ArrayList<>();
            for (OtConsumoMaterial c : consumos) {
                // Los servicios no requieren devolución ni clasificación
                if ("SERVICIO".equals(tipoByProductoId.get(c.getProductoId()))) continue;

                int devuelta     = devueltaMap.getOrDefault(c.getId(), 0);
                int clasificada  = clasificadaMap.getOrDefault(c.getId(), 0);
                int pendiente    = c.getCantidadBase() - devuelta - clasificada;
                if (pendiente > 0) {
                    pendientes.add("Consumo ID " + c.getId()
                            + " (" + pendiente + " unid. pendiente"
                            + (pendiente > 1 ? "s" : "") + ")");
                }
            }

            if (!pendientes.isEmpty()) {
                throw new BadRequestException(
                        "No se puede cerrar técnicamente la OT: existen materiales sin regularizar — "
                        + String.join("; ", pendientes));
            }
        }

        // Registrar diagnóstico, solución y fecha de cierre
        String estadoAnterior = ot.getEstado();

        ot.setDiagnostico(req.getDiagnosticoFinal().trim());
        ot.setTrabajoRealizado(req.getSolucionAplicada().trim());
        if (req.getObservaciones() != null && !req.getObservaciones().isBlank()) {
            ot.setObservaciones(req.getObservaciones().trim());
        }
        ot.setEstado("CERRADA_TECN");
        ot.setFechaCierreTecnico(LocalDateTime.now());

        // Registrar técnico que cierra
        Usuario tecnicoCierre = usuarioRepository.findById(userId).orElse(null);
        ot.setTecnicoCierre(tecnicoCierre);

        ot.setUpdatedAt(LocalDateTime.now());
        ot.setUpdatedBy(userId);
        ot = otRepository.save(ot);

        // Historial
        String obsHistorial = "Cierre técnico. Solución: " + req.getSolucionAplicada().trim();
        registrarHistorial(ot, estadoAnterior, "CERRADA_TECN", userId, obsHistorial);

        OrdenTrabajoDTO dto = mapper.toDTO(ot);

        List<OrdenTrabajoDTO.HistorialEstadoDTO> historial = historialRepository
                .findByOrdenTrabajo_IdAndTenantIdOrderByCreatedAtAsc(id, tenantId)
                .stream()
                .map(mapper::toHistorialDTO)
                .collect(Collectors.toList());
        dto.setHistorial(historial);

        return dto;
    }

    @Override
    @Transactional
    public OrdenTrabajoDTO facturarOt(Long id, FacturarOtRequestDTO req) {
        String tenantId = TenantContext.getCurrentTenant();
        Long userId     = TenantContext.getCurrentUserId();
        tenantService.requireRubro(tenantId, RUBRO);

        // Mano de obra: null → 0
        BigDecimal montoManoObra = req.getMontoManoObra() != null
                ? req.getMontoManoObra().setScale(2, RoundingMode.HALF_UP)
                : BigDecimal.ZERO;
        if (montoManoObra.compareTo(BigDecimal.ZERO) < 0) {
            throw new BadRequestException("El monto de mano de obra no puede ser negativo");
        }

        // Lock pesimista sobre la OT
        OrdenTrabajo ot = otRepository.findByIdAndTenantIdForUpdate(id, tenantId)
                .orElseThrow(() -> new ResourceNotFoundException("Orden de trabajo no encontrada"));

        // Solo se puede facturar una OT cerrada técnicamente
        if (!"CERRADA_TECN".equals(ot.getEstado())) {
            throw new BadRequestException(
                    "Solo se puede facturar una OT en estado CERRADA_TECN. Estado actual: " + ot.getEstado());
        }

        // Idempotencia: si ya tiene venta, solo se permite refacturar si la venta previa fue anulada
        if (ot.getVentaId() != null) {
            Venta ventaPrevia = ventaService.getVentaById(ot.getVentaId(), tenantId);
            if (!"ANULADA".equals(ventaPrevia.getEstado())) {
                throw new BadRequestException("Esta OT ya fue facturada (venta #" + ot.getVentaId() + ")");
            }
        }

        // Validar caja si se proporciona
        if (req.getCajaId() != null) {
            Caja caja = cajaRepository.findByIdAndTenantId(req.getCajaId(), tenantId)
                    .orElseThrow(() -> new ResourceNotFoundException("Caja no encontrada: " + req.getCajaId()));
            if (!"ABIERTA".equals(caja.getEstado())) {
                throw new BadRequestException("La caja seleccionada no está abierta");
            }
        }

        // Validar vendedor y que pertenezca al tenant
        Usuario vendedor = usuarioRepository.findById(req.getVendedorId())
                .orElseThrow(() -> new ResourceNotFoundException("Vendedor no encontrado: " + req.getVendedorId()));
        if (!usuarioTenantRepository.existsByUsuarioIdAndTenantIdAndActivoTrue(req.getVendedorId(), tenantId)) {
            throw new BadRequestException("El vendedor no pertenece a este tenant o no está activo");
        }

        // Cargar consumos de la OT
        List<OtConsumoMaterial> consumos =
                consumoRepository.findByOrdenTrabajo_IdAndTenantIdOrderByCreatedAtAsc(id, tenantId);

        // Batch-cargar productos para evitar N+1
        Map<Long, Producto> productoMap = consumos.isEmpty()
                ? java.util.Collections.emptyMap()
                : productoRepository.findAllById(
                        consumos.stream().map(OtConsumoMaterial::getProductoId).collect(Collectors.toSet()))
                  .stream()
                  .collect(Collectors.toMap(Producto::getId, p -> p));

        // Construir DetalleVenta a partir de consumos (materiales físicos Y servicios).
        // NO se genera descuento de inventario: los materiales físicos ya fueron descontados
        // al registrar cada consumo durante la ejecución de la OT; los servicios nunca
        // generan movimiento de stock.
        List<DetalleVenta> detalles = new ArrayList<>();
        BigDecimal totalMateriales = BigDecimal.ZERO; // productos tipo != SERVICIO
        BigDecimal totalServicios  = BigDecimal.ZERO; // productos tipo == SERVICIO

        for (OtConsumoMaterial c : consumos) {
            Producto producto = productoMap.get(c.getProductoId());
            if (producto == null) continue;

            BigDecimal precioUnitario = c.getPrecioUnitario() != null
                    ? c.getPrecioUnitario().setScale(2, RoundingMode.HALF_UP)
                    : BigDecimal.ZERO;
            if (precioUnitario.compareTo(BigDecimal.ZERO) < 0) {
                throw new BadRequestException("Precio unitario inválido en consumo ID " + c.getId());
            }
            int cantidad = c.getCantidadBase();
            if (cantidad <= 0) {
                throw new BadRequestException("Cantidad inválida en consumo ID " + c.getId());
            }

            BigDecimal subtotal = precioUnitario.multiply(BigDecimal.valueOf(cantidad))
                    .setScale(2, RoundingMode.HALF_UP);

            if ("SERVICIO".equals(producto.getTipo())) {
                totalServicios = totalServicios.add(subtotal);
            } else {
                totalMateriales = totalMateriales.add(subtotal);
            }

            detalles.add(DetalleVenta.builder()
                    .producto(producto)
                    .cantidad(cantidad)
                    .precioUnitario(precioUnitario)
                    .subtotal(subtotal)
                    .factor(1)
                    .build());
        }

        BigDecimal totalVenta = totalMateriales.add(totalServicios).add(montoManoObra)
                .setScale(2, RoundingMode.HALF_UP);

        Long sucursalId = ot.getSucursal() != null ? ot.getSucursal().getId() : null;
        Long clienteId  = ot.getCliente()  != null ? ot.getCliente().getId()  : null;

        Venta venta = Venta.builder()
                .vendedor(vendedor)
                .total(totalVenta)
                .metodoPago(req.getMetodoPago())
                .estado("COMPLETADA")
                .tenantId(tenantId)
                .detalles(detalles)
                .createdAt(LocalDateTime.now())
                .cajaId(req.getCajaId())
                .clienteId(clienteId)
                .sucursalId(sucursalId)
                .ordenTrabajoId(id)
                .build();

        // crearVenta persiste la Venta y sus detalles.
        // No ejecuta descuento de inventario (esa lógica vive en VentaController.crear()).
        Venta ventaCreada = ventaService.crearVenta(venta);

        // Actualizar OT: vincular venta, registrar mano de obra y transición de estado
        ot.setVentaId(ventaCreada.getId());
        ot.setMontoManoObra(montoManoObra);
        ot.setTotalFacturado(totalVenta);
        ot.setEstado("FACTURADA");
        ot.setUpdatedAt(LocalDateTime.now());
        ot.setUpdatedBy(userId);
        ot = otRepository.save(ot);

        registrarHistorial(ot, "CERRADA_TECN", "FACTURADA", userId,
                "Facturación OT. Venta #" + ventaCreada.getId()
                + ". Mano de obra: S/ " + montoManoObra.toPlainString());

        OrdenTrabajoDTO dto = mapper.toDTO(ot);
        List<OrdenTrabajoDTO.HistorialEstadoDTO> historial = historialRepository
                .findByOrdenTrabajo_IdAndTenantIdOrderByCreatedAtAsc(id, tenantId)
                .stream()
                .map(mapper::toHistorialDTO)
                .collect(Collectors.toList());
        dto.setHistorial(historial);

        return dto;
    }

    @Override
    @Transactional
    public OrdenTrabajoDTO entregarOt(Long id, EntregarOtRequestDTO req) {
        String tenantId = TenantContext.getCurrentTenant();
        Long userId     = TenantContext.getCurrentUserId();
        tenantService.requireRubro(tenantId, RUBRO);

        // Validación del receptor
        String recibidoPor = req.getRecibidoPor() == null ? "" : req.getRecibidoPor().trim();
        if (recibidoPor.isBlank()) {
            throw new BadRequestException("El nombre de quien recibe el equipo es obligatorio");
        }

        // Lock pesimista sobre la OT
        OrdenTrabajo ot = otRepository.findByIdAndTenantIdForUpdate(id, tenantId)
                .orElseThrow(() -> new ResourceNotFoundException("Orden de trabajo no encontrada"));

        // Solo se puede entregar una OT en estado FACTURADA
        if (!"FACTURADA".equals(ot.getEstado())) {
            throw new BadRequestException(
                    "Solo se puede registrar la entrega de una OT en estado FACTURADA. Estado actual: " + ot.getEstado());
        }

        // Idempotencia: evitar doble entrega
        if (ot.getFechaEntrega() != null) {
            throw new BadRequestException("Esta OT ya fue entregada el "
                    + ot.getFechaEntrega().toLocalDate() + " a: " + ot.getRecibidoPor());
        }

        // Registrar datos de entrega
        LocalDateTime ahora = LocalDateTime.now(ZoneId.of("America/Lima"));
        ot.setRecibidoPor(recibidoPor);
        ot.setDocumentoReceptor(req.getDocumentoReceptor());
        ot.setFechaEntrega(ahora);
        ot.setEntregadoPor(userId);
        ot.setObservacionesEntrega(req.getObservaciones());
        ot.setConformidadCliente(req.getConformidadCliente());

        // Cambiar estado a ENTREGADA
        ot.setEstado("ENTREGADA");
        ot.setUpdatedAt(ahora);
        ot.setUpdatedBy(userId);
        ot = otRepository.save(ot);

        String obsHistorial = "Equipo entregado a: " + recibidoPor
                + (Boolean.TRUE.equals(req.getConformidadCliente()) ? " · Conformidad: Sí" : " · Conformidad: No");
        registrarHistorial(ot, "FACTURADA", "ENTREGADA", userId, obsHistorial);

        OrdenTrabajoDTO dto = mapper.toDTO(ot);
        List<OrdenTrabajoDTO.HistorialEstadoDTO> historial = historialRepository
                .findByOrdenTrabajo_IdAndTenantIdOrderByCreatedAtAsc(id, tenantId)
                .stream()
                .map(mapper::toHistorialDTO)
                .collect(Collectors.toList());
        dto.setHistorial(historial);

        return dto;
    }

    @Override
    @Transactional
    public OrdenTrabajoDTO anularFacturacionOt(Long id) {
        String tenantId = TenantContext.getCurrentTenant();
        Long userId     = TenantContext.getCurrentUserId();
        tenantService.requireRubro(tenantId, RUBRO);

        // Lock pesimista sobre la OT
        OrdenTrabajo ot = otRepository.findByIdAndTenantIdForUpdate(id, tenantId)
                .orElseThrow(() -> new ResourceNotFoundException("Orden de trabajo no encontrada"));

        // Solo se puede anular la facturación de una OT en estado FACTURADA
        if (!"FACTURADA".equals(ot.getEstado())) {
            throw new BadRequestException(
                    "Solo se puede anular la facturación de una OT en estado FACTURADA. Estado actual: " + ot.getEstado());
        }

        if (ot.getVentaId() == null) {
            throw new BadRequestException("Esta OT no tiene una venta asociada");
        }

        // Cargar la venta y validar consistencia
        Venta venta = ventaService.getVentaById(ot.getVentaId(), tenantId);

        // Seguridad bi-direccional: verificar que la venta pertenece a esta OT
        if (venta.getOrdenTrabajoId() != null && !id.equals(venta.getOrdenTrabajoId())) {
            throw new BadRequestException("La venta #" + venta.getId() + " no pertenece a esta OT");
        }

        // Anular la venta SIN reponer stock (los consumos ya fueron manejados por CONSUMO_OT)
        ventaService.anularVentaSinReponerStock(ot.getVentaId(), tenantId);

        // Revertir OT a estado anterior a facturación
        String estadoAnterior = ot.getEstado();
        ot.setEstado("CERRADA_TECN");
        ot.setUpdatedAt(LocalDateTime.now());
        ot.setUpdatedBy(userId);
        ot = otRepository.save(ot);

        registrarHistorial(ot, estadoAnterior, "CERRADA_TECN", userId,
                "Anulación de facturación. Venta #" + ot.getVentaId() + " anulada sin restaurar stock.");

        return toFullDTO(ot);
    }

    // ── Helpers ───────────────────────────────────────────────────────────────

    private OrdenTrabajo findOrThrow(Long id, String tenantId) {
        return otRepository.findByIdAndTenantId(id, tenantId)
                .orElseThrow(() -> new ResourceNotFoundException("Orden de trabajo no encontrada"));
    }

    private void registrarHistorial(OrdenTrabajo ot, String estadoAnterior,
                                    String estadoNuevo, Long usuarioId, String observacion) {
        historialRepository.save(OtHistorialEstado.builder()
                .tenantId(ot.getTenantId())
                .ordenTrabajo(ot)
                .estadoAnterior(estadoAnterior)
                .estadoNuevo(estadoNuevo)
                .usuarioId(usuarioId)
                .observacion(observacion)
                .createdAt(LocalDateTime.now())
                .build());
    }

    private OrdenTrabajoDTO toFullDTO(OrdenTrabajo ot) {
        OrdenTrabajoDTO dto = mapper.toDTO(ot);
        List<OrdenTrabajoDTO.HistorialEstadoDTO> historial = historialRepository
                .findByOrdenTrabajo_IdAndTenantIdOrderByCreatedAtAsc(ot.getId(), ot.getTenantId())
                .stream().map(mapper::toHistorialDTO).collect(Collectors.toList());
        dto.setHistorial(historial);
        return dto;
    }

    private String resolvePrioridad(String raw) {
        if (raw == null || raw.isBlank()) return "NORMAL";
        String p = raw.trim().toUpperCase();
        if (!PRIORIDADES_VALIDAS.contains(p)) {
            throw new BadRequestException("Prioridad inválida: " + raw);
        }
        return p;
    }

    /** Devuelve true si el token del request actual incluye la authority dada (p.ej. "PERM_VER_TODAS_OT"). */
    private boolean callerHasAuthority(String authority) {
        var auth = SecurityContextHolder.getContext().getAuthentication();
        if (auth == null) return false;
        return auth.getAuthorities().stream()
                .anyMatch(a -> a.getAuthority().equals(authority));
    }

    /**
     * Verifica que el usuario actual tenga acceso a la OT.
     * Si tiene PERM_VER_TODAS_OT, pasa sin restricción.
     * Si no, debe ser el técnico asignado; en caso contrario lanza 404 para no revelar
     * la existencia de la OT al técnico no asignado.
     */
    private void assertTecnicoAccess(OrdenTrabajo ot) {
        if (callerHasAuthority("PERM_VER_TODAS_OT")) return;
        Long userId = TenantContext.getCurrentUserId();
        if (ot.getTecnico() == null || !ot.getTecnico().getId().equals(userId)) {
            throw new ResourceNotFoundException("Orden de trabajo no encontrada");
        }
    }
}
