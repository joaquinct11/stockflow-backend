package com.stockflow.service.impl;

import com.stockflow.dto.OtConsumoClasificacionDTO;
import com.stockflow.dto.OtConsumoDevolucionDTO;
import com.stockflow.dto.OtConsumoMaterialDTO;
import com.stockflow.dto.RegistrarConsumoOtRequestDTO;
import com.stockflow.entity.MovimientoInventario;
import com.stockflow.entity.OrdenTrabajo;
import com.stockflow.entity.OtConsumoClasificacion;
import com.stockflow.entity.OtConsumoDevolucion;
import com.stockflow.entity.OtConsumoDevolucionLote;
import com.stockflow.entity.OtConsumoLote;
import com.stockflow.entity.OtConsumoMaterial;
import com.stockflow.entity.Producto;
import com.stockflow.entity.Usuario;
import com.stockflow.entity.ProductoPresentacion;
import com.stockflow.entity.ProductoStockSucursal;
import com.stockflow.entity.StockLote;
import com.stockflow.exception.BadRequestException;
import com.stockflow.exception.ResourceNotFoundException;
import com.stockflow.repository.MovimientoInventarioRepository;
import com.stockflow.repository.OrdenTrabajoRepository;
import com.stockflow.repository.OtConsumoClasificacionRepository;
import com.stockflow.repository.OtConsumoDevolucionLoteRepository;
import com.stockflow.repository.OtConsumoDevolucionRepository;
import com.stockflow.repository.OtConsumoLoteRepository;
import com.stockflow.repository.OtConsumoMaterialRepository;
import com.stockflow.repository.ProductoPresentacionRepository;
import com.stockflow.repository.ProductoRepository;
import com.stockflow.repository.ProductoStockSucursalRepository;
import com.stockflow.repository.StockLoteRepository;
import com.stockflow.repository.UsuarioRepository;
import com.stockflow.service.OtConsumoService;
import com.stockflow.service.TenantService;
import com.stockflow.util.TenantContext;
import lombok.RequiredArgsConstructor;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
public class OtConsumoServiceImpl implements OtConsumoService {

    private static final String RUBRO = "EMPRESA_SERVICIOS";

    // Estados en los que se permite registrar consumos
    private static final Set<String> ESTADOS_CON_CONSUMO = Set.of(
            "ABIERTA", "EN_PROCESO", "DIAGNOSTICADA", "PENDIENTE_REPUESTO");

    private final OrdenTrabajoRepository otRepository;
    private final ProductoRepository productoRepository;
    private final ProductoPresentacionRepository presentacionRepository;
    private final ProductoStockSucursalRepository stockSucursalRepository;
    private final StockLoteRepository stockLoteRepository;
    private final OtConsumoMaterialRepository consumoRepository;
    private final OtConsumoLoteRepository consumoLoteRepository;
    private final OtConsumoDevolucionRepository devolucionRepository;
    private final OtConsumoDevolucionLoteRepository devolucionLoteRepository;
    private final OtConsumoClasificacionRepository clasificacionRepository;
    private final MovimientoInventarioRepository movimientoRepository;
    private final UsuarioRepository usuarioRepository;
    private final TenantService tenantService;

    @Override
    @Transactional
    public OtConsumoMaterialDTO registrarConsumo(Long otId, RegistrarConsumoOtRequestDTO req) {
        String tenantId = TenantContext.getCurrentTenant();
        Long userId = TenantContext.getCurrentUserId();
        tenantService.requireRubro(tenantId, RUBRO);

        // ── Paso 1: Lock OT ──────────────────────────────────────────────────────
        OrdenTrabajo ot = otRepository.findByIdAndTenantIdForUpdate(otId, tenantId)
                .orElseThrow(() -> new ResourceNotFoundException("Orden de trabajo no encontrada"));
        assertTecnicoAccess(ot, userId);

        if (!ESTADOS_CON_CONSUMO.contains(ot.getEstado())) {
            throw new BadRequestException(
                    "No se pueden registrar consumos en una OT en estado: " + ot.getEstado());
        }

        // Validación ya garantizada por @Min en el DTO, pero se verifica en servicio también
        if (req.getCantidadPresentacion() == null || req.getCantidadPresentacion() <= 0) {
            throw new BadRequestException("La cantidad debe ser mayor que cero");
        }

        // ── Paso 2: Lock Producto ────────────────────────────────────────────────
        Producto producto = productoRepository.findByIdForUpdate(req.getProductoId())
                .orElseThrow(() -> new ResourceNotFoundException("Producto no encontrado"));

        if (!tenantId.equals(producto.getTenantId())) {
            throw new ResourceNotFoundException("Producto no encontrado");
        }
        if (Boolean.FALSE.equals(producto.getActivo())) {
            throw new BadRequestException("El producto no está activo");
        }

        // ── Paso 3: Resolución de presentación y cálculo de cantidad base ────────
        int factor = 1;
        Long presentacionId = null;
        if (req.getPresentacionId() != null) {
            ProductoPresentacion pres = presentacionRepository.findById(req.getPresentacionId())
                    .orElseThrow(() -> new ResourceNotFoundException("Presentación no encontrada"));
            if (!pres.getProducto().getId().equals(producto.getId())) {
                throw new BadRequestException("La presentación no pertenece al producto indicado");
            }
            factor = (pres.getFactor() != null && pres.getFactor() > 0) ? pres.getFactor() : 1;
            presentacionId = pres.getId();
        }
        int cantidadBase = req.getCantidadPresentacion() * factor;

        // ── SERVICIO: solo registro, sin descuento de stock ──────────────────────
        if ("SERVICIO".equals(producto.getTipo())) {
            OtConsumoMaterial consumo = consumoRepository.save(buildConsumo(
                    tenantId, ot, producto.getId(), presentacionId, factor,
                    req.getCantidadPresentacion(), cantidadBase,
                    req.getPrecioUnitario(), req.getNotas(), userId));
            return toDTO(consumo, List.of());
        }

        // ── Paso 4: Contexto de sucursal ─────────────────────────────────────────
        Long sucursalId = (ot.getSucursal() != null) ? ot.getSucursal().getId() : null;

        // ── Paso 5: Lock stock de sucursal (si aplica) ───────────────────────────
        ProductoStockSucursal stockSucursal = null;
        if (sucursalId != null) {
            stockSucursal = stockSucursalRepository
                    .findByProductoIdAndSucursalIdForUpdate(producto.getId(), sucursalId)
                    .orElse(null);
        }

        // ── Paso 6: Validación de suficiencia de stock ───────────────────────────
        if (producto.getStockActual() < cantidadBase) {
            throw new BadRequestException("Stock global insuficiente: disponible="
                    + producto.getStockActual() + ", requerido=" + cantidadBase);
        }
        if (stockSucursal != null && stockSucursal.getStockActual() < cantidadBase) {
            throw new BadRequestException("Stock de sucursal insuficiente: disponible="
                    + stockSucursal.getStockActual() + ", requerido=" + cantidadBase);
        }

        // ── Paso 7: Descuento FEFO con lock por lote (orden ascendente de ID) ────
        boolean conLotes = stockLoteRepository.existsByProductoIdAndTenantId(producto.getId(), tenantId);
        if (conLotes && sucursalId != null && stockSucursal != null) {
            Integer sumTodosLotesRaw = stockLoteRepository.sumStockTotalLotesBySucursal(
                    producto.getId(), tenantId, sucursalId);
            int sumTodosLotes = (sumTodosLotesRaw != null) ? sumTodosLotesRaw : 0;
            if (sumTodosLotes != stockSucursal.getStockActual()) {
                throw new BadRequestException(
                        "Inconsistencia en inventario de sucursal " + sucursalId
                        + ": stockSucursal=" + stockSucursal.getStockActual()
                        + ", sumLotesTodos=" + sumTodosLotes
                        + ". Corrija el inventario antes de registrar el consumo.");
            }
        }
        if (conLotes) {
            LocalDate hoyPrecheck = LocalDate.now();
            Integer vigenteRaw = (sucursalId != null)
                    ? stockLoteRepository.sumStockVigenteConSucursal(producto.getId(), tenantId, sucursalId, hoyPrecheck)
                    : stockLoteRepository.sumStockVigente(producto.getId(), tenantId, hoyPrecheck);
            int vigente = (vigenteRaw != null) ? vigenteRaw : 0;
            if (vigente < cantidadBase) {
                throw new BadRequestException(
                        "Stock en lotes vigentes insuficiente: disponible=" + vigente
                        + ", requerido=" + cantidadBase
                        + ". Los lotes existentes pueden estar vencidos o sin stock disponible.");
            }
        }
        List<OtConsumoLote> lotesConsumo = conLotes
                ? descontarFefoConLock(tenantId, producto.getId(), sucursalId, cantidadBase)
                : List.of();

        // ── Paso 8: Actualizar stock global y de sucursal ────────────────────────
        producto.setStockActual(producto.getStockActual() - cantidadBase);
        productoRepository.save(producto);

        if (stockSucursal != null) {
            stockSucursal.setStockActual(stockSucursal.getStockActual() - cantidadBase);
            stockSucursalRepository.save(stockSucursal);
        }

        // ── Paso 9: Movimiento de inventario para trazabilidad ───────────────────
        Usuario usuario = usuarioRepository.findById(userId)
                .orElseThrow(() -> new ResourceNotFoundException("Usuario no encontrado: " + userId));
        movimientoRepository.save(MovimientoInventario.builder()
                .producto(producto)
                .usuario(usuario)
                .cantidad(cantidadBase)
                .tipo("CONSUMO_OT")
                .tenantId(tenantId)
                .referencia(ot.getNumeroOt())
                .descripcion("Consumo en OT " + ot.getNumeroOt())
                .sucursalId(sucursalId)
                .costoUnitario(producto.getCostoUnitario())
                .build());

        // ── Paso 10: Guardar consumo y lotes ─────────────────────────────────────
        OtConsumoMaterial consumo = consumoRepository.save(buildConsumo(
                tenantId, ot, producto.getId(), presentacionId, factor,
                req.getCantidadPresentacion(), cantidadBase,
                req.getPrecioUnitario(), req.getNotas(), userId));

        if (!lotesConsumo.isEmpty()) {
            final Long consumoId = consumo.getId();
            lotesConsumo.forEach(cl -> cl.setConsumoId(consumoId));
            consumoLoteRepository.saveAll(lotesConsumo);
        }

        return toDTO(consumo, lotesConsumo);
    }

    // ── Helpers ───────────────────────────────────────────────────────────────

    /**
     * Descuenta {@code necesario} unidades usando FEFO.
     * Los lotes se bloquean individualmente en orden ascendente de ID para
     * garantizar un orden de lock determinista y evitar deadlocks entre
     * transacciones concurrentes.
     */
    private List<OtConsumoLote> descontarFefoConLock(String tenantId, Long productoId,
                                                      Long sucursalId, int necesario) {
        LocalDate hoy = LocalDate.now();

        // Obtener IDs de lotes vigentes en orden FEFO (lectura sin lock)
        List<StockLote> lotesFefo = (sucursalId != null)
                ? stockLoteRepository.findVigentesFefoConSucursal(productoId, tenantId, sucursalId, hoy)
                : stockLoteRepository.findVigentesFefo(productoId, tenantId, hoy);

        int totalVigente = lotesFefo.stream().mapToInt(StockLote::getStockActual).sum();
        if (totalVigente < necesario) {
            throw new BadRequestException("Stock en lotes vigentes insuficiente: disponible="
                    + totalVigente + ", requerido=" + necesario);
        }

        // Lockear lotes en orden ascendente de ID (lock ordering)
        List<Long> idsOrdenados = lotesFefo.stream()
                .map(StockLote::getId)
                .sorted()
                .collect(Collectors.toList());

        List<StockLote> lotesLocked = idsOrdenados.stream()
                .map(id -> stockLoteRepository.findByIdForUpdate(id)
                        .orElseThrow(() -> new IllegalStateException("Lote no encontrado: " + id)))
                .collect(Collectors.toList());

        // Re-ordenar FEFO después del lock (la BD puede devolverlos en cualquier orden)
        lotesLocked.sort(Comparator
                .comparing(StockLote::getFechaVencimiento,
                        Comparator.nullsLast(Comparator.naturalOrder())));

        // Re-validar suficiencia post-lock (otro TX pudo haber consumido entre lectura y lock)
        int totalPostLock = lotesLocked.stream()
                .filter(l -> l.getFechaVencimiento() == null || !l.getFechaVencimiento().isBefore(hoy))
                .mapToInt(StockLote::getStockActual)
                .sum();
        if (totalPostLock < necesario) {
            throw new BadRequestException("Stock en lotes vigentes insuficiente (post-lock): disponible="
                    + totalPostLock + ", requerido=" + necesario);
        }

        // Descontar FEFO y construir registros de trazabilidad
        List<OtConsumoLote> result = new ArrayList<>();
        int restante = necesario;

        for (StockLote lote : lotesLocked) {
            if (restante <= 0) break;
            if (lote.getFechaVencimiento() != null && lote.getFechaVencimiento().isBefore(hoy)) continue;
            if (lote.getStockActual() <= 0) continue;

            int descontar = Math.min(lote.getStockActual(), restante);
            lote.setStockActual(lote.getStockActual() - descontar);
            stockLoteRepository.save(lote);
            restante -= descontar;

            result.add(OtConsumoLote.builder()
                    .tenantId(tenantId)
                    .productoId(productoId)
                    .stockLoteId(lote.getId())
                    .cantidad(descontar)
                    .createdAt(LocalDateTime.now())
                    .build());
        }

        if (restante > 0) {
            throw new BadRequestException(
                    "No se pudo completar el descuento FEFO: faltan " + restante + " unidades");
        }

        return result;
    }

    private OtConsumoMaterial buildConsumo(String tenantId, OrdenTrabajo ot, Long productoId,
            Long presentacionId, int factor, int cantPres, int cantBase,
            BigDecimal precioUnitario, String notas, Long userId) {
        return OtConsumoMaterial.builder()
                .tenantId(tenantId)
                .ordenTrabajo(ot)
                .productoId(productoId)
                .presentacionId(presentacionId)
                .factorPresentacion(factor)
                .cantidadPresentacion(cantPres)
                .cantidadBase(cantBase)
                .precioUnitario(precioUnitario)
                .notas(notas)
                .registradoPor(userId)
                .createdAt(LocalDateTime.now())
                .build();
    }

    @Override
    @Transactional(readOnly = true)
    public List<OtConsumoMaterialDTO> getConsumos(Long otId) {
        String tenantId = TenantContext.getCurrentTenant();
        tenantService.requireRubro(tenantId, RUBRO);

        otRepository.findByIdAndTenantId(otId, tenantId)
                .orElseThrow(() -> new ResourceNotFoundException("Orden de trabajo no encontrada"));

        List<OtConsumoMaterial> consumos = consumoRepository
                .findByOrdenTrabajo_IdAndTenantIdOrderByCreatedAtAsc(otId, tenantId);

        if (consumos.isEmpty()) {
            return List.of();
        }

        // ── Batch loading: 4 queries independientes del tamaño de la lista ────

        Set<Long> productoIds = consumos.stream()
                .map(OtConsumoMaterial::getProductoId).collect(Collectors.toSet());
        Set<Long> presentacionIds = consumos.stream()
                .map(OtConsumoMaterial::getPresentacionId)
                .filter(id -> id != null).collect(Collectors.toSet());
        Set<Long> usuarioIds = consumos.stream()
                .map(OtConsumoMaterial::getRegistradoPor)
                .filter(id -> id != null).collect(Collectors.toSet());
        Set<Long> consumoIds = consumos.stream()
                .map(OtConsumoMaterial::getId).collect(Collectors.toSet());

        List<Producto> productos = productoRepository.findAllById(productoIds);
        Map<Long, String> productoNombres = productos.stream()
                .collect(Collectors.toMap(Producto::getId, Producto::getNombre));
        Map<Long, String> productoTipos = productos.stream()
                .collect(Collectors.toMap(Producto::getId, p -> p.getTipo() != null ? p.getTipo() : "PRODUCTO"));

        // JOIN FETCH de unidadMedida para evitar lazy load dentro del mapeo
        Map<Long, ProductoPresentacion> presentacionesById =
                presentacionRepository.findByIdInWithUnidadMedida(presentacionIds).stream()
                        .collect(Collectors.toMap(ProductoPresentacion::getId, p -> p));

        Map<Long, List<OtConsumoLote>> lotesPorConsumo =
                consumoLoteRepository.findByConsumoIdInOrderByCreatedAtAsc(consumoIds).stream()
                        .collect(Collectors.groupingBy(OtConsumoLote::getConsumoId));

        // ── Fase 2B: devoluciones y clasificaciones por consumo ───────────────
        List<OtConsumoDevolucion> todasDevoluciones =
                devolucionRepository.findByConsumoIdInOrderByCreatedAtAsc(consumoIds);
        Map<Long, List<OtConsumoDevolucion>> devolucionesPorConsumo =
                todasDevoluciones.stream()
                        .collect(Collectors.groupingBy(OtConsumoDevolucion::getConsumoId));
        Map<Long, Integer> cantidadDevueltaPorConsumo = devolucionRepository
                .sumCantidadDevueltaGroupByConsumoId(consumoIds).stream()
                .collect(Collectors.toMap(
                        row -> ((Number) row[0]).longValue(),
                        row -> ((Number) row[1]).intValue()));

        Set<Long> devIds = todasDevoluciones.stream()
                .map(OtConsumoDevolucion::getId).collect(Collectors.toSet());
        Map<Long, List<OtConsumoDevolucionLote>> lotesPorDevolucion =
                devIds.isEmpty() ? Map.of() :
                devolucionLoteRepository.findByDevolucionIdInOrderByCreatedAtAsc(devIds).stream()
                        .collect(Collectors.groupingBy(OtConsumoDevolucionLote::getDevolucionId));

        List<OtConsumoClasificacion> todasClasificaciones =
                clasificacionRepository.findByConsumoIdInOrderByCreatedAtAsc(consumoIds);
        Map<Long, List<OtConsumoClasificacion>> clasificacionesPorConsumo =
                todasClasificaciones.stream()
                        .collect(Collectors.groupingBy(OtConsumoClasificacion::getConsumoId));
        Map<Long, Integer> cantidadClasificadaPorConsumo = clasificacionRepository
                .sumCantidadClasificadaGroupByConsumoId(consumoIds).stream()
                .collect(Collectors.toMap(
                        row -> ((Number) row[0]).longValue(),
                        row -> ((Number) row[1]).intValue()));

        // Acumular todos los user IDs antes de la carga batch de nombres
        todasDevoluciones.stream()
                .map(OtConsumoDevolucion::getRegistradoPor)
                .filter(id -> id != null).forEach(usuarioIds::add);
        todasClasificaciones.stream()
                .map(OtConsumoClasificacion::getRegistradoPor)
                .filter(id -> id != null).forEach(usuarioIds::add);

        Map<Long, String> usuarioNombres = usuarioRepository.findAllById(usuarioIds).stream()
                .collect(Collectors.toMap(
                        Usuario::getId,
                        u -> u.getNombre() != null ? u.getNombre() : u.getEmail()));

        // ── Mapeo en memoria ──────────────────────────────────────────────────

        return consumos.stream().map(c -> {
            String productoNombre = productoNombres.getOrDefault(
                    c.getProductoId(), "Producto " + c.getProductoId());

            String presentacionNombre = null;
            if (c.getPresentacionId() != null) {
                ProductoPresentacion pres = presentacionesById.get(c.getPresentacionId());
                if (pres != null) {
                    String um = (pres.getUnidadMedida() != null)
                            ? pres.getUnidadMedida().getNombre() : "unid";
                    presentacionNombre = um + " ×" + pres.getFactor();
                }
            }

            String registradoPorNombre = c.getRegistradoPor() != null
                    ? usuarioNombres.get(c.getRegistradoPor()) : null;

            List<OtConsumoMaterialDTO.LoteDTO> loteDTOs =
                    lotesPorConsumo.getOrDefault(c.getId(), List.of()).stream()
                            .map(l -> OtConsumoMaterialDTO.LoteDTO.builder()
                                    .stockLoteId(l.getStockLoteId())
                                    .cantidad(l.getCantidad())
                                    .build())
                            .collect(Collectors.toList());

            // Fase 2B
            String productoTipo = productoTipos.getOrDefault(c.getProductoId(), "PRODUCTO");
            Integer cantDevuelta = cantidadDevueltaPorConsumo.getOrDefault(c.getId(), 0);
            Integer cantClasificada = cantidadClasificadaPorConsumo.getOrDefault(c.getId(), 0);

            List<OtConsumoDevolucionDTO> devDTOs =
                    devolucionesPorConsumo.getOrDefault(c.getId(), List.of()).stream()
                    .map((OtConsumoDevolucion d) -> {
                        List<OtConsumoDevolucionDTO.LoteDTO> dlotes =
                                lotesPorDevolucion.getOrDefault(d.getId(), List.of()).stream()
                                        .map(dl -> OtConsumoDevolucionDTO.LoteDTO.builder()
                                                .stockLoteId(dl.getStockLoteId())
                                                .cantidad(dl.getCantidad())
                                                .build())
                                        .collect(Collectors.toList());
                        return OtConsumoDevolucionDTO.builder()
                                .id(d.getId())
                                .consumoId(d.getConsumoId())
                                .ordenTrabajoId(d.getOrdenTrabajoId())
                                .cantidadBase(d.getCantidadBase())
                                .motivo(d.getMotivo())
                                .registradoPor(d.getRegistradoPor())
                                .registradoPorNombre(d.getRegistradoPor() != null
                                        ? usuarioNombres.get(d.getRegistradoPor()) : null)
                                .createdAt(d.getCreatedAt())
                                .lotes(dlotes)
                                .build();
                    }).collect(Collectors.toList());

            List<OtConsumoClasificacionDTO> clDTOs =
                    clasificacionesPorConsumo.getOrDefault(c.getId(), List.of()).stream()
                    .map((OtConsumoClasificacion cl) ->
                        OtConsumoClasificacionDTO.builder()
                                .id(cl.getId())
                                .consumoId(cl.getConsumoId())
                                .ordenTrabajoId(cl.getOrdenTrabajoId())
                                .clasificacion(cl.getClasificacion())
                                .cantidad(cl.getCantidad())
                                .notas(cl.getNotas())
                                .registradoPor(cl.getRegistradoPor())
                                .registradoPorNombre(cl.getRegistradoPor() != null
                                        ? usuarioNombres.get(cl.getRegistradoPor()) : null)
                                .createdAt(cl.getCreatedAt())
                                .build()
                    ).collect(Collectors.toList());

            return OtConsumoMaterialDTO.builder()
                    .id(c.getId())
                    .ordenTrabajoId(c.getOrdenTrabajo().getId())
                    .productoId(c.getProductoId())
                    .presentacionId(c.getPresentacionId())
                    .factorPresentacion(c.getFactorPresentacion())
                    .cantidadPresentacion(c.getCantidadPresentacion())
                    .cantidadBase(c.getCantidadBase())
                    .precioUnitario(c.getPrecioUnitario())
                    .notas(c.getNotas())
                    .registradoPor(c.getRegistradoPor())
                    .createdAt(c.getCreatedAt())
                    .lotes(loteDTOs)
                    .productoNombre(productoNombre)
                    .presentacionNombre(presentacionNombre)
                    .registradoPorNombre(registradoPorNombre)
                    .productoTipo(productoTipo)
                    .cantidadDevuelta(cantDevuelta)
                    .cantidadClasificada(cantClasificada)
                    .devoluciones(devDTOs)
                    .clasificaciones(clDTOs)
                    .build();
        }).collect(Collectors.toList());
    }

    private OtConsumoMaterialDTO toDTO(OtConsumoMaterial c, List<OtConsumoLote> lotes) {
        List<OtConsumoMaterialDTO.LoteDTO> loteDTOs = lotes.stream()
                .map(l -> OtConsumoMaterialDTO.LoteDTO.builder()
                        .stockLoteId(l.getStockLoteId())
                        .cantidad(l.getCantidad())
                        .build())
                .collect(Collectors.toList());

        return OtConsumoMaterialDTO.builder()
                .id(c.getId())
                .ordenTrabajoId(c.getOrdenTrabajo().getId())
                .productoId(c.getProductoId())
                .presentacionId(c.getPresentacionId())
                .factorPresentacion(c.getFactorPresentacion())
                .cantidadPresentacion(c.getCantidadPresentacion())
                .cantidadBase(c.getCantidadBase())
                .precioUnitario(c.getPrecioUnitario())
                .notas(c.getNotas())
                .registradoPor(c.getRegistradoPor())
                .createdAt(c.getCreatedAt())
                .lotes(loteDTOs)
                .build();
    }

    private boolean callerHasAuthority(String authority) {
        var auth = SecurityContextHolder.getContext().getAuthentication();
        if (auth == null) return false;
        return auth.getAuthorities().stream()
                .anyMatch(a -> a.getAuthority().equals(authority));
    }

    private void assertTecnicoAccess(OrdenTrabajo ot, Long userId) {
        if (callerHasAuthority("PERM_VER_TODAS_OT")) return;
        if (ot.getTecnico() == null || !ot.getTecnico().getId().equals(userId)) {
            throw new ResourceNotFoundException("Orden de trabajo no encontrada");
        }
    }
}
