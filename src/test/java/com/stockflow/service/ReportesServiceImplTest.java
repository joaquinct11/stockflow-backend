package com.stockflow.service;

import com.stockflow.dto.reportes.*;
import com.stockflow.entity.Producto;
import com.stockflow.repository.MovimientoInventarioRepository;
import com.stockflow.repository.ProductoRepository;
import com.stockflow.repository.RecepcionRepository;
import com.stockflow.repository.VentaRepository;
import com.stockflow.service.impl.ReportesServiceImpl;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.util.Collections;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.when;

// Lenient mode is required because @BeforeEach sets up stubs shared by the original
// obtenerResumen tests; new tests for individual methods don't invoke obtenerResumen
// and therefore don't consume all those shared stubs.
@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class ReportesServiceImplTest {

    @Mock
    private ProductoRepository productoRepository;
    @Mock
    private MovimientoInventarioRepository movimientoRepository;
    @Mock
    private RecepcionRepository recepcionRepository;
    @Mock
    private VentaRepository ventaRepository;

    @InjectMocks
    private ReportesServiceImpl reportesService;

    private static final String TENANT = "tenant-test";
    private static final LocalDate DESDE = LocalDate.of(2026, 1, 1);
    private static final LocalDate HASTA = LocalDate.of(2026, 1, 31);
    private static final LocalDateTime INICIO = DESDE.atStartOfDay();
    private static final LocalDateTime FIN = HASTA.atTime(LocalTime.MAX);

    @BeforeEach
    void setUp() {
        // inventario sin datos
        when(productoRepository.countByTenantId(TENANT)).thenReturn(0L);
        when(productoRepository.findProductosBajoStock(TENANT)).thenReturn(Collections.emptyList());
        when(productoRepository.calcularValorizacionStock(TENANT)).thenReturn(null);

        // movimientos sin datos
        when(movimientoRepository.sumCantidadByTenantIdAndTipoAndPeriodo(eq(TENANT), eq("ENTRADA"), any(), any())).thenReturn(0L);
        when(movimientoRepository.sumCantidadByTenantIdAndTipoAndPeriodo(eq(TENANT), eq("SALIDA"), any(), any())).thenReturn(0L);
        when(movimientoRepository.findTopMovimientosProductos(eq(TENANT), any(), any())).thenReturn(Collections.emptyList());

        // recepciones sin datos
        when(recepcionRepository.countConfirmadasByTenantIdAndPeriodo(eq(TENANT), any(), any())).thenReturn(0L);
        when(recepcionRepository.sumUnidadesRecibidasByTenantIdAndPeriodo(eq(TENANT), any(), any())).thenReturn(0L);
        when(recepcionRepository.sumMontoComprasByTenantIdAndPeriodo(eq(TENANT), any(), any())).thenReturn(null);
    }

    @Test
    void obtenerResumen_sinDatos_devuelveEstructuraCompleta() {
        // ventas sin datos → sección ventas null
        when(ventaRepository.countByTenantIdAndPeriodo(eq(TENANT), any(), any())).thenReturn(0L);

        ReportesResumenDTO resultado = reportesService.obtenerResumen(TENANT, DESDE, HASTA);

        assertThat(resultado).isNotNull();

        // rango
        assertThat(resultado.getRango()).isNotNull();
        assertThat(resultado.getRango().getDesde()).isEqualTo(DESDE);
        assertThat(resultado.getRango().getHasta()).isEqualTo(HASTA);

        // inventario
        InventarioResumenDTO inv = resultado.getInventario();
        assertThat(inv).isNotNull();
        assertThat(inv.getTotalProductos()).isZero();
        assertThat(inv.getProductosBajoStock()).isEmpty();
        assertThat(inv.getValorizacionStock()).isNull();

        // movimientos
        MovimientosResumenDTO mov = resultado.getMovimientos();
        assertThat(mov).isNotNull();
        assertThat(mov.getEntradasCantidad()).isZero();
        assertThat(mov.getSalidasCantidad()).isZero();
        assertThat(mov.getTopMovimientosProductos()).isEmpty();

        // compras/recepciones
        ComprasRecepcionesResumenDTO comp = resultado.getComprasRecepciones();
        assertThat(comp).isNotNull();
        assertThat(comp.getRecepcionesConfirmadasCount()).isZero();
        assertThat(comp.getUnidadesRecibidas()).isZero();
        assertThat(comp.getMontoComprasEstimado()).isNull();

        // ventas null cuando no hay ventas en el rango
        assertThat(resultado.getVentas()).isNull();
    }

    @Test
    void obtenerResumen_conInventario_devuelveProductosBajoStock() {
        when(ventaRepository.countByTenantIdAndPeriodo(eq(TENANT), any(), any())).thenReturn(0L);

        Producto prod = Producto.builder()
                .id(1L)
                .nombre("Producto A")
                .stockActual(2)
                .stockMinimo(10)
                .tenantId(TENANT)
                .build();

        when(productoRepository.countByTenantId(TENANT)).thenReturn(1L);
        when(productoRepository.findProductosBajoStock(TENANT)).thenReturn(List.of(prod));
        when(productoRepository.calcularValorizacionStock(TENANT)).thenReturn(new BigDecimal("200.00"));

        ReportesResumenDTO resultado = reportesService.obtenerResumen(TENANT, DESDE, HASTA);

        InventarioResumenDTO inv = resultado.getInventario();
        assertThat(inv.getTotalProductos()).isEqualTo(1L);
        assertThat(inv.getProductosBajoStock()).hasSize(1);
        assertThat(inv.getProductosBajoStock().get(0).getNombre()).isEqualTo("Producto A");
        assertThat(inv.getValorizacionStock()).isEqualByComparingTo("200.00");
    }

    @Test
    void obtenerResumen_conVentas_devuelveSeccionVentas() {
        when(ventaRepository.countByTenantIdAndPeriodo(eq(TENANT), any(), any())).thenReturn(3L);
        when(ventaRepository.sumTotalByTenantIdAndPeriodo(eq(TENANT), any(), any())).thenReturn(new BigDecimal("300.00"));
        when(ventaRepository.findTopProductosVendidos(eq(TENANT), any(), any())).thenReturn(Collections.emptyList());
        when(ventaRepository.sumCostoVentasByTenantIdAndPeriodo(eq(TENANT), any(), any())).thenReturn(new BigDecimal("150.00"));

        ReportesResumenDTO resultado = reportesService.obtenerResumen(TENANT, DESDE, HASTA);

        VentasResumenDTO ventas = resultado.getVentas();
        assertThat(ventas).isNotNull();
        assertThat(ventas.getVentasCount()).isEqualTo(3L);
        assertThat(ventas.getIngresosTotal()).isEqualByComparingTo("300.00");
        assertThat(ventas.getTicketPromedio()).isEqualByComparingTo("100.00");
        assertThat(ventas.getMargenEstimado()).isEqualByComparingTo("150.00");
    }

    // ── Tests para nuevos endpoints ───────────────────────────────────────────

    @Test
    void tendenciaVentas_sinDatos_devuelveListaVacia() {
        when(ventaRepository.findTendenciaDiaria(eq(TENANT), any(), any()))
                .thenReturn(Collections.emptyList());

        List<VentaTendenciaDTO> resultado = reportesService.tendenciaVentas(TENANT, DESDE, HASTA, "DIA");

        assertThat(resultado).isNotNull().isEmpty();
    }

    @Test
    void tendenciaVentas_conDatos_devuelveListaOrdenadaPorPeriodo() {
        Object[] row1 = new Object[]{"2026-01-01", 5L, new BigDecimal("500.00")};
        Object[] row2 = new Object[]{"2026-01-02", 3L, new BigDecimal("300.00")};
        when(ventaRepository.findTendenciaDiaria(eq(TENANT), any(), any()))
                .thenReturn(List.<Object[]>of(row1, row2));

        List<VentaTendenciaDTO> resultado = reportesService.tendenciaVentas(TENANT, DESDE, HASTA, "DIA");

        assertThat(resultado).hasSize(2);
        assertThat(resultado.get(0).getPeriodo()).isEqualTo("2026-01-01");
        assertThat(resultado.get(0).getVentasCount()).isEqualTo(5L);
        assertThat(resultado.get(0).getIngresosTotal()).isEqualByComparingTo("500.00");
        assertThat(resultado.get(1).getPeriodo()).isEqualTo("2026-01-02");
    }

    @Test
    void tendenciaVentas_agrupacionMes_llamaQueryMensual() {
        Object[] row = new Object[]{"2026-01", 10L, new BigDecimal("1000.00")};
        when(ventaRepository.findTendenciaMensual(eq(TENANT), any(), any()))
                .thenReturn(List.<Object[]>of(row));

        List<VentaTendenciaDTO> resultado = reportesService.tendenciaVentas(TENANT, DESDE, HASTA, "MES");

        assertThat(resultado).hasSize(1);
        assertThat(resultado.get(0).getPeriodo()).isEqualTo("2026-01");
        assertThat(resultado.get(0).getIngresosTotal()).isEqualByComparingTo("1000.00");
    }

    @Test
    void slowMovers_sinProductos_devuelveListaVacia() {
        when(productoRepository.findSlowMovers(eq(TENANT), any(LocalDateTime.class)))
                .thenReturn(Collections.emptyList());

        List<SlowMoverDTO> resultado = reportesService.slowMovers(TENANT, 30, 50);

        assertThat(resultado).isNotNull().isEmpty();
    }

    @Test
    void slowMovers_conProductos_devuelveListaConCostoTotal() {
        Producto p1 = Producto.builder()
                .id(10L)
                .nombre("Producto Lento")
                .stockActual(100)
                .costoUnitario(new BigDecimal("15.50"))
                .tenantId(TENANT)
                .activo(true)
                .build();
        Producto p2 = Producto.builder()
                .id(11L)
                .nombre("Otro Lento")
                .stockActual(50)
                .costoUnitario(new BigDecimal("20.00"))
                .tenantId(TENANT)
                .activo(true)
                .build();

        when(productoRepository.findSlowMovers(eq(TENANT), any(LocalDateTime.class)))
                .thenReturn(List.of(p1, p2));

        List<SlowMoverDTO> resultado = reportesService.slowMovers(TENANT, 30, 50);

        assertThat(resultado).hasSize(2);

        SlowMoverDTO dto1 = resultado.get(0);
        assertThat(dto1.getProductoId()).isEqualTo(10L);
        assertThat(dto1.getNombre()).isEqualTo("Producto Lento");
        assertThat(dto1.getStockActual()).isEqualTo(100);
        assertThat(dto1.getCostoUnitario()).isEqualByComparingTo("15.50");
        assertThat(dto1.getCostoTotal()).isEqualByComparingTo("1550.00");
        assertThat(dto1.getDiasSinSalida()).isEqualTo(30);
    }

    @Test
    void slowMovers_conLimit_respetaLimite() {
        List<Producto> muchos = java.util.stream.IntStream.range(0, 10)
                .mapToObj(i -> Producto.builder()
                        .id((long) i)
                        .nombre("P" + i)
                        .stockActual(5)
                        .costoUnitario(new BigDecimal("10.00"))
                        .tenantId(TENANT)
                        .activo(true)
                        .build())
                .collect(java.util.stream.Collectors.toList());

        when(productoRepository.findSlowMovers(eq(TENANT), any(LocalDateTime.class)))
                .thenReturn(muchos);

        List<SlowMoverDTO> resultado = reportesService.slowMovers(TENANT, 30, 3);

        assertThat(resultado).hasSize(3);
    }

    @Test
    void ventasPorVendedor_sinDatos_devuelveListaVacia() {
        when(ventaRepository.findVentasPorVendedor(eq(TENANT), any(), any()))
                .thenReturn(Collections.emptyList());

        List<VentaVendedorDTO> resultado = reportesService.ventasPorVendedor(TENANT, DESDE, HASTA, 20);

        assertThat(resultado).isNotNull().isEmpty();
    }

    @Test
    void ventasPorVendedor_conDatos_calculaTicketPromedio() {
        Object[] row = new Object[]{1L, "Juan Pérez", 4L, new BigDecimal("400.00")};
        when(ventaRepository.findVentasPorVendedor(eq(TENANT), any(), any()))
                .thenReturn(List.<Object[]>of(row));

        List<VentaVendedorDTO> resultado = reportesService.ventasPorVendedor(TENANT, DESDE, HASTA, 20);

        assertThat(resultado).hasSize(1);
        VentaVendedorDTO dto = resultado.get(0);
        assertThat(dto.getVendedorId()).isEqualTo(1L);
        assertThat(dto.getVendedorNombre()).isEqualTo("Juan Pérez");
        assertThat(dto.getVentasCount()).isEqualTo(4L);
        assertThat(dto.getIngresosTotal()).isEqualByComparingTo("400.00");
        assertThat(dto.getTicketPromedio()).isEqualByComparingTo("100.00");
    }

    @Test
    void clasificacionAbc_sinDatos_devuelveListaVacia() {
        when(ventaRepository.findTopProductosVendidosPorIngresos(eq(TENANT), any(), any()))
                .thenReturn(Collections.emptyList());

        List<ProductoAbcDTO> resultado = reportesService.clasificacionAbc(TENANT, DESDE, HASTA, 200);

        assertThat(resultado).isNotNull().isEmpty();
    }

    @Test
    void clasificacionAbc_conProductos_asignaClasificacionCorrectamente() {
        // 3 productos: P1=800, P2=150, P3=50 → total=1000
        // P1 pct=80% → acumulado=80% → A (<=80 → A)
        // P2 pct=15% → acumulado=95% → B (<=95 → B)
        // P3 pct=5%  → acumulado=100% → C
        Object[] p1 = new Object[]{1L, "Producto A", 10L, new BigDecimal("800.00")};
        Object[] p2 = new Object[]{2L, "Producto B", 5L, new BigDecimal("150.00")};
        Object[] p3 = new Object[]{3L, "Producto C", 2L, new BigDecimal("50.00")};

        when(ventaRepository.findTopProductosVendidosPorIngresos(eq(TENANT), any(), any()))
                .thenReturn(List.<Object[]>of(p1, p2, p3));

        List<ProductoAbcDTO> resultado = reportesService.clasificacionAbc(TENANT, DESDE, HASTA, 200);

        assertThat(resultado).hasSize(3);

        assertThat(resultado.get(0).getClasificacion()).isEqualTo("A");
        assertThat(resultado.get(0).getProductoId()).isEqualTo(1L);
        assertThat(resultado.get(0).getPorcentaje()).isEqualByComparingTo("80.00");

        assertThat(resultado.get(1).getClasificacion()).isEqualTo("B");
        assertThat(resultado.get(1).getPorcentaje()).isEqualByComparingTo("15.00");

        assertThat(resultado.get(2).getClasificacion()).isEqualTo("C");
    }

    @Test
    void clasificacionAbc_respetaLimit() {
        Object[] p1 = new Object[]{1L, "P1", 100L, new BigDecimal("500.00")};
        Object[] p2 = new Object[]{2L, "P2", 50L, new BigDecimal("300.00")};
        Object[] p3 = new Object[]{3L, "P3", 10L, new BigDecimal("200.00")};

        when(ventaRepository.findTopProductosVendidosPorIngresos(eq(TENANT), any(), any()))
                .thenReturn(List.<Object[]>of(p1, p2, p3));

        List<ProductoAbcDTO> resultado = reportesService.clasificacionAbc(TENANT, DESDE, HASTA, 2);

        assertThat(resultado).hasSize(2);
    }

    @Test
    void ventasPorMetodoPago_sinDatos_devuelveListaVacia() {
        when(ventaRepository.findVentasPorMetodoPago(eq(TENANT), any(), any()))
                .thenReturn(Collections.emptyList());

        List<VentaMetodoPagoDTO> resultado = reportesService.ventasPorMetodoPago(TENANT, DESDE, HASTA);

        assertThat(resultado).isNotNull().isEmpty();
    }

    @Test
    void ventasPorMetodoPago_conDatos_calculaPorcentaje() {
        Object[] efectivo = new Object[]{"EFECTIVO", 6L, new BigDecimal("600.00")};
        Object[] tarjeta  = new Object[]{"TARJETA", 4L, new BigDecimal("400.00")};

        when(ventaRepository.findVentasPorMetodoPago(eq(TENANT), any(), any()))
                .thenReturn(List.<Object[]>of(efectivo, tarjeta));

        List<VentaMetodoPagoDTO> resultado = reportesService.ventasPorMetodoPago(TENANT, DESDE, HASTA);

        assertThat(resultado).hasSize(2);
        assertThat(resultado.get(0).getMetodoPago()).isEqualTo("EFECTIVO");
        assertThat(resultado.get(0).getPorcentaje()).isEqualByComparingTo("60.00");
        assertThat(resultado.get(1).getMetodoPago()).isEqualTo("TARJETA");
        assertThat(resultado.get(1).getPorcentaje()).isEqualByComparingTo("40.00");
    }

    @Test
    void comprasPorProveedor_sinDatos_devuelveListaVacia() {
        when(recepcionRepository.findComprasPorProveedor(eq(TENANT), any(), any()))
                .thenReturn(Collections.emptyList());

        List<CompraProveedorDTO> resultado = reportesService.comprasPorProveedor(TENANT, DESDE, HASTA, 20);

        assertThat(resultado).isNotNull().isEmpty();
    }

    @Test
    void comprasPorProveedor_conDatos_devuelveDatosCorrectamente() {
        Object[] row = new Object[]{5L, "Proveedor Uno", 3L, 120L, new BigDecimal("2400.00")};
        when(recepcionRepository.findComprasPorProveedor(eq(TENANT), any(), any()))
                .thenReturn(List.<Object[]>of(row));

        List<CompraProveedorDTO> resultado = reportesService.comprasPorProveedor(TENANT, DESDE, HASTA, 20);

        assertThat(resultado).hasSize(1);
        CompraProveedorDTO dto = resultado.get(0);
        assertThat(dto.getProveedorId()).isEqualTo(5L);
        assertThat(dto.getProveedorNombre()).isEqualTo("Proveedor Uno");
        assertThat(dto.getRecepcionesCount()).isEqualTo(3L);
        assertThat(dto.getUnidadesRecibidas()).isEqualTo(120L);
        assertThat(dto.getMontoEstimado()).isEqualByComparingTo("2400.00");
    }
}

