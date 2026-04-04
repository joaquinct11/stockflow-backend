package com.stockflow.service.impl;

import com.stockflow.dto.reportes.*;
import com.stockflow.entity.Producto;
import com.stockflow.repository.MovimientoInventarioRepository;
import com.stockflow.repository.ProductoRepository;
import com.stockflow.repository.RecepcionRepository;
import com.stockflow.repository.VentaRepository;
import com.stockflow.service.ReportesService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.util.ArrayList;
import java.util.List;

@Slf4j
@Service
@RequiredArgsConstructor
public class ReportesServiceImpl implements ReportesService {

    private final ProductoRepository productoRepository;
    private final MovimientoInventarioRepository movimientoRepository;
    private final RecepcionRepository recepcionRepository;
    private final VentaRepository ventaRepository;

    @Override
    @Transactional(readOnly = true)
    public ReportesResumenDTO obtenerResumen(String tenantId, LocalDate desde, LocalDate hasta) {
        log.info("📊 Generando resumen de reportes para tenant={} rango=[{}, {}]", tenantId, desde, hasta);

        LocalDateTime inicio = desde.atStartOfDay();
        LocalDateTime fin = hasta.atTime(LocalTime.MAX);

        return ReportesResumenDTO.builder()
                .rango(RangoDTO.builder().desde(desde).hasta(hasta).build())
                .inventario(buildInventario(tenantId))
                .movimientos(buildMovimientos(tenantId, inicio, fin))
                .comprasRecepciones(buildComprasRecepciones(tenantId, inicio, fin))
                .ventas(buildVentas(tenantId, inicio, fin))
                .build();
    }

    private InventarioResumenDTO buildInventario(String tenantId) {
        long totalProductos = productoRepository.countByTenantId(tenantId);

        List<Producto> bajoStock = productoRepository.findProductosBajoStock(tenantId);
        List<ProductoBajoStockDTO> productosBajoStock = bajoStock.stream()
                .limit(10)
                .map(p -> ProductoBajoStockDTO.builder()
                        .productoId(p.getId())
                        .nombre(p.getNombre())
                        .stockActual(p.getStockActual())
                        .stockMinimo(p.getStockMinimo())
                        .build())
                .toList();

        BigDecimal valorizacion = productoRepository.calcularValorizacionStock(tenantId);

        return InventarioResumenDTO.builder()
                .totalProductos(totalProductos)
                .productosBajoStock(productosBajoStock)
                .valorizacionStock(valorizacion)
                .build();
    }

    private MovimientosResumenDTO buildMovimientos(String tenantId, LocalDateTime inicio, LocalDateTime fin) {
        long entradas = movimientoRepository.sumCantidadByTenantIdAndTipoAndPeriodo(tenantId, "ENTRADA", inicio, fin);
        long salidas = movimientoRepository.sumCantidadByTenantIdAndTipoAndPeriodo(tenantId, "SALIDA", inicio, fin);

        List<Object[]> rawTop = movimientoRepository.findTopMovimientosProductos(tenantId, inicio, fin);
        List<TopMovimientoProductoDTO> topMovimientos = new ArrayList<>();
        int limit = Math.min(rawTop.size(), 10);
        for (int i = 0; i < limit; i++) {
            Object[] row = rawTop.get(i);
            topMovimientos.add(TopMovimientoProductoDTO.builder()
                    .productoId(((Number) row[0]).longValue())
                    .nombre((String) row[1])
                    .tipo((String) row[2])
                    .cantidadTotal(((Number) row[3]).longValue())
                    .build());
        }

        return MovimientosResumenDTO.builder()
                .entradasCantidad(entradas)
                .salidasCantidad(salidas)
                .topMovimientosProductos(topMovimientos)
                .build();
    }

    private ComprasRecepcionesResumenDTO buildComprasRecepciones(String tenantId, LocalDateTime inicio, LocalDateTime fin) {
        long recepcionesConfirmadas = recepcionRepository.countConfirmadasByTenantIdAndPeriodo(tenantId, inicio, fin);
        long unidadesRecibidas = recepcionRepository.sumUnidadesRecibidasByTenantIdAndPeriodo(tenantId, inicio, fin);
        BigDecimal montoCompras = recepcionRepository.sumMontoComprasByTenantIdAndPeriodo(tenantId, inicio, fin);

        return ComprasRecepcionesResumenDTO.builder()
                .recepcionesConfirmadasCount(recepcionesConfirmadas)
                .unidadesRecibidas(unidadesRecibidas)
                .montoComprasEstimado(montoCompras)
                .build();
    }

    private VentasResumenDTO buildVentas(String tenantId, LocalDateTime inicio, LocalDateTime fin) {
        long ventasCount = ventaRepository.countByTenantIdAndPeriodo(tenantId, inicio, fin);

        if (ventasCount == 0) {
            return null;
        }

        BigDecimal ingresosTotal = ventaRepository.sumTotalByTenantIdAndPeriodo(tenantId, inicio, fin);
        if (ingresosTotal == null) ingresosTotal = BigDecimal.ZERO;

        BigDecimal ticketPromedio = ventasCount > 0
                ? ingresosTotal.divide(BigDecimal.valueOf(ventasCount), 2, RoundingMode.HALF_UP)
                : BigDecimal.ZERO;

        List<Object[]> rawTop = ventaRepository.findTopProductosVendidos(tenantId, inicio, fin);
        List<TopProductoVendidoDTO> topProductos = new ArrayList<>();
        int limit = Math.min(rawTop.size(), 10);
        for (int i = 0; i < limit; i++) {
            Object[] row = rawTop.get(i);
            topProductos.add(TopProductoVendidoDTO.builder()
                    .productoId(((Number) row[0]).longValue())
                    .nombre((String) row[1])
                    .cantidadVendida(((Number) row[2]).longValue())
                    .ingresos((BigDecimal) row[3])
                    .build());
        }

        BigDecimal costoTotal = ventaRepository.sumCostoVentasByTenantIdAndPeriodo(tenantId, inicio, fin);
        BigDecimal margen = costoTotal != null ? ingresosTotal.subtract(costoTotal) : null;

        return VentasResumenDTO.builder()
                .ventasCount(ventasCount)
                .ingresosTotal(ingresosTotal)
                .ticketPromedio(ticketPromedio)
                .topProductosVendidos(topProductos)
                .margenEstimado(margen)
                .build();
    }
}
