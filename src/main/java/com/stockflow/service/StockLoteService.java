package com.stockflow.service;

import com.stockflow.dto.StockLoteDisponibleDTO;
import com.stockflow.entity.Proveedor;
import com.stockflow.entity.StockLote;
import com.stockflow.repository.ProveedorRepository;
import com.stockflow.repository.StockLoteRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.temporal.ChronoUnit;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

@Slf4j
@Service
@RequiredArgsConstructor
public class StockLoteService {

    private final StockLoteRepository stockLoteRepository;
    private final ProveedorRepository proveedorRepository;

    /**
     * Registra un nuevo lote cuando se crea un movimiento de ENTRADA con fechaVencimiento.
     */
    @Transactional
    public void registrarLote(String tenantId, Long movimientoId, Long productoId,
                               Long sucursalId, String lote, LocalDate fechaVencimiento,
                               Integer cantidad, Long proveedorId, BigDecimal precioVenta,
                               BigDecimal costoUnitario) {
        // Idempotente: si ya existe (reintento) no duplicar
        if (stockLoteRepository.findByMovimientoId(movimientoId).isPresent()) return;

        StockLote stockLote = StockLote.builder()
                .tenantId(tenantId)
                .movimientoId(movimientoId)
                .productoId(productoId)
                .sucursalId(sucursalId)
                .lote(lote)
                .fechaVencimiento(fechaVencimiento)
                .stockInicial(cantidad)
                .stockActual(cantidad)
                .proveedorId(proveedorId)
                .precioVenta(precioVenta)
                .costoUnitario(costoUnitario)
                .build();
        stockLoteRepository.save(stockLote);
        log.info("✅ StockLote registrado: producto={} lote={} costo={} precio={} proveedor={}",
                productoId, lote, costoUnitario, precioVenta, proveedorId);
    }

    /**
     * Descuenta stock de un lote específico seleccionado por el usuario en el POS.
     * Retorna la cantidad que no pudo descontarse (0 = éxito completo).
     */
    @Transactional
    public int descontarLoteEspecifico(Long stockLoteId, int cantidad) {
        return stockLoteRepository.findById(stockLoteId)
                .map(lote -> {
                    int descontar = Math.min(lote.getStockActual(), cantidad);
                    lote.setStockActual(lote.getStockActual() - descontar);
                    stockLoteRepository.save(lote);
                    int restante = cantidad - descontar;
                    if (restante > 0) {
                        log.warn("⚠️ Lote {} sin stock suficiente: faltaron {} unidades", stockLoteId, restante);
                    }
                    return restante;
                })
                .orElseGet(() -> {
                    log.warn("⚠️ No se encontró stock_lote id={}", stockLoteId);
                    return cantidad;
                });
    }

    /**
     * Restaura stock a un lote específico (usado al anular una venta).
     */
    @Transactional
    public void restaurarLoteEspecifico(Long stockLoteId, int cantidad) {
        stockLoteRepository.findById(stockLoteId).ifPresentOrElse(lote -> {
            lote.setStockActual(lote.getStockActual() + cantidad);
            stockLoteRepository.save(lote);
            log.info("✅ Lote {} restaurado: +{}", stockLoteId, cantidad);
        }, () -> log.warn("⚠️ No se encontró stock_lote id={} para restaurar", stockLoteId));
    }

    /**
     * Lotes disponibles (no vencidos, con stock) para seleccionar en el POS.
     */
    public List<StockLoteDisponibleDTO> getLotesDisponibles(String tenantId, Long productoId, Long sucursalId) {
        LocalDate hoy = LocalDate.now();
        List<StockLote> lotes = sucursalId != null
                ? stockLoteRepository.findDisponiblesConSucursal(productoId, tenantId, sucursalId, hoy)
                : stockLoteRepository.findDisponibles(productoId, tenantId, hoy);

        return lotes.stream().map(l -> {
            String proveedorNombre = null;
            if (l.getProveedorId() != null) {
                proveedorNombre = proveedorRepository.findById(l.getProveedorId())
                        .map(Proveedor::getNombre).orElse(null);
            }
            long dias = ChronoUnit.DAYS.between(hoy, l.getFechaVencimiento());
            return StockLoteDisponibleDTO.builder()
                    .id(l.getId())
                    .lote(l.getLote())
                    .fechaVencimiento(l.getFechaVencimiento())
                    .stockActual(l.getStockActual())
                    .proveedorId(l.getProveedorId())
                    .proveedorNombre(proveedorNombre)
                    .diasParaVencer((int) dias)
                    .precioVenta(l.getPrecioVenta())
                    .costoUnitario(l.getCostoUnitario())
                    .build();
        }).collect(Collectors.toList());
    }

    /**
     * Descuenta stock en orden FEFO (más próximo a vencer primero).
     * Solo descuenta de lotes NO vencidos.
     * Si el total de lotes vigentes no alcanza, consume lo que hay y termina
     * (el stockActual del producto ya fue descontado por el flujo normal).
     */
    @Transactional
    public void descontarFefo(String tenantId, Long productoId, Long sucursalId, int cantidad) {
        LocalDate hoy = LocalDate.now();
        List<StockLote> lotes = sucursalId != null
                ? stockLoteRepository.findVigentesFefoConSucursal(productoId, tenantId, sucursalId, hoy)
                : stockLoteRepository.findVigentesFefo(productoId, tenantId, hoy);

        int restante = cantidad;
        for (StockLote lote : lotes) {
            if (restante <= 0) break;
            int descontar = Math.min(lote.getStockActual(), restante);
            lote.setStockActual(lote.getStockActual() - descontar);
            stockLoteRepository.save(lote);
            restante -= descontar;
        }
        if (restante > 0) {
            log.warn("⚠️ FEFO: producto={} sin suficientes lotes vigentes (faltaron {} unidades)", productoId, restante);
        }
    }

    /**
     * Devuelve el stock vigente (no vencido) de un producto.
     * Si el producto no tiene lotes registrados devuelve null (el caller usa stockActual normal).
     */
    public Integer getStockVigente(String tenantId, Long productoId, Long sucursalId) {
        if (!stockLoteRepository.existsByProductoIdAndTenantId(productoId, tenantId)) return null;
        LocalDate hoy = LocalDate.now();
        return sucursalId != null
                ? stockLoteRepository.sumStockVigenteConSucursal(productoId, tenantId, sucursalId, hoy)
                : stockLoteRepository.sumStockVigente(productoId, tenantId, hoy);
    }

    /**
     * Batch: mapa productoId → stockVigente para una lista de productos.
     * Solo incluye productos que tienen lotes. Los demás no aparecen en el mapa.
     */
    public Map<Long, Integer> getStockVigenteBatch(String tenantId, List<Long> productoIds, Long sucursalId) {
        if (productoIds == null || productoIds.isEmpty()) return Map.of();
        LocalDate hoy = LocalDate.now();

        // Solo consultar productos que realmente tienen lotes
        List<Long> conLotes = stockLoteRepository.findProductosConLotes(productoIds, tenantId);
        if (conLotes.isEmpty()) return Map.of();

        List<Object[]> rows = sucursalId != null
                ? stockLoteRepository.sumStockVigenteByProductoIdsConSucursal(conLotes, tenantId, sucursalId, hoy)
                : stockLoteRepository.sumStockVigenteByProductoIds(conLotes, tenantId, hoy);

        return rows.stream().collect(Collectors.toMap(
                r -> (Long) r[0],
                r -> ((Number) r[1]).intValue()
        ));
    }

    /**
     * Ajusta un lote específico identificado por movimientoId.
     * Devuelve el delta aplicado (positivo o negativo) para que el caller
     * actualice producto.stockActual en consecuencia.
     * Retorna 0 si el lote no existe.
     */
    @Transactional
    public int ajustarLoteEspecifico(Long movimientoId, int nuevaCantidad) {
        return stockLoteRepository.findByMovimientoId(movimientoId)
                .map(lote -> {
                    int delta = nuevaCantidad - lote.getStockActual();
                    lote.setStockActual(Math.max(0, nuevaCantidad));
                    stockLoteRepository.save(lote);
                    log.info("✅ Lote específico ajustado: movimientoId={} {} → {} (delta={})",
                            movimientoId, lote.getStockActual() - delta, nuevaCantidad, delta);
                    return delta;
                })
                .orElseGet(() -> {
                    log.warn("⚠️ No se encontró stock_lote para movimientoId={}", movimientoId);
                    return 0;
                });
    }

    /**
     * Sincroniza stock_lotes cuando se hace un AJUSTE manual de inventario.
     * - Delta negativo: descuenta de los lotes más próximos a vencer (FEFO).
     * - Delta positivo: suma al lote con vencimiento más lejano (el más fresco).
     * Si el producto no tiene lotes no hace nada.
     */
    @Transactional
    public void ajustarStockLotes(String tenantId, Long productoId, Long sucursalId, int delta) {
        if (delta == 0) return;
        if (!stockLoteRepository.existsByProductoIdAndTenantId(productoId, tenantId)) return;

        LocalDate hoy = LocalDate.now();

        if (delta < 0) {
            // Reducción: descontar en orden FEFO (más próximo a vencer primero)
            List<StockLote> lotes = sucursalId != null
                    ? stockLoteRepository.findVigentesFefoConSucursal(productoId, tenantId, sucursalId, hoy)
                    : stockLoteRepository.findVigentesFefo(productoId, tenantId, hoy);
            int restante = Math.abs(delta);
            for (StockLote lote : lotes) {
                if (restante <= 0) break;
                int descontar = Math.min(lote.getStockActual(), restante);
                lote.setStockActual(lote.getStockActual() - descontar);
                stockLoteRepository.save(lote);
                restante -= descontar;
            }
            if (restante > 0) {
                log.warn("⚠️ Ajuste FEFO: producto={} delta={} — {} unidades sin lote asignado", productoId, delta, restante);
            }
        } else {
            // Aumento: sumar al lote con vencimiento más lejano
            List<StockLote> lotes = stockLoteRepository.findVigentesInverso(productoId, tenantId, hoy);
            if (!lotes.isEmpty()) {
                StockLote lote = lotes.get(0);
                lote.setStockActual(lote.getStockActual() + delta);
                stockLoteRepository.save(lote);
            } else {
                log.warn("⚠️ Ajuste positivo: producto={} sin lotes vigentes para absorber +{}", productoId, delta);
            }
        }
        log.info("✅ stock_lotes ajustado: producto={} delta={}", productoId, delta);
    }

    /**
     * Actualiza proveedor y/o precio de venta de un lote existente identificado por movimientoId.
     */
    @Transactional
    public void actualizarProveedorLote(Long movimientoId, Long proveedorId, BigDecimal precioVenta) {
        stockLoteRepository.findByMovimientoId(movimientoId).ifPresent(lote -> {
            lote.setProveedorId(proveedorId);
            if (precioVenta != null) lote.setPrecioVenta(precioVenta);
            stockLoteRepository.save(lote);
            log.info("✅ Lote actualizado: movimientoId={} proveedorId={} precioVenta={}", movimientoId, proveedorId, precioVenta);
        });
    }

    /** Busca un lote por id (para validación en VentaController). */
    public java.util.Optional<StockLote> findLoteById(Long loteId) {
        return stockLoteRepository.findById(loteId);
    }

    /**
     * Stock actual de un lote específico (para la página Lotes).
     */
    public Map<Long, Integer> getStockPorMovimientoIds(List<Long> movimientoIds) {
        if (movimientoIds == null || movimientoIds.isEmpty()) return Map.of();
        return stockLoteRepository.findByMovimientoIdIn(movimientoIds).stream()
                .collect(Collectors.toMap(StockLote::getMovimientoId, StockLote::getStockActual));
    }

    /**
     * PrecioVenta por movimientoId (para enriquecer LoteVencimientoDTO con precio del lote).
     */
    public Map<Long, java.math.BigDecimal> getPrecioVentaPorMovimientoIds(List<Long> movimientoIds) {
        if (movimientoIds == null || movimientoIds.isEmpty()) return Map.of();
        return stockLoteRepository.findByMovimientoIdIn(movimientoIds).stream()
                .filter(l -> l.getPrecioVenta() != null)
                .collect(Collectors.toMap(StockLote::getMovimientoId, StockLote::getPrecioVenta));
    }

    /**
     * Nombre del proveedor por movimientoId (para enriquecer LoteVencimientoDTO).
     * Solo incluye entradas donde el lote tiene proveedor asignado.
     */
    public Map<Long, String> getProveedorNombrePorMovimientoIds(List<Long> movimientoIds) {
        if (movimientoIds == null || movimientoIds.isEmpty()) return Map.of();
        List<StockLote> lotes = stockLoteRepository.findByMovimientoIdIn(movimientoIds);

        Set<Long> proveedorIds = lotes.stream()
                .filter(l -> l.getProveedorId() != null)
                .map(StockLote::getProveedorId)
                .collect(Collectors.toSet());

        Map<Long, String> nombrePorProveedorId = new HashMap<>();
        if (!proveedorIds.isEmpty()) {
            proveedorRepository.findAllById(proveedorIds)
                    .forEach(p -> nombrePorProveedorId.put(p.getId(), p.getNombre()));
        }

        Map<Long, String> result = new HashMap<>();
        lotes.stream()
                .filter(l -> l.getProveedorId() != null)
                .forEach(l -> result.put(l.getMovimientoId(),
                        nombrePorProveedorId.get(l.getProveedorId())));
        return result;
    }
}
