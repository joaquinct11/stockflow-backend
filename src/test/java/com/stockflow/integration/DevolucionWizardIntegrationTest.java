package com.stockflow.integration;

import com.stockflow.dto.CrearDevolucionDTO;
import com.stockflow.dto.DevolucionDetalleItemDTO;
import com.stockflow.repository.DetalleVentaRepository;
import com.stockflow.repository.DevolucionRepository;
import com.stockflow.repository.ProductoRepository;
import com.stockflow.repository.StockLoteRepository;
import com.stockflow.repository.VentaRepository;
import com.stockflow.service.DevolucionService;
import com.stockflow.service.StockLoteService;
import com.stockflow.util.TenantContext;
import org.junit.jupiter.api.*;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.transaction.support.TransactionTemplate;
import org.testcontainers.containers.PostgreSQLContainer;

import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.*;

/**
 * F-13 Phase 4 — Pruebas de integración para el wizard de devoluciones.
 * Cubre: búsqueda de ventas, estado-devolucion, anti-sobredevolución,
 * consistencia multi-tenant, atomicidad de incrementos.
 * PostgreSQL 16 real (Testcontainers). Sin mocks.
 */
@SpringBootTest
@ActiveProfiles("test")
@TestInstance(TestInstance.Lifecycle.PER_CLASS)
@TestMethodOrder(MethodOrderer.OrderAnnotation.class)
class DevolucionWizardIntegrationTest {

    @ServiceConnection
    static PostgreSQLContainer<?> postgres = new PostgreSQLContainer<>("postgres:16");

    static {
        postgres.start();
    }

    @Autowired private VentaRepository ventaRepository;
    @Autowired private DevolucionRepository devolucionRepository;
    @Autowired private DetalleVentaRepository detalleVentaRepository;
    @Autowired private ProductoRepository productoRepository;
    @Autowired private StockLoteRepository stockLoteRepository;
    @Autowired private StockLoteService stockLoteService;
    @Autowired private DevolucionService devolucionService;
    @Autowired private JdbcTemplate jdbc;
    @Autowired private TransactionTemplate txTemplate;

    private static final String TENANT_A = "dw-f13-a";
    private static final String TENANT_B = "dw-f13-b";

    // IDs reutilizados por los tests
    private Long productoId;
    private Long productoId2;
    private Long clienteId;
    private Long usuarioId;
    private Long ventaId;
    private Long ventaId2; // venta multi-producto
    private Long ventaIdTenantB;

    @BeforeAll
    void setup() {
        // Tenants
        jdbc.execute("INSERT INTO tenants (tenant_id, nombre, activo, rubro) VALUES ('" + TENANT_A + "', 'DW Test A', true, 'OTRO') ON CONFLICT (tenant_id) DO NOTHING");
        jdbc.execute("INSERT INTO tenants (tenant_id, nombre, activo, rubro) VALUES ('" + TENANT_B + "', 'DW Test B', true, 'OTRO') ON CONFLICT (tenant_id) DO NOTHING");

        Long umId = jdbc.queryForObject("SELECT id FROM unidad_medida WHERE tenant_id IS NULL LIMIT 1", Long.class);

        // Productos
        productoId = jdbc.queryForObject(
                "INSERT INTO productos (nombre, stock_actual, costo_unitario, precio_venta, unidad_medida_id, activo, tenant_id, version) " +
                "VALUES ('ProdWiz-A', 100, 5.00, 10.00, " + umId + ", true, '" + TENANT_A + "', 0) RETURNING id", Long.class);

        productoId2 = jdbc.queryForObject(
                "INSERT INTO productos (nombre, stock_actual, costo_unitario, precio_venta, unidad_medida_id, activo, tenant_id, version) " +
                "VALUES ('ProdWiz-A2', 50, 3.00, 7.00, " + umId + ", true, '" + TENANT_A + "', 0) RETURNING id", Long.class);

        // Cliente con documento
        clienteId = jdbc.queryForObject(
                "INSERT INTO clientes (nombre, tipo_documento, numero_documento, activo, tenant_id) " +
                "VALUES ('Ana Pérez Ruiz', 'DNI', '12345678', true, '" + TENANT_A + "') RETURNING id", Long.class);

        // Usuario para pruebas de DevolucionServiceImpl
        usuarioId = jdbc.queryForObject(
                "INSERT INTO usuarios (email, contraseña, nombre, activo) " +
                "VALUES ('testdev@dw-f13.internal', 'hash', 'Test Devolucion', true) RETURNING id",
                Long.class);

        // Venta A con cliente (10 unidades del productoId)
        ventaId = jdbc.queryForObject(
                "INSERT INTO ventas (total, estado, tenant_id, created_at, cliente_id) " +
                "VALUES (100.00, 'COMPLETADA', '" + TENANT_A + "', NOW(), " + clienteId + ") RETURNING id", Long.class);

        jdbc.execute("INSERT INTO detalles_venta (venta_id, producto_id, cantidad, precio_unitario, subtotal, factor) " +
                "VALUES (" + ventaId + ", " + productoId + ", 10, 10.00, 100.00, 1)");

        // Venta multi-producto (5 de productoId + 3 de productoId2)
        ventaId2 = jdbc.queryForObject(
                "INSERT INTO ventas (total, estado, tenant_id, created_at, cliente_id) " +
                "VALUES (71.00, 'COMPLETADA', '" + TENANT_A + "', NOW(), " + clienteId + ") RETURNING id", Long.class);

        jdbc.execute("INSERT INTO detalles_venta (venta_id, producto_id, cantidad, precio_unitario, subtotal, factor) " +
                "VALUES (" + ventaId2 + ", " + productoId + ", 5, 10.00, 50.00, 1)");
        jdbc.execute("INSERT INTO detalles_venta (venta_id, producto_id, cantidad, precio_unitario, subtotal, factor) " +
                "VALUES (" + ventaId2 + ", " + productoId2 + ", 3, 7.00, 21.00, 1)");

        // Venta tenant B (aislamiento multi-tenant)
        Long prodBId = jdbc.queryForObject(
                "INSERT INTO productos (nombre, stock_actual, costo_unitario, precio_venta, unidad_medida_id, activo, tenant_id, version) " +
                "VALUES ('ProdWiz-B', 50, 2.00, 5.00, " + umId + ", true, '" + TENANT_B + "', 0) RETURNING id", Long.class);

        ventaIdTenantB = jdbc.queryForObject(
                "INSERT INTO ventas (total, estado, tenant_id, created_at) " +
                "VALUES (50.00, 'COMPLETADA', '" + TENANT_B + "', NOW()) RETURNING id", Long.class);

        jdbc.execute("INSERT INTO detalles_venta (venta_id, producto_id, cantidad, precio_unitario, subtotal, factor) " +
                "VALUES (" + ventaIdTenantB + ", " + prodBId + ", 10, 5.00, 50.00, 1)");
    }

    @BeforeEach
    void setTenantA() {
        TenantContext.setCurrentTenant(TENANT_A);
    }

    @AfterEach
    void clearTenant() {
        TenantContext.clear();
    }

    // ── Grupo 1: buscarVentas ─────────────────────────────────────────────────

    @Test
    @Order(1)
    void t01_buscarPorIdExacto_encuentraVenta() {
        List<Object[]> rows = ventaRepository.buscarVentas(TENANT_A, String.valueOf(ventaId));

        assertThat(rows).isNotEmpty();
        Long idEncontrado = ((Number) rows.get(0)[0]).longValue();
        assertThat(idEncontrado).isEqualTo(ventaId);
    }

    @Test
    @Order(2)
    void t02_buscarPorNombreClienteParcial_encuentraVenta() {
        List<Object[]> rows = ventaRepository.buscarVentas(TENANT_A, "Ana Pérez");

        assertThat(rows).isNotEmpty();
        String clienteNombre = (String) rows.get(0)[2];
        assertThat(clienteNombre).containsIgnoringCase("Ana");
    }

    @Test
    @Order(3)
    void t03_buscarPorDocumento_encuentraVenta() {
        List<Object[]> rows = ventaRepository.buscarVentas(TENANT_A, "12345678");

        assertThat(rows).isNotEmpty();
        String documento = (String) rows.get(0)[3];
        assertThat(documento).isEqualTo("12345678");
    }

    @Test
    @Order(4)
    void t04_buscarQueryVacio_devuelveListaVacia() {
        // La query no hace ILIKE sin query real, con texto que no existe devolverá vacío
        List<Object[]> rows = ventaRepository.buscarVentas(TENANT_A, "ZZZ_no_existe_99999");

        assertThat(rows).isEmpty();
    }

    // ── Grupo 2: sumarDevueltoPorProducto ────────────────────────────────────

    @Test
    @Order(5)
    void t05_sinDevolucionesAnteriores_devueltoEsCero() {
        List<Object[]> rows = devolucionRepository.sumarDevueltoPorProducto(ventaId, TENANT_A);

        // Sin ninguna devolución registrada, la lista debe estar vacía
        assertThat(rows).isEmpty();
    }

    @Test
    @Order(6)
    void t06_conDevolucionParcial_cantidadDevueltaCorrecta() {
        // Registrar una devolución de 3 unidades del productoId en ventaId
        Long devId = jdbc.queryForObject(
                "INSERT INTO devoluciones (tenant_id, venta_id, motivo, total_devuelto, reponer_stock, estado, fecha_devolucion, created_at) " +
                "VALUES ('" + TENANT_A + "', " + ventaId + ", 'Test parcial', 30.00, true, 'PROCESADA', NOW(), NOW()) RETURNING id",
                Long.class);
        jdbc.execute("INSERT INTO devolucion_detalle (devolucion_id, producto_id, cantidad_devuelta, precio_unitario, subtotal, factor) " +
                "VALUES (" + devId + ", " + productoId + ", 3, 10.00, 30.00, 1)");

        List<Object[]> rows = devolucionRepository.sumarDevueltoPorProducto(ventaId, TENANT_A);

        assertThat(rows).hasSize(1);
        long prodIdRow = ((Number) rows.get(0)[0]).longValue();
        int sumaDevuelta = ((Number) rows.get(0)[1]).intValue();
        assertThat(prodIdRow).isEqualTo(productoId);
        assertThat(sumaDevuelta).isEqualTo(3);

        // Limpiar
        jdbc.execute("DELETE FROM devolucion_detalle WHERE devolucion_id = " + devId);
        jdbc.execute("DELETE FROM devoluciones WHERE id = " + devId);
    }

    @Test
    @Order(7)
    void t07_conDevolucionTotal_pendienteEsCero() {
        // Devolver las 10 unidades de ventaId
        Long devId = jdbc.queryForObject(
                "INSERT INTO devoluciones (tenant_id, venta_id, motivo, total_devuelto, reponer_stock, estado, fecha_devolucion, created_at) " +
                "VALUES ('" + TENANT_A + "', " + ventaId + ", 'Test total', 100.00, true, 'PROCESADA', NOW(), NOW()) RETURNING id",
                Long.class);
        jdbc.execute("INSERT INTO devolucion_detalle (devolucion_id, producto_id, cantidad_devuelta, precio_unitario, subtotal, factor) " +
                "VALUES (" + devId + ", " + productoId + ", 10, 10.00, 100.00, 1)");

        List<Object[]> rows = devolucionRepository.sumarDevueltoPorProducto(ventaId, TENANT_A);
        assertThat(rows).hasSize(1);
        int sumaDevuelta = ((Number) rows.get(0)[1]).intValue();

        // Verificar que cantidadPendiente = vendido - devuelto = 10 - 10 = 0
        List<com.stockflow.entity.DetalleVenta> detalles = detalleVentaRepository.findByVentaId(ventaId);
        int vendido = detalles.stream().mapToInt(com.stockflow.entity.DetalleVenta::getCantidad).sum();
        assertThat(sumaDevuelta).isEqualTo(vendido); // todo devuelto
        assertThat(vendido - sumaDevuelta).isEqualTo(0); // pendiente = 0

        // Limpiar
        jdbc.execute("DELETE FROM devolucion_detalle WHERE devolucion_id = " + devId);
        jdbc.execute("DELETE FROM devoluciones WHERE id = " + devId);
    }

    // ── Grupo 3: DetalleVentaRepository ──────────────────────────────────────

    @Test
    @Order(8)
    void t08_findByVentaId_devuelveDetallesCorrectos() {
        List<com.stockflow.entity.DetalleVenta> detalles = detalleVentaRepository.findByVentaId(ventaId);

        assertThat(detalles).hasSize(1);
        assertThat(detalles.get(0).getCantidad()).isEqualTo(10);
        assertThat(detalles.get(0).getProducto().getId()).isEqualTo(productoId);
    }

    @Test
    @Order(9)
    void t09_ventaMultiProducto_detallesCompletos() {
        List<com.stockflow.entity.DetalleVenta> detalles = detalleVentaRepository.findByVentaId(ventaId2);

        assertThat(detalles).hasSize(2);
        int totalUnidades = detalles.stream().mapToInt(com.stockflow.entity.DetalleVenta::getCantidad).sum();
        assertThat(totalUnidades).isEqualTo(8); // 5 + 3
    }

    // ── Grupo 4: Anti-sobredevolución ─────────────────────────────────────────

    @Test
    @Order(10)
    void t10_dosDevolucionesParciales_sumanCorrectamente() {
        // Primera devolución: 4 unidades
        Long dev1Id = jdbc.queryForObject(
                "INSERT INTO devoluciones (tenant_id, venta_id, motivo, total_devuelto, reponer_stock, estado, fecha_devolucion, created_at) " +
                "VALUES ('" + TENANT_A + "', " + ventaId + ", 'Primera', 40.00, true, 'PROCESADA', NOW(), NOW()) RETURNING id", Long.class);
        jdbc.execute("INSERT INTO devolucion_detalle (devolucion_id, producto_id, cantidad_devuelta, precio_unitario, subtotal, factor) " +
                "VALUES (" + dev1Id + ", " + productoId + ", 4, 10.00, 40.00, 1)");

        // Segunda devolución: 3 unidades
        Long dev2Id = jdbc.queryForObject(
                "INSERT INTO devoluciones (tenant_id, venta_id, motivo, total_devuelto, reponer_stock, estado, fecha_devolucion, created_at) " +
                "VALUES ('" + TENANT_A + "', " + ventaId + ", 'Segunda', 30.00, true, 'PROCESADA', NOW(), NOW()) RETURNING id", Long.class);
        jdbc.execute("INSERT INTO devolucion_detalle (devolucion_id, producto_id, cantidad_devuelta, precio_unitario, subtotal, factor) " +
                "VALUES (" + dev2Id + ", " + productoId + ", 3, 10.00, 30.00, 1)");

        List<Object[]> rows = devolucionRepository.sumarDevueltoPorProducto(ventaId, TENANT_A);
        assertThat(rows).hasSize(1);
        int totalDevuelto = ((Number) rows.get(0)[1]).intValue();
        assertThat(totalDevuelto).isEqualTo(7); // 4 + 3

        // Pendiente = 10 - 7 = 3 (el wizard mostraría máximo 3 para devolver)
        List<com.stockflow.entity.DetalleVenta> detalles = detalleVentaRepository.findByVentaId(ventaId);
        int vendido = detalles.get(0).getCantidad();
        assertThat(vendido - totalDevuelto).isEqualTo(3);

        // Limpiar
        jdbc.execute("DELETE FROM devolucion_detalle WHERE devolucion_id IN (" + dev1Id + "," + dev2Id + ")");
        jdbc.execute("DELETE FROM devoluciones WHERE id IN (" + dev1Id + "," + dev2Id + ")");
    }

    @Test
    @Order(11)
    void t11_devolucionConFactor_unidadesBaseCorretas() {
        // Factor = 10: devolver 1 "caja" = 10 unidades base
        Long devId = jdbc.queryForObject(
                "INSERT INTO devoluciones (tenant_id, venta_id, motivo, total_devuelto, reponer_stock, estado, fecha_devolucion, created_at) " +
                "VALUES ('" + TENANT_A + "', " + ventaId + ", 'Factor test', 10.00, true, 'PROCESADA', NOW(), NOW()) RETURNING id", Long.class);
        jdbc.execute("INSERT INTO devolucion_detalle (devolucion_id, producto_id, cantidad_devuelta, precio_unitario, subtotal, factor) " +
                "VALUES (" + devId + ", " + productoId + ", 1, 10.00, 10.00, 10)"); // 1 caja × factor 10

        List<Object[]> rows = devolucionRepository.sumarDevueltoPorProducto(ventaId, TENANT_A);
        assertThat(rows).hasSize(1);
        int sumaUnidadesBase = ((Number) rows.get(0)[1]).intValue(); // SUM(cantidad * factor)
        assertThat(sumaUnidadesBase).isEqualTo(10); // 1 caja × 10

        // Limpiar
        jdbc.execute("DELETE FROM devolucion_detalle WHERE devolucion_id = " + devId);
        jdbc.execute("DELETE FROM devoluciones WHERE id = " + devId);
    }

    // ── Grupo 5: Aislamiento multi-tenant ─────────────────────────────────────

    @Test
    @Order(12)
    void t12_buscarVentas_noRetornaVentasDeOtroTenant() {
        // Búsqueda en TENANT_A no debe devolver la venta de TENANT_B
        List<Object[]> rows = ventaRepository.buscarVentas(TENANT_A, String.valueOf(ventaIdTenantB));

        assertThat(rows).isEmpty();
    }

    @Test
    @Order(13)
    void t13_sumarDevuelto_noContaDevollucionesDeOtroTenant() {
        // Registrar devolución en TENANT_B para la venta de TENANT_B
        Long devBId = jdbc.queryForObject(
                "INSERT INTO devoluciones (tenant_id, venta_id, motivo, total_devuelto, reponer_stock, estado, fecha_devolucion, created_at) " +
                "VALUES ('" + TENANT_B + "', " + ventaIdTenantB + ", 'Tenant B dev', 50.00, true, 'PROCESADA', NOW(), NOW()) RETURNING id", Long.class);

        // Buscar detalles de ventaIdTenantB con producto de Tenant B
        Long prodBId = jdbc.queryForObject(
                "SELECT producto_id FROM detalles_venta WHERE venta_id = " + ventaIdTenantB + " LIMIT 1", Long.class);

        jdbc.execute("INSERT INTO devolucion_detalle (devolucion_id, producto_id, cantidad_devuelta, precio_unitario, subtotal, factor) " +
                "VALUES (" + devBId + ", " + prodBId + ", 5, 5.00, 25.00, 1)");

        // Desde TENANT_A, buscar para ventaId (de A) — NO debe incluir devoluciones de B
        List<Object[]> rowsA = devolucionRepository.sumarDevueltoPorProducto(ventaId, TENANT_A);
        assertThat(rowsA).isEmpty(); // ventaId de A no tiene devoluciones

        // Desde TENANT_B, buscar para ventaIdTenantB — sí debe incluir
        List<Object[]> rowsB = devolucionRepository.sumarDevueltoPorProducto(ventaIdTenantB, TENANT_B);
        assertThat(rowsB).hasSize(1);
        assertThat(((Number) rowsB.get(0)[1]).intValue()).isEqualTo(5);

        // Limpiar
        jdbc.execute("DELETE FROM devolucion_detalle WHERE devolucion_id = " + devBId);
        jdbc.execute("DELETE FROM devoluciones WHERE id = " + devBId);
    }

    // ── Grupo 6: Venta sin cliente ─────────────────────────────────────────────

    @Test
    @Order(14)
    void t14_ventaSinCliente_clienteNombreEsSinCliente() {
        // Crear venta sin cliente
        Long ventaSinClienteId = jdbc.queryForObject(
                "INSERT INTO ventas (total, estado, tenant_id, created_at) " +
                "VALUES (20.00, 'COMPLETADA', '" + TENANT_A + "', NOW()) RETURNING id", Long.class);

        List<Object[]> rows = ventaRepository.buscarVentas(TENANT_A, String.valueOf(ventaSinClienteId));

        assertThat(rows).hasSize(1);
        String clienteNombre = (String) rows.get(0)[2];
        assertThat(clienteNombre).isEqualTo("Sin cliente");

        // Limpiar
        jdbc.execute("DELETE FROM ventas WHERE id = " + ventaSinClienteId);
    }

    // ── Grupo 7: Incremento atómico en devoluciones ───────────────────────────

    @Test
    @Order(15)
    void t15_incrementarStockAtomico_negativoDaDescuento() {
        int stockAntes = jdbc.queryForObject(
                "SELECT stock_actual FROM productos WHERE id = " + productoId, Integer.class);

        // @Modifying requiere transacción activa
        txTemplate.execute(s -> {
            TenantContext.setCurrentTenant(TENANT_A);
            productoRepository.incrementarStockAtomico(productoId, -5, TENANT_A);
            return null;
        });

        int stockDespues = jdbc.queryForObject(
                "SELECT stock_actual FROM productos WHERE id = " + productoId, Integer.class);
        assertThat(stockDespues).isEqualTo(stockAntes - 5);

        // Restaurar
        txTemplate.execute(s -> {
            TenantContext.setCurrentTenant(TENANT_A);
            return productoRepository.incrementarStockAtomico(productoId, 5, TENANT_A);
        });
    }

    @Test
    @Order(16)
    void t16_incrementarStockAtomico_incrementaVersionColumna() {
        Long versionAntes = jdbc.queryForObject(
                "SELECT version FROM productos WHERE id = " + productoId, Long.class);

        txTemplate.execute(s -> {
            TenantContext.setCurrentTenant(TENANT_A);
            return productoRepository.incrementarStockAtomico(productoId, 5, TENANT_A);
        });

        Long versionDespues = jdbc.queryForObject(
                "SELECT version FROM productos WHERE id = " + productoId, Long.class);
        assertThat(versionDespues).isEqualTo(versionAntes + 1);

        // Restaurar stock
        txTemplate.execute(s -> {
            TenantContext.setCurrentTenant(TENANT_A);
            return productoRepository.incrementarStockAtomico(productoId, -5, TENANT_A);
        });
    }

    @Test
    @Order(17)
    void t17_incrementarStockAtomico_tenantIncorrectoNoAfecta() {
        int stockAntes = jdbc.queryForObject(
                "SELECT stock_actual FROM productos WHERE id = " + productoId, Integer.class);

        // Usar TENANT_B para el producto de TENANT_A — no debe cambiar
        Integer rows = txTemplate.execute(s -> {
            TenantContext.setCurrentTenant(TENANT_B);
            return productoRepository.incrementarStockAtomico(productoId, 50, TENANT_B);
        });

        assertThat(rows).isEqualTo(0);
        int stockDespues = jdbc.queryForObject(
                "SELECT stock_actual FROM productos WHERE id = " + productoId, Integer.class);
        assertThat(stockDespues).isEqualTo(stockAntes);
    }

    @Test
    @Order(18)
    void t18_estadoDevolucion_dosProductosEnUnaVenta_ambosListados() {
        // ventaId2 tiene 2 productos → findByVentaId debe retornarlos ambos
        List<com.stockflow.entity.DetalleVenta> detalles = detalleVentaRepository.findByVentaId(ventaId2);

        assertThat(detalles).hasSize(2);

        // Sin devoluciones → sumar devuelto vacío → pendiente = vendido para todos
        List<Object[]> devueltos = devolucionRepository.sumarDevueltoPorProducto(ventaId2, TENANT_A);
        assertThat(devueltos).isEmpty();

        // Calcular estado: pendiente = vendido - 0
        Map<Long, Integer> devueltoPorProducto = new java.util.HashMap<>();
        for (Object[] row : devueltos) {
            devueltoPorProducto.put(((Number) row[0]).longValue(), ((Number) row[1]).intValue());
        }

        for (com.stockflow.entity.DetalleVenta dv : detalles) {
            int yaDevuelto = devueltoPorProducto.getOrDefault(dv.getProducto().getId(), 0);
            int pendiente = dv.getCantidad() - yaDevuelto;
            assertThat(pendiente).isPositive(); // todos tienen pendiente > 0
        }
    }

    // ── Grupo 8: restaurarDesdeJson — fix F-13 lote over-restoration ─────────

    /** Crea un movimiento mínimo en movimientos_inventario y retorna su id. */
    private Long crearMovimiento() {
        return jdbc.queryForObject(
                "INSERT INTO movimientos_inventario (producto_id, cantidad, tipo, tenant_id, created_at) " +
                "VALUES (" + productoId + ", 100, 'ENTRADA', '" + TENANT_A + "', NOW()) RETURNING id", Long.class);
    }

    /** Crea un stock_lote con stockInicial y stockActual dados y retorna su id. */
    private Long crearLote(Long movimientoId, int stockInicial, int stockActual) {
        return jdbc.queryForObject(
                "INSERT INTO stock_lotes (tenant_id, movimiento_id, producto_id, stock_inicial, stock_actual, created_at) " +
                "VALUES ('" + TENANT_A + "', " + movimientoId + ", " + productoId + ", " + stockInicial + ", " + stockActual + ", NOW()) RETURNING id", Long.class);
    }

    private int getStockActualLote(Long loteId) {
        return jdbc.queryForObject("SELECT stock_actual FROM stock_lotes WHERE id = " + loteId, Integer.class);
    }

    private void limpiarLote(Long loteId, Long movimientoId) {
        jdbc.execute("DELETE FROM stock_lotes WHERE id = " + loteId);
        jdbc.execute("DELETE FROM movimientos_inventario WHERE id = " + movimientoId);
    }

    @Test
    @Order(19)
    void t19_restaurarDesdeJson_devolucionParcial_restauraExactamenteCantidadBase() {
        Long movId = crearMovimiento();
        Long loteId = crearLote(movId, 20, 17); // se vendieron 3 (20-3=17)
        try {
            String json = "[[" + loteId + ",3]]"; // venta consumió 3
            txTemplate.execute(s -> {
                TenantContext.setCurrentTenant(TENANT_A);
                stockLoteService.restaurarDesdeJson(json, null, 2, TENANT_A, productoId, null);
                return null;
            });
            // cantidadBase=2 → debe restaurar exactamente 2, no los 3 del JSON
            assertThat(getStockActualLote(loteId)).isEqualTo(19); // 17 + 2
        } finally {
            limpiarLote(loteId, movId);
        }
    }

    @Test
    @Order(20)
    void t20_restaurarDesdeJson_tresDevoluciones1a1a1_cadaUnaRestaurarUno() {
        Long movId = crearMovimiento();
        Long loteId = crearLote(movId, 20, 17); // vendidas 3
        try {
            String json = "[[" + loteId + ",3]]";
            // Primera devolución: 1
            txTemplate.execute(s -> {
                TenantContext.setCurrentTenant(TENANT_A);
                stockLoteService.restaurarDesdeJson(json, null, 1, TENANT_A, productoId, null);
                return null;
            });
            assertThat(getStockActualLote(loteId)).isEqualTo(18);
            // Segunda devolución: 1
            txTemplate.execute(s -> {
                TenantContext.setCurrentTenant(TENANT_A);
                stockLoteService.restaurarDesdeJson(json, null, 1, TENANT_A, productoId, null);
                return null;
            });
            assertThat(getStockActualLote(loteId)).isEqualTo(19);
            // Tercera devolución: 1
            txTemplate.execute(s -> {
                TenantContext.setCurrentTenant(TENANT_A);
                stockLoteService.restaurarDesdeJson(json, null, 1, TENANT_A, productoId, null);
                return null;
            });
            assertThat(getStockActualLote(loteId)).isEqualTo(20); // stock original
        } finally {
            limpiarLote(loteId, movId);
        }
    }

    @Test
    @Order(21)
    void t21_restaurarDesdeJson_dosLotes_greedy_distribuyeCorrectamente() {
        Long movId1 = crearMovimiento();
        Long movId2 = jdbc.queryForObject(
                "INSERT INTO movimientos_inventario (producto_id, cantidad, tipo, tenant_id, created_at) " +
                "VALUES (" + productoId + ", 100, 'ENTRADA', '" + TENANT_A + "', NOW()) RETURNING id", Long.class);
        Long lote1 = crearLote(movId1, 15, 12); // vendidas 3
        Long lote2 = crearLote(movId2, 10, 8);  // vendidas 2
        try {
            // JSON: venta consumió 3 de lote1 y 2 de lote2 (total 5)
            String json = "[[" + lote1 + ",3],[" + lote2 + ",2]]";
            txTemplate.execute(s -> {
                TenantContext.setCurrentTenant(TENANT_A);
                stockLoteService.restaurarDesdeJson(json, null, 4, TENANT_A, productoId, null);
                return null;
            });
            // cantidadBase=4: greedy → lote1 restaura 3, lote2 restaura 1 (3+1=4)
            assertThat(getStockActualLote(lote1)).isEqualTo(15); // 12+3
            assertThat(getStockActualLote(lote2)).isEqualTo(9);  // 8+1
        } finally {
            limpiarLote(lote1, movId1);
            limpiarLote(lote2, movId2);
        }
    }

    @Test
    @Order(22)
    void t22_restaurarDesdeJson_cantidadBaseMenorQueUnLote_segundoLoteIntocado() {
        Long movId1 = crearMovimiento();
        Long movId2 = jdbc.queryForObject(
                "INSERT INTO movimientos_inventario (producto_id, cantidad, tipo, tenant_id, created_at) " +
                "VALUES (" + productoId + ", 100, 'ENTRADA', '" + TENANT_A + "', NOW()) RETURNING id", Long.class);
        Long lote1 = crearLote(movId1, 15, 10); // vendidas 5
        Long lote2 = crearLote(movId2, 10, 5);  // vendidas 5
        try {
            String json = "[[" + lote1 + ",5],[" + lote2 + ",5]]";
            txTemplate.execute(s -> {
                TenantContext.setCurrentTenant(TENANT_A);
                stockLoteService.restaurarDesdeJson(json, null, 3, TENANT_A, productoId, null);
                return null;
            });
            // cantidadBase=3: lote1 restaura 3, lote2 NO toca (restante=0 después del lote1)
            assertThat(getStockActualLote(lote1)).isEqualTo(13); // 10+3
            assertThat(getStockActualLote(lote2)).isEqualTo(5);  // sin cambio
        } finally {
            limpiarLote(lote1, movId1);
            limpiarLote(lote2, movId2);
        }
    }

    @Test
    @Order(23)
    void t23_restaurarDesdeJson_fallbackLoteId_restauraExactamenteCantidadBase() {
        Long movId = crearMovimiento();
        Long loteId = crearLote(movId, 20, 17);
        try {
            // JSON null → usa fallback con loteId
            txTemplate.execute(s -> {
                TenantContext.setCurrentTenant(TENANT_A);
                stockLoteService.restaurarDesdeJson(null, loteId, 2, TENANT_A, productoId, null);
                return null;
            });
            assertThat(getStockActualLote(loteId)).isEqualTo(19); // 17+2
        } finally {
            limpiarLote(loteId, movId);
        }
    }

    @Test
    @Order(24)
    void t24_restaurarDesdeJson_jsonVacio_noRestauraNada() {
        Long movId = crearMovimiento();
        Long loteId = crearLote(movId, 20, 17);
        try {
            txTemplate.execute(s -> {
                TenantContext.setCurrentTenant(TENANT_A);
                stockLoteService.restaurarDesdeJson("[]", null, 2, TENANT_A, productoId, null);
                return null;
            });
            // JSON vacío → contenido.isBlank() → return sin restaurar
            assertThat(getStockActualLote(loteId)).isEqualTo(17); // sin cambio
        } finally {
            limpiarLote(loteId, movId);
        }
    }

    @Test
    @Order(25)
    void t25_consistencia_loteYProductoActualizadosCorrectamente() {
        Long movId = crearMovimiento();
        Long loteId = crearLote(movId, 20, 17);
        int stockProductoAntes = jdbc.queryForObject(
                "SELECT stock_actual FROM productos WHERE id = " + productoId, Integer.class);
        try {
            String json = "[[" + loteId + ",3]]";
            txTemplate.execute(s -> {
                TenantContext.setCurrentTenant(TENANT_A);
                // Simula exactamente lo que DevolucionServiceImpl hace: incremento atómico + restaurar lote
                productoRepository.incrementarStockAtomico(productoId, 2, TENANT_A);
                stockLoteService.restaurarDesdeJson(json, null, 2, TENANT_A, productoId, null);
                return null;
            });
            // Lote recibe +2 (no +3)
            assertThat(getStockActualLote(loteId)).isEqualTo(19); // 17+2
            // Producto recibe +2 vía incremento atómico
            int stockProductoDespues = jdbc.queryForObject(
                    "SELECT stock_actual FROM productos WHERE id = " + productoId, Integer.class);
            assertThat(stockProductoDespues).isEqualTo(stockProductoAntes + 2);
        } finally {
            // Restaurar stock producto
            txTemplate.execute(s -> {
                TenantContext.setCurrentTenant(TENANT_A);
                return productoRepository.incrementarStockAtomico(productoId, -2, TENANT_A);
            });
            limpiarLote(loteId, movId);
        }
    }

    @Test
    @Order(26)
    void t26_restaurarDesdeJson_parMalFormado_ignoraParInvalidoYRestaurarValidos() {
        Long movId = crearMovimiento();
        Long loteId = crearLote(movId, 20, 17);
        try {
            // JSON con un par válido y uno inválido (texto en lugar de número)
            String json = "[[" + loteId + ",2],[abc,xyz]]";
            txTemplate.execute(s -> {
                TenantContext.setCurrentTenant(TENANT_A);
                // No debe lanzar excepción; debe restaurar el par válido
                stockLoteService.restaurarDesdeJson(json, null, 2, TENANT_A, productoId, null);
                return null;
            });
            // El par válido restaura 2 (hasta cantidadBase=2), par inválido ignorado con log.warn
            assertThat(getStockActualLote(loteId)).isEqualTo(19); // 17+2
        } finally {
            limpiarLote(loteId, movId);
        }
    }

    // ─────────────────────────────────────────────────────────────────────────────
    // t27: path completo via DevolucionServiceImpl.crear()
    //      Reproduce exactamente el bug DEV-27:
    //        stock 19 → venta 4 → 15 → devolver 1 (factor=1) → esperado 16
    //      Sin el fix: restaurarDesdeJson ignoraba cantidadBase → lote += 4 = 19
    //      Con el fix: min(4, 1) = 1 → lote += 1 = 16
    // ─────────────────────────────────────────────────────────────────────────────
    @Test
    @Order(27)
    void t27_devolucionViaServiceImpl_factor1_productoYLoteConsistentes() {
        // Arrange: producto=19, lote=19
        jdbc.execute("UPDATE productos SET stock_actual = 19, version = 0 WHERE id = " + productoId);
        Long movId = crearMovimiento();
        Long loteId = crearLote(movId, 19, 19);

        // Venta con 4 unidades (factor=1), con lotesConsumidosJson registrado
        Long ventaIdT27 = jdbc.queryForObject(
                "INSERT INTO ventas (total, estado, tenant_id, created_at, cliente_id) " +
                "VALUES (40.00, 'COMPLETADA', '" + TENANT_A + "', NOW(), " + clienteId + ") RETURNING id", Long.class);
        jdbc.execute(
                "INSERT INTO detalles_venta (venta_id, producto_id, cantidad, precio_unitario, subtotal, factor, lotes_consumidos_json, stock_lote_id) " +
                "VALUES (" + ventaIdT27 + ", " + productoId + ", 4, 10.00, 40.00, 1, '[[" + loteId + ",4]]', " + loteId + ")");

        // Simular el descuento de la venta
        jdbc.execute("UPDATE productos SET stock_actual = 15 WHERE id = " + productoId);
        jdbc.execute("UPDATE stock_lotes SET stock_actual = 15 WHERE id = " + loteId);

        try {
            // Act: DevolucionServiceImpl.crear() — devuelve 1 unidad (factor=1 → cantidadBase=1)
            CrearDevolucionDTO dto = CrearDevolucionDTO.builder()
                    .ventaId(ventaIdT27)
                    .motivo("Test t27 consistencia stock")
                    .reponerStock(true)
                    .detalles(List.of(DevolucionDetalleItemDTO.builder()
                            .productoId(productoId)
                            .cantidadDevuelta(1)
                            .build()))
                    .build();
            devolucionService.crear(dto, usuarioId, TENANT_A);

            // Assert: incrementarStockAtomico(+1) → 16; restaurarDesdeJson min(4,1)=1 → lote 16
            Integer stockProducto = jdbc.queryForObject(
                    "SELECT stock_actual FROM productos WHERE id = " + productoId, Integer.class);
            Integer stockLote = getStockActualLote(loteId);

            assertThat(stockProducto)
                    .as("Producto: 15 + 1 (atómico) = 16; el bug pre-fix daba 19 vía sum(lotes)")
                    .isEqualTo(16);
            assertThat(stockLote)
                    .as("Lote: restaurarDesdeJson([[lote,4]], cantidadBase=1) debe min(4,1)=1 → 15+1=16, no 15+4=19")
                    .isEqualTo(16);
        } finally {
            limpiarLote(loteId, movId);
        }
    }
}
