package com.stockflow.integration;

import com.stockflow.entity.StockLote;
import com.stockflow.repository.MovimientoInventarioRepository;
import com.stockflow.repository.StockLoteRepository;
import com.stockflow.service.StockLoteService;
import org.junit.jupiter.api.*;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.ActiveProfiles;
import org.testcontainers.containers.PostgreSQLContainer;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Pruebas de integración: lotes con y sin fecha de vencimiento.
 *
 * FT-1:  Crear entrada con lote y fecha de vencimiento.
 * FT-2:  Crear entrada con lote sin fecha de vencimiento.
 * FT-3:  Consultar ambos lotes del tenant.
 * FT-4:  Consumir stock de un lote sin fecha (directo).
 * FT-5:  Consumir stock de lotes con fecha respetando FEFO.
 * FT-6:  Registrar y restaurar lote (devolución).
 * FT-7:  diasRestantes es null cuando fechaVencimiento es null.
 * FT-8:  Lotes sin fecha NO aparecen en findEntradasConVencimientoPorTenant.
 * FT-9:  Aislamiento entre tenants.
 * FT-10: Aislamiento entre sucursales.
 * FT-11: Lotes farmacéuticos con fecha siguen funcionando (FEFO respetado).
 */
@SpringBootTest
@ActiveProfiles("test")
@TestInstance(TestInstance.Lifecycle.PER_CLASS)
@TestMethodOrder(MethodOrderer.OrderAnnotation.class)
class StockLoteSinFechaIntegrationTest {

    @ServiceConnection
    static PostgreSQLContainer<?> postgres = new PostgreSQLContainer<>("postgres:16");

    static {
        postgres.start();
    }

    @Autowired private StockLoteRepository stockLoteRepository;
    @Autowired private MovimientoInventarioRepository movimientoRepository;
    @Autowired private StockLoteService stockLoteService;
    @Autowired private JdbcTemplate jdbc;

    private static final String TENANT_ID   = "slsf-test-001";
    private static final String TENANT_OTRO = "slsf-test-002";

    // IDs dinámicos asignados en setup
    private Long productoId1;
    private Long productoId2;
    private Long sucursalA;
    private Long sucursalB;
    private Long movConFechaId;
    private Long movSinFechaId;
    private Long movOtroTenantId;
    private Long movSucursalBId;
    private Long movFarmaId1;
    private Long movFarmaId2;

    @BeforeAll
    void setup() {
        // Tenants
        jdbc.update("INSERT INTO tenants (tenant_id, nombre, activo, rubro) VALUES (?, 'Ferretería Test', true, 'FERRETERIA') ON CONFLICT (tenant_id) DO NOTHING", TENANT_ID);
        jdbc.update("INSERT INTO tenants (tenant_id, nombre, activo, rubro) VALUES (?, 'Otro Tenant Test', true, 'FERRETERIA') ON CONFLICT (tenant_id) DO NOTHING", TENANT_OTRO);

        // Limpiar datos previos de estos tenants
        jdbc.update("DELETE FROM stock_lotes WHERE tenant_id IN (?, ?)", TENANT_ID, TENANT_OTRO);
        jdbc.update("DELETE FROM movimientos_inventario WHERE tenant_id IN (?, ?)", TENANT_ID, TENANT_OTRO);
        jdbc.update("DELETE FROM productos WHERE tenant_id IN (?, ?)", TENANT_ID, TENANT_OTRO);
        jdbc.update("DELETE FROM sucursales WHERE tenant_id IN (?, ?)", TENANT_ID, TENANT_OTRO);

        // Sucursales con IDs dinámicos
        sucursalA = jdbc.queryForObject(
                "INSERT INTO sucursales (nombre, tenant_id, es_principal, activo) VALUES ('Sucursal A', ?, true, true) RETURNING id",
                Long.class, TENANT_ID);
        sucursalB = jdbc.queryForObject(
                "INSERT INTO sucursales (nombre, tenant_id, es_principal, activo) VALUES ('Sucursal B', ?, false, true) RETURNING id",
                Long.class, TENANT_ID);

        // Unidad de medida global
        Long umId = jdbc.queryForObject(
                "SELECT id FROM unidad_medida WHERE tenant_id IS NULL LIMIT 1", Long.class);

        // Producto 1: ferretería (sin fecha de vencimiento)
        productoId1 = jdbc.queryForObject(
                "INSERT INTO productos (nombre, codigo_barras, precio_venta, costo_unitario, stock_actual, stock_minimo, activo, tenant_id, unidad_medida_id) VALUES ('Perno 3/4', 'SLSF-FER001', 10.00, 5.00, 100, 1, true, ?, ?) RETURNING id",
                Long.class, TENANT_ID, umId);

        // Producto 2: farmacia (con fecha de vencimiento)
        productoId2 = jdbc.queryForObject(
                "INSERT INTO productos (nombre, codigo_barras, precio_venta, costo_unitario, stock_actual, stock_minimo, activo, tenant_id, unidad_medida_id) VALUES ('Paracetamol 500mg', 'SLSF-FARM001', 15.00, 8.00, 50, 1, true, ?, ?) RETURNING id",
                Long.class, TENANT_ID, umId);

        // Movimiento ENTRADA con lote y fecha (caso farmacia)
        movConFechaId = jdbc.queryForObject(
                "INSERT INTO movimientos_inventario (producto_id, tipo, cantidad, tenant_id, lote, fecha_vencimiento, sucursal_id) VALUES (?, 'ENTRADA', 20, ?, 'LOTE-FEC-01', ?, ?) RETURNING id",
                Long.class, productoId1, TENANT_ID, LocalDate.now().plusMonths(6), sucursalA);

        // Movimiento ENTRADA con lote sin fecha (caso ferretería)
        movSinFechaId = jdbc.queryForObject(
                "INSERT INTO movimientos_inventario (producto_id, tipo, cantidad, tenant_id, lote, sucursal_id) VALUES (?, 'ENTRADA', 50, ?, 'LOTE-NOFEC-01', ?) RETURNING id",
                Long.class, productoId1, TENANT_ID, sucursalA);

        // Movimiento de otro tenant para prueba de aislamiento
        movOtroTenantId = jdbc.queryForObject(
                "INSERT INTO movimientos_inventario (producto_id, tipo, cantidad, tenant_id, lote, sucursal_id) VALUES (?, 'ENTRADA', 30, ?, 'LOTE-OTRO', ?) RETURNING id",
                Long.class, productoId1, TENANT_OTRO, sucursalA);

        // Movimiento de sucursal B para prueba de aislamiento
        movSucursalBId = jdbc.queryForObject(
                "INSERT INTO movimientos_inventario (producto_id, tipo, cantidad, tenant_id, lote, sucursal_id) VALUES (?, 'ENTRADA', 10, ?, 'LOTE-SUCB-01', ?) RETURNING id",
                Long.class, productoId1, TENANT_ID, sucursalB);

        // Movimientos farmacia con fechas distintas para FEFO
        movFarmaId1 = jdbc.queryForObject(
                "INSERT INTO movimientos_inventario (producto_id, tipo, cantidad, tenant_id, lote, fecha_vencimiento, sucursal_id) VALUES (?, 'ENTRADA', 10, ?, 'FARM-LOTE-01', ?, ?) RETURNING id",
                Long.class, productoId2, TENANT_ID, LocalDate.now().plusMonths(1), sucursalA);
        movFarmaId2 = jdbc.queryForObject(
                "INSERT INTO movimientos_inventario (producto_id, tipo, cantidad, tenant_id, lote, fecha_vencimiento, sucursal_id) VALUES (?, 'ENTRADA', 10, ?, 'FARM-LOTE-02', ?, ?) RETURNING id",
                Long.class, productoId2, TENANT_ID, LocalDate.now().plusMonths(3), sucursalA);

        // Registrar StockLotes via servicio
        stockLoteService.registrarLote(TENANT_ID, movConFechaId, productoId1, sucursalA,
                "LOTE-FEC-01", LocalDate.now().plusMonths(6), 20, null, BigDecimal.TEN, BigDecimal.valueOf(5));
        stockLoteService.registrarLote(TENANT_ID, movSinFechaId, productoId1, sucursalA,
                "LOTE-NOFEC-01", null, 50, null, BigDecimal.TEN, BigDecimal.valueOf(5));
        stockLoteService.registrarLote(TENANT_OTRO, movOtroTenantId, productoId1, sucursalA,
                "LOTE-OTRO", null, 30, null, BigDecimal.TEN, BigDecimal.valueOf(5));
        stockLoteService.registrarLote(TENANT_ID, movSucursalBId, productoId1, sucursalB,
                "LOTE-SUCB-01", null, 10, null, BigDecimal.TEN, BigDecimal.valueOf(5));
        stockLoteService.registrarLote(TENANT_ID, movFarmaId1, productoId2, sucursalA,
                "FARM-LOTE-01", LocalDate.now().plusMonths(1), 10, null, BigDecimal.valueOf(15), BigDecimal.valueOf(8));
        stockLoteService.registrarLote(TENANT_ID, movFarmaId2, productoId2, sucursalA,
                "FARM-LOTE-02", LocalDate.now().plusMonths(3), 10, null, BigDecimal.valueOf(15), BigDecimal.valueOf(8));
    }

    @AfterAll
    void tearDown() {
        jdbc.update("DELETE FROM stock_lotes WHERE tenant_id IN (?, ?)", TENANT_ID, TENANT_OTRO);
        jdbc.update("DELETE FROM movimientos_inventario WHERE tenant_id IN (?, ?)", TENANT_ID, TENANT_OTRO);
        jdbc.update("DELETE FROM productos WHERE tenant_id IN (?, ?)", TENANT_ID, TENANT_OTRO);
        jdbc.update("DELETE FROM sucursales WHERE tenant_id IN (?, ?)", TENANT_ID, TENANT_OTRO);
        jdbc.update("DELETE FROM tenants WHERE tenant_id IN (?, ?)", TENANT_ID, TENANT_OTRO);
    }

    @Test @Order(1)
    void ft1_loteConFechaEsRegistradoCorrectamente() {
        StockLote lote = stockLoteRepository.findByMovimientoId(movConFechaId).orElseThrow();
        assertThat(lote.getFechaVencimiento()).isNotNull();
        assertThat(lote.getLote()).isEqualTo("LOTE-FEC-01");
        assertThat(lote.getStockActual()).isEqualTo(20);
    }

    @Test @Order(2)
    void ft2_loteSinFechaEsRegistradoSinExcepcion() {
        StockLote lote = stockLoteRepository.findByMovimientoId(movSinFechaId).orElseThrow();
        assertThat(lote.getFechaVencimiento()).isNull();
        assertThat(lote.getLote()).isEqualTo("LOTE-NOFEC-01");
        assertThat(lote.getStockActual()).isEqualTo(50);
    }

    @Test @Order(3)
    void ft3_ambosLotesAparecenEnConsultaPorTenant() {
        var movimientos = movimientoRepository.findEntradasConLotePorTenant(TENANT_ID);
        var lotes = movimientos.stream().map(m -> m.getLote()).toList();
        assertThat(lotes).contains("LOTE-FEC-01", "LOTE-NOFEC-01");
        // Lote con fecha aparece ANTES que el sin fecha (NULLS LAST)
        int idxConFecha = lotes.indexOf("LOTE-FEC-01");
        int idxSinFecha = lotes.indexOf("LOTE-NOFEC-01");
        assertThat(idxConFecha).isLessThan(idxSinFecha);
    }

    @Test @Order(4)
    void ft4_consumirStockDeLoteSinFecha() {
        StockLote lote = stockLoteRepository.findByMovimientoId(movSinFechaId).orElseThrow();
        int stockAntes = lote.getStockActual();

        stockLoteService.descontarLoteEspecifico(lote.getId(), 5);

        StockLote actualizado = stockLoteRepository.findById(lote.getId()).orElseThrow();
        assertThat(actualizado.getStockActual()).isEqualTo(stockAntes - 5);
    }

    @Test @Order(5)
    void ft5_fefoConsumePrimeroLoteConFechaProxima() {
        // FEFO: FARM-LOTE-01 (1 mes) debe consumirse antes que FARM-LOTE-02 (3 meses)
        int stockL1Antes = stockLoteRepository.findByMovimientoId(movFarmaId1).orElseThrow().getStockActual();
        int stockL2Antes = stockLoteRepository.findByMovimientoId(movFarmaId2).orElseThrow().getStockActual();

        stockLoteService.descontarFefo(TENANT_ID, productoId2, sucursalA, 7);

        int stockL1Despues = stockLoteRepository.findByMovimientoId(movFarmaId1).orElseThrow().getStockActual();
        int stockL2Despues = stockLoteRepository.findByMovimientoId(movFarmaId2).orElseThrow().getStockActual();

        assertThat(stockL1Despues).isLessThan(stockL1Antes);
        assertThat(stockL2Despues).isEqualTo(stockL2Antes);
    }

    @Test @Order(6)
    void ft6_restaurarLoteConservaDatos() {
        StockLote lote = stockLoteRepository.findByMovimientoId(movSinFechaId).orElseThrow();
        int stockAntes = lote.getStockActual();

        stockLoteService.restaurarLoteEspecifico(lote.getId(), 3);

        StockLote actualizado = stockLoteRepository.findById(lote.getId()).orElseThrow();
        assertThat(actualizado.getStockActual()).isEqualTo(stockAntes + 3);
        assertThat(actualizado.getFechaVencimiento()).isNull();
    }

    @Test @Order(7)
    void ft7_diasRestantesEsNullCuandoFechaEsNull() {
        StockLote lote = stockLoteRepository.findByMovimientoId(movSinFechaId).orElseThrow();
        assertThat(lote.getFechaVencimiento()).isNull();

        List<com.stockflow.dto.StockLoteDisponibleDTO> disponibles =
                stockLoteService.getLotesDisponibles(TENANT_ID, productoId1, sucursalA);

        disponibles.stream()
                .filter(d -> "LOTE-NOFEC-01".equals(d.getLote()))
                .forEach(d -> assertThat(d.getDiasParaVencer()).isNull());
    }

    @Test @Order(8)
    void ft8_loteSinFechaNoApareceEnConsultaConVencimiento() {
        var movimientos = movimientoRepository.findEntradasConVencimientoPorTenant(TENANT_ID);
        var lotes = movimientos.stream().map(m -> m.getLote()).toList();
        assertThat(lotes).doesNotContain("LOTE-NOFEC-01");
        assertThat(lotes).contains("LOTE-FEC-01");
    }

    @Test @Order(9)
    void ft9_aislamientoEntreTenantsEnConsulta() {
        var movTenant1 = movimientoRepository.findEntradasConLotePorTenant(TENANT_ID);
        var movTenant2 = movimientoRepository.findEntradasConLotePorTenant(TENANT_OTRO);

        var lotesTenant1 = movTenant1.stream().map(m -> m.getLote()).toList();
        var lotesTenant2 = movTenant2.stream().map(m -> m.getLote()).toList();

        assertThat(lotesTenant1).doesNotContain("LOTE-OTRO");
        assertThat(lotesTenant2).doesNotContain("LOTE-FEC-01", "LOTE-NOFEC-01");
        assertThat(lotesTenant2).contains("LOTE-OTRO");
    }

    @Test @Order(10)
    void ft10_aislamientoEntreSucursalesEnFefo() {
        StockLote loteSucB = stockLoteRepository.findByMovimientoId(movSucursalBId).orElseThrow();
        int stockBAntes = loteSucB.getStockActual();

        stockLoteService.descontarFefo(TENANT_ID, productoId1, sucursalA, 5);

        int stockBDespues = stockLoteRepository.findById(loteSucB.getId()).orElseThrow().getStockActual();
        assertThat(stockBDespues).isEqualTo(stockBAntes);
    }

    @Test @Order(11)
    void ft11_lotesConFechaExistentesSiguenFuncionando() {
        StockLote lote = stockLoteRepository.findByMovimientoId(movConFechaId).orElseThrow();
        assertThat(lote.getFechaVencimiento()).isNotNull().isAfter(LocalDate.now());
        assertThat(lote.getStockActual()).isPositive();

        List<com.stockflow.dto.StockLoteDisponibleDTO> disponibles =
                stockLoteService.getLotesDisponibles(TENANT_ID, productoId1, sucursalA);

        boolean tieneConFecha = disponibles.stream().anyMatch(d -> "LOTE-FEC-01".equals(d.getLote()));
        assertThat(tieneConFecha).isTrue();

        disponibles.stream()
                .filter(d -> "LOTE-FEC-01".equals(d.getLote()))
                .forEach(d -> assertThat(d.getDiasParaVencer()).isNotNull().isPositive());
    }
}
