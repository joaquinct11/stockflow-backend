package com.stockflow.controller;

import com.stockflow.dto.reportes.*;
import com.stockflow.exception.BadRequestException;
import com.stockflow.service.ReportesService;
import com.stockflow.util.TenantContext;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.time.LocalDate;
import java.util.List;

@Slf4j
@RestController
@RequestMapping("/reportes")
@RequiredArgsConstructor
public class ReportesController {

    private final ReportesService reportesService;

    /**
     * GET /api/reportes/resumen?desde=YYYY-MM-DD&hasta=YYYY-MM-DD
     * <p>
     * Devuelve un resumen compacto con métricas de inventario, movimientos,
     * compras/recepciones y ventas para el tenant actual en el rango indicado.
     */
    @GetMapping("/resumen")
    @PreAuthorize("hasAnyRole('ADMIN', 'GERENTE') or hasAuthority('PERM_VER_REPORTES')")
    public ResponseEntity<ReportesResumenDTO> obtenerResumen(
            @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate desde,
            @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate hasta) {

        validarRango(desde, hasta);
        String tenantId = TenantContext.getCurrentTenant();
        log.info("📊 Solicitud de reporte resumen: tenant={} rango=[{}, {}]", tenantId, desde, hasta);

        ReportesResumenDTO resumen = reportesService.obtenerResumen(tenantId, desde, hasta);
        return ResponseEntity.ok(resumen);
    }

    // ── Ventas ────────────────────────────────────────────────────────────────

    /**
     * GET /api/reportes/ventas/tendencia?desde&hasta&agrupacion=DIA|SEMANA|MES
     * <p>
     * Tendencia de ventas agrupada por día, semana o mes.
     */
    @GetMapping("/ventas/tendencia")
    @PreAuthorize("hasAnyRole('ADMIN', 'GERENTE') or hasAuthority('PERM_VER_REPORTES')")
    public ResponseEntity<List<VentaTendenciaDTO>> tendenciaVentas(
            @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate desde,
            @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate hasta,
            @RequestParam(defaultValue = "DIA") String agrupacion) {

        validarRango(desde, hasta);
        if (!List.of("DIA", "SEMANA", "MES").contains(agrupacion.toUpperCase())) {
            throw new BadRequestException("El parámetro 'agrupacion' debe ser DIA, SEMANA o MES");
        }
        String tenantId = TenantContext.getCurrentTenant();
        log.info("📈 Tendencia ventas: tenant={} rango=[{}, {}] agrupacion={}", tenantId, desde, hasta, agrupacion);

        return ResponseEntity.ok(reportesService.tendenciaVentas(tenantId, desde, hasta, agrupacion));
    }

    /**
     * GET /api/reportes/ventas/por-vendedor?desde&hasta&limit=20
     * <p>
     * Ventas agrupadas por vendedor, ordenadas por ingresos desc.
     */
    @GetMapping("/ventas/por-vendedor")
    @PreAuthorize("hasAnyRole('ADMIN', 'GERENTE') or hasAuthority('PERM_VER_REPORTES')")
    public ResponseEntity<List<VentaVendedorDTO>> ventasPorVendedor(
            @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate desde,
            @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate hasta,
            @RequestParam(defaultValue = "20") int limit) {

        validarRango(desde, hasta);
        String tenantId = TenantContext.getCurrentTenant();
        log.info("👤 Ventas por vendedor: tenant={} rango=[{}, {}]", tenantId, desde, hasta);

        return ResponseEntity.ok(reportesService.ventasPorVendedor(tenantId, desde, hasta, limit));
    }

    /**
     * GET /api/reportes/ventas/por-categoria?desde&hasta&limit=50
     * <p>
     * Ventas agrupadas por categoría de producto. null/blank → "Sin categoría".
     */
    @GetMapping("/ventas/por-categoria")
    @PreAuthorize("hasAnyRole('ADMIN', 'GERENTE') or hasAuthority('PERM_VER_REPORTES')")
    public ResponseEntity<List<VentaCategoriaDTO>> ventasPorCategoria(
            @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate desde,
            @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate hasta,
            @RequestParam(defaultValue = "50") int limit) {

        validarRango(desde, hasta);
        String tenantId = TenantContext.getCurrentTenant();
        log.info("🏷️ Ventas por categoría: tenant={} rango=[{}, {}]", tenantId, desde, hasta);

        return ResponseEntity.ok(reportesService.ventasPorCategoria(tenantId, desde, hasta, limit));
    }

    /**
     * GET /api/reportes/ventas/por-metodo-pago?desde&hasta
     * <p>
     * Ventas agrupadas por método de pago con porcentaje sobre el total de ingresos.
     */
    @GetMapping("/ventas/por-metodo-pago")
    @PreAuthorize("hasAnyRole('ADMIN', 'GERENTE') or hasAuthority('PERM_VER_REPORTES')")
    public ResponseEntity<List<VentaMetodoPagoDTO>> ventasPorMetodoPago(
            @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate desde,
            @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate hasta) {

        validarRango(desde, hasta);
        String tenantId = TenantContext.getCurrentTenant();
        log.info("💳 Ventas por método de pago: tenant={} rango=[{}, {}]", tenantId, desde, hasta);

        return ResponseEntity.ok(reportesService.ventasPorMetodoPago(tenantId, desde, hasta));
    }

    /**
     * GET /api/reportes/ventas/productos?desde&hasta&limit=20&orden=MAS|MENOS&metrica=UNIDADES|INGRESOS
     * <p>
     * Productos vendidos ordenados por la métrica y dirección indicadas.
     */
    @GetMapping("/ventas/productos")
    @PreAuthorize("hasAnyRole('ADMIN', 'GERENTE') or hasAuthority('PERM_VER_REPORTES')")
    public ResponseEntity<List<ProductoVentaDTO>> productosVendidos(
            @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate desde,
            @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate hasta,
            @RequestParam(defaultValue = "20") int limit,
            @RequestParam(defaultValue = "MAS") String orden,
            @RequestParam(defaultValue = "UNIDADES") String metrica) {

        validarRango(desde, hasta);
        if (!List.of("MAS", "MENOS").contains(orden.toUpperCase())) {
            throw new BadRequestException("El parámetro 'orden' debe ser MAS o MENOS");
        }
        if (!List.of("UNIDADES", "INGRESOS").contains(metrica.toUpperCase())) {
            throw new BadRequestException("El parámetro 'metrica' debe ser UNIDADES o INGRESOS");
        }
        String tenantId = TenantContext.getCurrentTenant();
        log.info("📦 Productos vendidos: tenant={} rango=[{}, {}] orden={} metrica={}", tenantId, desde, hasta, orden, metrica);

        return ResponseEntity.ok(reportesService.productosVendidos(tenantId, desde, hasta, limit, orden, metrica));
    }

    // ── Inventario ────────────────────────────────────────────────────────────

    /**
     * GET /api/reportes/inventario/abc?desde&hasta&limit=200
     * <p>
     * Clasificación ABC de productos por ingresos en el período.
     * A = acumulado 0-80%, B = 80-95%, C = 95-100%.
     */
    @GetMapping("/inventario/abc")
    @PreAuthorize("hasAnyRole('ADMIN', 'GERENTE') or hasAuthority('PERM_VER_REPORTES')")
    public ResponseEntity<List<ProductoAbcDTO>> clasificacionAbc(
            @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate desde,
            @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate hasta,
            @RequestParam(defaultValue = "200") int limit) {

        validarRango(desde, hasta);
        String tenantId = TenantContext.getCurrentTenant();
        log.info("🔡 ABC: tenant={} rango=[{}, {}]", tenantId, desde, hasta);

        return ResponseEntity.ok(reportesService.clasificacionAbc(tenantId, desde, hasta, limit));
    }

    /**
     * GET /api/reportes/inventario/slow-movers?diasSinSalida=30&limit=50
     * <p>
     * Productos activos con stock > 0 y sin movimientos de SALIDA en el período indicado.
     */
    @GetMapping("/inventario/slow-movers")
    @PreAuthorize("hasAnyRole('ADMIN', 'GERENTE') or hasAuthority('PERM_VER_REPORTES')")
    public ResponseEntity<List<SlowMoverDTO>> slowMovers(
            @RequestParam(defaultValue = "30") int diasSinSalida,
            @RequestParam(defaultValue = "50") int limit) {

        if (diasSinSalida <= 0) {
            throw new BadRequestException("El parámetro 'diasSinSalida' debe ser mayor a 0");
        }
        String tenantId = TenantContext.getCurrentTenant();
        log.info("🐢 Slow movers: tenant={} diasSinSalida={}", tenantId, diasSinSalida);

        return ResponseEntity.ok(reportesService.slowMovers(tenantId, diasSinSalida, limit));
    }

    /**
     * GET /api/reportes/inventario/cobertura?desde&hasta&limit=50
     * <p>
     * Cobertura de inventario: días que dura el stock actual al ritmo de salidas del período.
     */
    @GetMapping("/inventario/cobertura")
    @PreAuthorize("hasAnyRole('ADMIN', 'GERENTE') or hasAuthority('PERM_VER_REPORTES')")
    public ResponseEntity<List<CoberturaProductoDTO>> coberturaInventario(
            @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate desde,
            @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate hasta,
            @RequestParam(defaultValue = "50") int limit) {

        validarRango(desde, hasta);
        String tenantId = TenantContext.getCurrentTenant();
        log.info("📐 Cobertura inventario: tenant={} rango=[{}, {}]", tenantId, desde, hasta);

        return ResponseEntity.ok(reportesService.coberturaInventario(tenantId, desde, hasta, limit));
    }

    // ── Compras ───────────────────────────────────────────────────────────────

    /**
     * GET /api/reportes/compras/por-proveedor?desde&hasta&limit=20
     * <p>
     * Recepciones confirmadas agrupadas por proveedor.
     * Monto estimado usando costoUnitario actual del producto (no histórico).
     */
    @GetMapping("/compras/por-proveedor")
    @PreAuthorize("hasAnyRole('ADMIN', 'GERENTE') or hasAuthority('PERM_VER_REPORTES')")
    public ResponseEntity<List<CompraProveedorDTO>> comprasPorProveedor(
            @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate desde,
            @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate hasta,
            @RequestParam(defaultValue = "20") int limit) {

        validarRango(desde, hasta);
        String tenantId = TenantContext.getCurrentTenant();
        log.info("🛒 Compras por proveedor: tenant={} rango=[{}, {}]", tenantId, desde, hasta);

        return ResponseEntity.ok(reportesService.comprasPorProveedor(tenantId, desde, hasta, limit));
    }

    // ── Helper ────────────────────────────────────────────────────────────────

    private void validarRango(LocalDate desde, LocalDate hasta) {
        if (desde == null || hasta == null) {
            throw new BadRequestException("Los parámetros 'desde' y 'hasta' son obligatorios (formato YYYY-MM-DD)");
        }
        if (desde.isAfter(hasta)) {
            throw new BadRequestException("El parámetro 'desde' no puede ser posterior a 'hasta'");
        }
    }
}

