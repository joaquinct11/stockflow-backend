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
    ReportesResumenDTO obtenerResumen(String tenantId, Long sucursalId, LocalDate desde, LocalDate hasta);

    List<VentaTendenciaDTO> tendenciaVentas(String tenantId, Long sucursalId, LocalDate desde, LocalDate hasta, String agrupacion);

    List<VentaVendedorDTO> ventasPorVendedor(String tenantId, Long sucursalId, LocalDate desde, LocalDate hasta, int limit);

    List<VentaCategoriaDTO> ventasPorCategoria(String tenantId, Long sucursalId, LocalDate desde, LocalDate hasta, int limit);

    List<VentaMetodoPagoDTO> ventasPorMetodoPago(String tenantId, Long sucursalId, LocalDate desde, LocalDate hasta);

    List<ProductoVentaDTO> productosVendidos(String tenantId, Long sucursalId, LocalDate desde, LocalDate hasta, int limit,
                                             String orden, String metrica);

    List<ProductoAbcDTO> clasificacionAbc(String tenantId, Long sucursalId, LocalDate desde, LocalDate hasta, int limit);

    List<SlowMoverDTO> slowMovers(String tenantId, int diasSinSalida, int limit);

    List<CoberturaProductoDTO> coberturaInventario(String tenantId, Long sucursalId, LocalDate desde, LocalDate hasta, int limit);

    List<CompraProveedorDTO> comprasPorProveedor(String tenantId, Long sucursalId, LocalDate desde, LocalDate hasta, int limit);

    FinancieroDTO getFinanciero(String tenantId, Long sucursalId, LocalDate desde, LocalDate hasta);

    VencimientosRiesgoDTO getVencimientosRiesgo(String tenantId);

    List<ClienteReporteDTO> getTopClientes(String tenantId, Long sucursalId, LocalDate desde, LocalDate hasta, int limit);
}
