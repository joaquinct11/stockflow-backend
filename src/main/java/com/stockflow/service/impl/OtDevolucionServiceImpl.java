package com.stockflow.service.impl;

import com.stockflow.dto.OtConsumoDevolucionDTO;
import com.stockflow.dto.RegistrarDevolucionOtRequestDTO;
import com.stockflow.entity.MovimientoInventario;
import com.stockflow.entity.OtConsumoClasificacion;
import com.stockflow.entity.OtConsumoDevolucion;
import com.stockflow.entity.OtConsumoDevolucionLote;
import com.stockflow.entity.OtConsumoLote;
import com.stockflow.entity.OtConsumoMaterial;
import com.stockflow.entity.OrdenTrabajo;
import com.stockflow.entity.Producto;
import com.stockflow.entity.ProductoStockSucursal;
import com.stockflow.entity.StockLote;
import com.stockflow.entity.Usuario;
import com.stockflow.exception.BadRequestException;
import com.stockflow.exception.ResourceNotFoundException;
import com.stockflow.repository.MovimientoInventarioRepository;
import com.stockflow.repository.OrdenTrabajoRepository;
import com.stockflow.repository.OtConsumoClasificacionRepository;
import com.stockflow.repository.OtConsumoDevolucionLoteRepository;
import com.stockflow.repository.OtConsumoDevolucionRepository;
import com.stockflow.repository.OtConsumoLoteRepository;
import com.stockflow.repository.OtConsumoMaterialRepository;
import com.stockflow.repository.ProductoRepository;
import com.stockflow.repository.ProductoStockSucursalRepository;
import com.stockflow.repository.StockLoteRepository;
import com.stockflow.repository.UsuarioRepository;
import com.stockflow.service.OtDevolucionService;
import com.stockflow.service.TenantService;
import org.springframework.security.core.context.SecurityContextHolder;
import com.stockflow.util.TenantContext;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
public class OtDevolucionServiceImpl implements OtDevolucionService {

    private static final String RUBRO = "EMPRESA_SERVICIOS";
    private static final Set<String> ESTADOS_CON_DEVOLUCION = Set.of(
            "ABIERTA", "EN_PROCESO", "DIAGNOSTICADA", "PENDIENTE_REPUESTO");

    private final OrdenTrabajoRepository otRepository;
    private final OtConsumoMaterialRepository consumoRepository;
    private final OtConsumoLoteRepository consumoLoteRepository;
    private final OtConsumoDevolucionRepository devolucionRepository;
    private final OtConsumoDevolucionLoteRepository devolucionLoteRepository;
    private final OtConsumoClasificacionRepository clasificacionRepository;
    private final ProductoRepository productoRepository;
    private final ProductoStockSucursalRepository stockSucursalRepository;
    private final StockLoteRepository stockLoteRepository;
    private final MovimientoInventarioRepository movimientoRepository;
    private final UsuarioRepository usuarioRepository;
    private final TenantService tenantService;

    @Override
    @Transactional
    public OtConsumoDevolucionDTO registrarDevolucion(Long otId, Long consumoId, RegistrarDevolucionOtRequestDTO req) {
        String tenantId = TenantContext.getCurrentTenant();
        Long userId = TenantContext.getCurrentUserId();
        tenantService.requireRubro(tenantId, RUBRO);

        // ── Paso 1: Lock OT ─────────────────────────────────────────────────────
        OrdenTrabajo ot = otRepository.findByIdAndTenantIdForUpdate(otId, tenantId)
                .orElseThrow(() -> new ResourceNotFoundException("Orden de trabajo no encontrada"));
        assertTecnicoAccess(ot, userId);

        if (!ESTADOS_CON_DEVOLUCION.contains(ot.getEstado())) {
            throw new BadRequestException(
                    "No se pueden registrar devoluciones en una OT en estado: " + ot.getEstado());
        }

        // ── Paso 2: Lock Consumo ─────────────────────────────────────────────────
        OtConsumoMaterial consumo = consumoRepository.findByIdAndTenantIdForUpdate(consumoId, tenantId)
                .orElseThrow(() -> new ResourceNotFoundException("Consumo no encontrado"));

        if (!consumo.getOrdenTrabajo().getId().equals(otId)) {
            throw new ResourceNotFoundException("Consumo no encontrado");
        }

        // Solo se pueden devolver materiales físicos (no servicios)
        // ── Paso 3: Lock Producto ────────────────────────────────────────────────
        Producto producto = productoRepository.findByIdForUpdate(consumo.getProductoId())
                .orElseThrow(() -> new ResourceNotFoundException("Producto no encontrado"));

        if (!tenantId.equals(producto.getTenantId())) {
            throw new ResourceNotFoundException("Producto no encontrado");
        }
        if ("SERVICIO".equals(producto.getTipo())) {
            throw new BadRequestException("No se pueden devolver servicios, solo materiales físicos");
        }

        // ── Paso 4: Verificar cantidad a devolver ────────────────────────────────
        // El disponible descuenta tanto las devoluciones como las clasificaciones previas,
        // ya que clasificar un material (INSTALADO, DAÑADO, etc.) lo deja fuera del circuito
        // de stock y no debe poder devolverse después.
        int cantidadDevueltaPrevia = devolucionRepository
                .sumCantidadDevueltaByConsumoId(consumoId, tenantId);
        int cantidadClasificadaPrevia = clasificacionRepository
                .sumCantidadClasificadaByConsumoId(consumoId, tenantId);
        int disponibleParaDevolver = consumo.getCantidadBase()
                - cantidadDevueltaPrevia
                - cantidadClasificadaPrevia;

        if (disponibleParaDevolver <= 0) {
            throw new BadRequestException(
                    "Todo el material de este consumo ya fue devuelto o clasificado");
        }
        if (req.getCantidadBase() > disponibleParaDevolver) {
            throw new BadRequestException(
                    "Cantidad a devolver (" + req.getCantidadBase()
                    + ") supera la disponible (" + disponibleParaDevolver + ")");
        }

        // ── Paso 5: Restaurar lotes LIFO con lock en orden ascendente de ID ──────
        Long sucursalId = (ot.getSucursal() != null) ? ot.getSucursal().getId() : null;
        List<OtConsumoLote> lotesOriginales = consumoLoteRepository
                .findByConsumoIdOrderByCreatedAtAsc(consumoId);

        List<OtConsumoDevolucionLote> devolucionLotes = new ArrayList<>();
        int restanteADevolver = req.getCantidadBase();

        if (!lotesOriginales.isEmpty()) {
            // LIFO: invertir el orden de lotes para restaurar los últimos primero
            List<Long> idsLotesLiboOrden = lotesOriginales.stream()
                    .map(OtConsumoLote::getStockLoteId)
                    .collect(Collectors.toList());
            // Invertir para LIFO
            List<Long> idsInvertidos = new ArrayList<>(idsLotesLiboOrden);
            java.util.Collections.reverse(idsInvertidos);
            // Pero lockear en orden ascendente de ID para evitar deadlocks
            List<Long> idsAscendente = idsInvertidos.stream().sorted().collect(Collectors.toList());

            List<StockLote> lotesLocked = idsAscendente.stream()
                    .map(id -> stockLoteRepository.findByIdForUpdate(id)
                            .orElseThrow(() -> new IllegalStateException("Lote no encontrado: " + id)))
                    .collect(Collectors.toList());

            // Re-ordenar LIFO después del lock
            Map<Long, StockLote> lotePorId = lotesLocked.stream()
                    .collect(Collectors.toMap(StockLote::getId, l -> l));

            // Mapear cantidad consumida por lote para saber cuánto se puede restaurar
            Map<Long, Integer> cantidadConsumidaPorLote = lotesOriginales.stream()
                    .collect(Collectors.toMap(OtConsumoLote::getStockLoteId, OtConsumoLote::getCantidad));

            for (Long loteId : idsInvertidos) {
                if (restanteADevolver <= 0) break;
                StockLote lote = lotePorId.get(loteId);
                if (lote == null) continue;

                int consumidoEnEsteLote = cantidadConsumidaPorLote.getOrDefault(loteId, 0);
                int aRestaurar = Math.min(consumidoEnEsteLote, restanteADevolver);
                if (aRestaurar <= 0) continue;

                lote.setStockActual(lote.getStockActual() + aRestaurar);
                stockLoteRepository.save(lote);
                restanteADevolver -= aRestaurar;

                devolucionLotes.add(OtConsumoDevolucionLote.builder()
                        .stockLoteId(loteId)
                        .cantidad(aRestaurar)
                        .build());
            }
        }

        // ── Paso 6: Restaurar stock global y de sucursal ─────────────────────────
        int cantidadEfectivaDevuelta = req.getCantidadBase() - restanteADevolver;
        // Si no había lotes, toda la cantidad se toma directamente del stock
        if (lotesOriginales.isEmpty()) cantidadEfectivaDevuelta = req.getCantidadBase();

        producto.setStockActual(producto.getStockActual() + cantidadEfectivaDevuelta);
        productoRepository.save(producto);

        if (sucursalId != null) {
            ProductoStockSucursal stockSucursal = stockSucursalRepository
                    .findByProductoIdAndSucursalIdForUpdate(producto.getId(), sucursalId)
                    .orElse(null);
            if (stockSucursal != null) {
                stockSucursal.setStockActual(stockSucursal.getStockActual() + cantidadEfectivaDevuelta);
                stockSucursalRepository.save(stockSucursal);
            }
        }

        // ── Paso 7: Movimiento de inventario para Kardex ─────────────────────────
        Usuario usuario = usuarioRepository.findById(userId)
                .orElseThrow(() -> new ResourceNotFoundException("Usuario no encontrado: " + userId));
        movimientoRepository.save(MovimientoInventario.builder()
                .producto(producto)
                .usuario(usuario)
                .cantidad(cantidadEfectivaDevuelta)
                .tipo("DEVOLUCION_OT")
                .tenantId(tenantId)
                .referencia(ot.getNumeroOt())
                .descripcion("Devolución de OT " + ot.getNumeroOt())
                .sucursalId(sucursalId)
                .costoUnitario(producto.getCostoUnitario())
                .build());

        // ── Paso 8: Guardar devolución y lotes ───────────────────────────────────
        OtConsumoDevolucion devolucion = devolucionRepository.save(
                OtConsumoDevolucion.builder()
                        .tenantId(tenantId)
                        .consumoId(consumoId)
                        .ordenTrabajoId(otId)
                        .cantidadBase(cantidadEfectivaDevuelta)
                        .motivo(req.getMotivo())
                        .registradoPor(userId)
                        .build());

        if (!devolucionLotes.isEmpty()) {
            final Long devId = devolucion.getId();
            devolucionLotes.forEach(dl -> dl.setDevolucionId(devId));
            devolucionLoteRepository.saveAll(devolucionLotes);
        }

        String registradoPorNombre = usuarioNombre(usuario);
        return toDTO(devolucion, devolucionLotes, registradoPorNombre);
    }

    @Override
    @Transactional(readOnly = true)
    public List<OtConsumoDevolucionDTO> getDevoluciones(Long otId, Long consumoId) {
        String tenantId = TenantContext.getCurrentTenant();
        tenantService.requireRubro(tenantId, RUBRO);

        otRepository.findByIdAndTenantId(otId, tenantId)
                .orElseThrow(() -> new ResourceNotFoundException("Orden de trabajo no encontrada"));

        OtConsumoMaterial consumo = consumoRepository.findById(consumoId)
                .filter(c -> tenantId.equals(c.getTenantId()))
                .orElseThrow(() -> new ResourceNotFoundException("Consumo no encontrado"));
        if (!consumo.getOrdenTrabajo().getId().equals(otId)) {
            throw new ResourceNotFoundException("Consumo no encontrado");
        }

        List<OtConsumoDevolucion> devoluciones = devolucionRepository
                .findByConsumoIdOrderByCreatedAtAsc(consumoId);

        if (devoluciones.isEmpty()) return List.of();

        Set<Long> devIds = devoluciones.stream().map(OtConsumoDevolucion::getId).collect(Collectors.toSet());
        Map<Long, List<OtConsumoDevolucionLote>> lotesPorDev = devolucionLoteRepository
                .findByDevolucionIdInOrderByCreatedAtAsc(devIds).stream()
                .collect(Collectors.groupingBy(OtConsumoDevolucionLote::getDevolucionId));

        Set<Long> userIds = devoluciones.stream()
                .map(OtConsumoDevolucion::getRegistradoPor)
                .filter(id -> id != null)
                .collect(Collectors.toSet());
        Map<Long, String> usuarioNombres = usuarioRepository.findAllById(userIds).stream()
                .collect(Collectors.toMap(Usuario::getId, this::usuarioNombre));

        return devoluciones.stream().map(d -> toDTO(
                d,
                lotesPorDev.getOrDefault(d.getId(), List.of()),
                d.getRegistradoPor() != null ? usuarioNombres.get(d.getRegistradoPor()) : null
        )).collect(Collectors.toList());
    }

    private OtConsumoDevolucionDTO toDTO(OtConsumoDevolucion d,
                                          List<OtConsumoDevolucionLote> lotes,
                                          String registradoPorNombre) {
        List<OtConsumoDevolucionDTO.LoteDTO> loteDTOs = lotes.stream()
                .map(l -> OtConsumoDevolucionDTO.LoteDTO.builder()
                        .stockLoteId(l.getStockLoteId())
                        .cantidad(l.getCantidad())
                        .build())
                .collect(Collectors.toList());
        return OtConsumoDevolucionDTO.builder()
                .id(d.getId())
                .consumoId(d.getConsumoId())
                .ordenTrabajoId(d.getOrdenTrabajoId())
                .cantidadBase(d.getCantidadBase())
                .motivo(d.getMotivo())
                .registradoPor(d.getRegistradoPor())
                .registradoPorNombre(registradoPorNombre)
                .createdAt(d.getCreatedAt())
                .lotes(loteDTOs)
                .build();
    }

    private String usuarioNombre(Usuario u) {
        return u.getNombre() != null ? u.getNombre() : u.getEmail();
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
