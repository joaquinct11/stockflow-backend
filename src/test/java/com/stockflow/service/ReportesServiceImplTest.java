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

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.util.Collections;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
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
}
