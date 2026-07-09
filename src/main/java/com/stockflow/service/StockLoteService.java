package com.stockflow.service;

import com.stockflow.entity.StockLote;
import com.stockflow.repository.StockLoteRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

@Slf4j
@Service
@RequiredArgsConstructor
public class StockLoteService {

    private final StockLoteRepository stockLoteRepository;

    /**
     * Registra un nuevo lote cuando se crea un movimiento de ENTRADA con fechaVencimiento.
     */
    @Transactional
    public void registrarLote(String tenantId, Long movimientoId, Long productoId,
                               Long sucursalId, String lote, LocalDate fechaVencimiento,
                               Integer cantidad) {
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
                .build();
        stockLoteRepository.save(stockLote);
        log.info("✅ StockLote registrado: producto={} lote={} fecha={} qty={}",
                productoId, lote, fechaVencimiento, cantidad);
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
     * Stock actual de un lote específico (para la página Lotes).
     */
    public Map<Long, Integer> getStockPorMovimientoIds(List<Long> movimientoIds) {
        if (movimientoIds == null || movimientoIds.isEmpty()) return Map.of();
        return stockLoteRepository.findByMovimientoIdIn(movimientoIds).stream()
                .collect(Collectors.toMap(StockLote::getMovimientoId, StockLote::getStockActual));
    }
}
