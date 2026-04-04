package com.stockflow.service;

import com.stockflow.dto.reportes.*;

import java.time.LocalDate;
import java.util.List;

public interface ReportesService {

    /**
     * Genera un resumen de reportes para el tenant actual en el rango de fechas indicado.
     *
     * @param tenantId identificador del tenant
     * @param desde    fecha de inicio del rango (inclusive)
     * @param hasta    fecha de fin del rango (inclusive)
     * @return DTO con métricas de inventario, movimientos, compras/recepciones y ventas
     */
    ReportesResumenDTO obtenerResumen(String tenantId, LocalDate desde, LocalDate hasta);

    /**
     * Tendencia de ventas agrupada por DIA, SEMANA o MES.
     *
     * @param agrupacion "DIA", "SEMANA" o "MES"
     */
    List<VentaTendenciaDTO> tendenciaVentas(String tenantId, LocalDate desde, LocalDate hasta, String agrupacion);

    /** Ventas agrupadas por vendedor, ordenadas por ingresos desc. */
    List<VentaVendedorDTO> ventasPorVendedor(String tenantId, LocalDate desde, LocalDate hasta, int limit);

    /** Ventas agrupadas por categoría de producto, null/blank → "Sin categoría". */
    List<VentaCategoriaDTO> ventasPorCategoria(String tenantId, LocalDate desde, LocalDate hasta, int limit);

    /** Ventas agrupadas por método de pago con porcentaje sobre el total. */
    List<VentaMetodoPagoDTO> ventasPorMetodoPago(String tenantId, LocalDate desde, LocalDate hasta);

    /**
     * Productos vendidos ordenados por metrica (UNIDADES o INGRESOS) y dirección (MAS desc / MENOS asc).
     *
     * @param orden   "MAS" (desc) o "MENOS" (asc)
     * @param metrica "UNIDADES" o "INGRESOS"
     */
    List<ProductoVentaDTO> productosVendidos(String tenantId, LocalDate desde, LocalDate hasta, int limit,
                                             String orden, String metrica);

    /**
     * Clasificación ABC de productos por ingresos en el período.
     * A = acumulado 0-80%, B = 80-95%, C = 95-100%.
     */
    List<ProductoAbcDTO> clasificacionAbc(String tenantId, LocalDate desde, LocalDate hasta, int limit);

    /**
     * Productos activos con stock > 0 y sin movimientos SALIDA desde hace diasSinSalida días.
     */
    List<SlowMoverDTO> slowMovers(String tenantId, int diasSinSalida, int limit);

    /**
     * Cobertura de inventario: días que dura el stock actual al ritmo de salidas del período.
     * diasCobertura = null cuando promedioSalidasDiarias = 0.
     */
    List<CoberturaProductoDTO> coberturaInventario(String tenantId, LocalDate desde, LocalDate hasta, int limit);

    /** Compras (recepciones confirmadas) agrupadas por proveedor. */
    List<CompraProveedorDTO> comprasPorProveedor(String tenantId, LocalDate desde, LocalDate hasta, int limit);
}
