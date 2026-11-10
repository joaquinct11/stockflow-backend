package com.stockflow.integration;

import com.stockflow.entity.StockLote;
import com.stockflow.repository.StockLoteRepository;
import com.stockflow.service.SucursalService;
import com.stockflow.service.TenantService;
import com.stockflow.util.TenantContext;
import org.junit.jupiter.api.*;
import org.mockito.Mockito;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.boot.test.mock.mockito.SpyBean;
import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.ActiveProfiles;
import org.testcontainers.containers.PostgreSQLContainer;

import java.time.LocalDate;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.doThrow;

/**
 * IT-REG: Pruebas de integración para regularizarDatosSucursalPrincipal().
 *
 * Verifica que el backfill completo (stock, variantes, lotes históricos) se ejecuta
 * correctamente sobre PostgreSQL real. Usa tenants únicos por escenario para aislamiento.
 */
@SpringBootTest
@ActiveProfiles("test")
@TestInstance(TestInstance.Lifecycle.PER_CLASS)
@TestMethodOrder(MethodOrderer.OrderAnnotation.class)
class RegularizacionSucursalIntegrationTest {

    @ServiceConnection
    static PostgreSQLContainer<?> postgres = new PostgreSQLContainer<>("postgres:16");

    static {
        postgres.start();
    }

    @Autowired
    private SucursalService sucursalService;

    @SpyBean
    private StockLoteRepository stockLoteRepository;

    @MockBean
    private TenantService tenantService;

    @Autowired
    private JdbcTemplate jdbc;

    // ── Tenants únicos por escenario ───────────────────────────────────────────
    private static final String TENANT_STOCK     = "tc-reg-stock-001";
    private static final String TENANT_VARIANTES = "tc-reg-var-002";
    private static final String TENANT_SERVICIO  = "tc-reg-svc-003";
    private static final String TENANT_LOTES     = "tc-reg-lotes-004";
    private static final String TENANT_ROLLBACK  = "tc-reg-rb-005";
    private static final String TENANT_IDEM      = "tc-reg-idem-006";
    private static final Long   USER_ID          = 97001L;

    // IDs de sucursal por tenant (rellenados en @BeforeAll)
    private Long sucursalStockId;
    private Long sucursalVariantesId;
    private Long sucursalServicioId;
    private Long sucursalLotesId;
    private Long sucursalRollbackId;
    private Long sucursalIdemId;

    // ── Setup global ───────────────────────────────────────────────────────────

    @BeforeAll
    void insertarDatosBase() {
        Long rolId = jdbc.queryForObject(
                "SELECT id FROM roles WHERE nombre = 'ADMIN' LIMIT 1", Long.class);
        Long umId = jdbc.queryForObject(
                "SELECT id FROM unidad_medida WHERE tenant_id IS NULL LIMIT 1", Long.class);

        for (String t : List.of(TENANT_STOCK, TENANT_VARIANTES, TENANT_SERVICIO,
                                TENANT_LOTES, TENANT_ROLLBACK, TENANT_IDEM)) {
            jdbc.update("INSERT INTO tenants (tenant_id, nombre, activo, rubro) " +
                    "VALUES (?, 'Empresa Test Reg', true, 'FERRETERIA') ON CONFLICT DO NOTHING", t);
        }

        jdbc.update("INSERT INTO usuarios (id, email, contraseña, nombre, rol_id, activo, tenant_id) " +
                "VALUES (?, 'reguser@test.com', 'x', 'Reg User', ?, true, ?) ON CONFLICT DO NOTHING",
                USER_ID, rolId, TENANT_STOCK);

        sucursalStockId     = crearSucursalPrincipal(TENANT_STOCK, umId);
        sucursalVariantesId = crearSucursalPrincipal(TENANT_VARIANTES, umId);
        sucursalServicioId  = crearSucursalPrincipal(TENANT_SERVICIO, umId);
        sucursalLotesId     = crearSucursalPrincipal(TENANT_LOTES, umId);
        sucursalRollbackId  = crearSucursalPrincipal(TENANT_ROLLBACK, umId);
        sucursalIdemId      = crearSucursalPrincipal(TENANT_IDEM, umId);
    }

    /** Inserta una sucursal principal para el tenant dado y devuelve su id. */
    private Long crearSucursalPrincipal(String tenantId, Long umId) {
        return jdbc.queryForObject(
                "INSERT INTO sucursales (nombre, tenant_id, es_principal, activo, bloqueada_por_plan) " +
                "VALUES ('Principal', ?, true, true, false) RETURNING id", Long.class, tenantId);
    }

    @BeforeEach
    void setContexto() {
        // Limpia cualquier stub del test anterior (especialmente IT-REG-5)
        Mockito.reset(stockLoteRepository);
        TenantContext.setCurrentTenant(TENANT_STOCK); // valor por defecto; cada test lo sobreescribe
        TenantContext.setCurrentUserId(USER_ID);
    }

    @AfterEach
    void clearContexto() {
        TenantContext.clear();
    }

    // ── IT-REG-1: Stock global > 0 ────────────────────────────────────────────

    @Test
    @Order(1)
    @DisplayName("IT-REG-1 — stock global > 0: producto_stock_sucursal se puebla desde productos.stock_actual")
    void it1_stockGlobal_seInicializaEnSucursal() {
        Long umId = jdbc.queryForObject("SELECT id FROM unidad_medida WHERE tenant_id IS NULL LIMIT 1", Long.class);

        Long prodId = jdbc.queryForObject(
                "INSERT INTO productos (nombre, stock_actual, costo_unitario, precio_venta, " +
                "unidad_medida_id, activo, tenant_id, tipo) " +
                "VALUES ('Tornillo M8', 100, 1.00, 2.00, ?, true, ?, 'PRODUCTO') RETURNING id",
                Long.class, umId, TENANT_STOCK);

        TenantContext.setCurrentTenant(TENANT_STOCK);
        sucursalService.regularizarDatosSucursalPrincipal(TENANT_STOCK);

        Integer stockEnSucursal = jdbc.queryForObject(
                "SELECT stock_actual FROM producto_stock_sucursal " +
                "WHERE producto_id = ? AND sucursal_id = ?",
                Integer.class, prodId, sucursalStockId);

        assertThat(stockEnSucursal).isEqualTo(100);

        // El campo original de productos no debe cambiar
        Integer stockProducto = jdbc.queryForObject(
                "SELECT stock_actual FROM productos WHERE id = ?", Integer.class, prodId);
        assertThat(stockProducto).isEqualTo(100);
    }

    // ── IT-REG-2: Stock de variantes ──────────────────────────────────────────

    @Test
    @Order(2)
    @DisplayName("IT-REG-2 — variantes: producto_variante_stock_sucursal se puebla desde producto_variantes")
    void it2_stockVariantes_seInicializaEnSucursal() {
        Long umId = jdbc.queryForObject("SELECT id FROM unidad_medida WHERE tenant_id IS NULL LIMIT 1", Long.class);

        Long prodId = jdbc.queryForObject(
                "INSERT INTO productos (nombre, stock_actual, costo_unitario, precio_venta, " +
                "unidad_medida_id, activo, tenant_id, tipo) " +
                "VALUES ('Remera M', 50, 5.00, 10.00, ?, true, ?, 'PRODUCTO') RETURNING id",
                Long.class, umId, TENANT_VARIANTES);

        Long varianteId = jdbc.queryForObject(
                "INSERT INTO producto_variantes (producto_id, talla, color, stock_actual, stock_minimo, activo, tenant_id) " +
                "VALUES (?, 'M', 'Rojo', 30, 5, true, ?) RETURNING id",
                Long.class, prodId, TENANT_VARIANTES);

        TenantContext.setCurrentTenant(TENANT_VARIANTES);
        sucursalService.regularizarDatosSucursalPrincipal(TENANT_VARIANTES);

        Integer stockVariante = jdbc.queryForObject(
                "SELECT stock_actual FROM producto_variante_stock_sucursal " +
                "WHERE variante_id = ? AND sucursal_id = ?",
                Integer.class, varianteId, sucursalVariantesId);

        assertThat(stockVariante).isEqualTo(30);

        Integer stockMinimo = jdbc.queryForObject(
                "SELECT stock_minimo FROM producto_variante_stock_sucursal " +
                "WHERE variante_id = ? AND sucursal_id = ?",
                Integer.class, varianteId, sucursalVariantesId);
        assertThat(stockMinimo).isEqualTo(5);
    }

    // ── IT-REG-3: Productos SERVICIO ──────────────────────────────────────────

    @Test
    @Order(3)
    @DisplayName("IT-REG-3 — SERVICIO: se crea fila en producto_stock_sucursal con stock_actual = 0")
    void it3_productoServicio_seCreaFilaConCeroStock() {
        Long umId = jdbc.queryForObject("SELECT id FROM unidad_medida WHERE tenant_id IS NULL LIMIT 1", Long.class);

        Long servicioId = jdbc.queryForObject(
                "INSERT INTO productos (nombre, stock_actual, costo_unitario, precio_venta, " +
                "unidad_medida_id, activo, tenant_id, tipo) " +
                "VALUES ('Consultoría', 0, 0.00, 150.00, ?, true, ?, 'SERVICIO') RETURNING id",
                Long.class, umId, TENANT_SERVICIO);

        TenantContext.setCurrentTenant(TENANT_SERVICIO);
        sucursalService.regularizarDatosSucursalPrincipal(TENANT_SERVICIO);

        Integer stockServicio = jdbc.queryForObject(
                "SELECT stock_actual FROM producto_stock_sucursal " +
                "WHERE producto_id = ? AND sucursal_id = ?",
                Integer.class, servicioId, sucursalServicioId);

        assertThat(stockServicio).isEqualTo(0);

        // Debe existir solo una fila (no duplicada)
        Integer count = jdbc.queryForObject(
                "SELECT COUNT(*) FROM producto_stock_sucursal " +
                "WHERE producto_id = ? AND sucursal_id = ?",
                Integer.class, servicioId, sucursalServicioId);
        assertThat(count).isEqualTo(1);
    }

    // ── IT-REG-4: Lotes históricos + validación FEFO ──────────────────────────

    @Test
    @Order(4)
    @DisplayName("IT-REG-4 — lotes históricos: sucursal_id asignado, cantidades intactas, FEFO los encuentra")
    void it4_lotesHistoricos_sucursalAsignadaYFefoFunciona() {
        Long umId = jdbc.queryForObject("SELECT id FROM unidad_medida WHERE tenant_id IS NULL LIMIT 1", Long.class);

        Long prodId = jdbc.queryForObject(
                "INSERT INTO productos (nombre, stock_actual, costo_unitario, precio_venta, " +
                "unidad_medida_id, activo, tenant_id, tipo) " +
                "VALUES ('Ibuprofeno 400mg', 25, 3.00, 6.00, ?, true, ?, 'PRODUCTO') RETURNING id",
                Long.class, umId, TENANT_LOTES);

        Long rolId = jdbc.queryForObject("SELECT id FROM roles WHERE nombre = 'ADMIN' LIMIT 1", Long.class);
        Long userLocalId = jdbc.queryForObject(
                "INSERT INTO usuarios (email, contraseña, nombre, rol_id, activo, tenant_id) " +
                "VALUES ('lotes@test.com', 'x', 'Lotes User', ?, true, ?) RETURNING id",
                Long.class, rolId, TENANT_LOTES);

        Long movId = jdbc.queryForObject(
                "INSERT INTO movimientos_inventario (producto_id, cantidad, tipo, usuario_id, tenant_id) " +
                "VALUES (?, 25, 'COMPRA', ?, ?) RETURNING id",
                Long.class, prodId, userLocalId, TENANT_LOTES);

        // Lote histórico SIN sucursal_id (simula dato previo a multi-local)
        Long loteId = jdbc.queryForObject(
                "INSERT INTO stock_lotes (tenant_id, movimiento_id, producto_id, " +
                "fecha_vencimiento, stock_inicial, stock_actual) " +
                "VALUES (?, ?, ?, '2027-06-30', 25, 25) RETURNING id",
                Long.class, TENANT_LOTES, movId, prodId);

        // Verificar que antes de regularizar el lote no tiene sucursal
        String sucursalAntes = jdbc.queryForObject(
                "SELECT CAST(sucursal_id AS TEXT) FROM stock_lotes WHERE id = ?",
                String.class, loteId);
        assertThat(sucursalAntes).isNull();

        TenantContext.setCurrentTenant(TENANT_LOTES);
        sucursalService.regularizarDatosSucursalPrincipal(TENANT_LOTES);

        // Después del backfill el lote debe tener la sucursal principal asignada
        Long sucursalDespues = jdbc.queryForObject(
                "SELECT sucursal_id FROM stock_lotes WHERE id = ?", Long.class, loteId);
        assertThat(sucursalDespues).isEqualTo(sucursalLotesId);

        // Las cantidades no deben haber cambiado
        Integer stockActual = jdbc.queryForObject(
                "SELECT stock_actual FROM stock_lotes WHERE id = ?", Integer.class, loteId);
        assertThat(stockActual).isEqualTo(25);

        String fechaVenc = jdbc.queryForObject(
                "SELECT CAST(fecha_vencimiento AS TEXT) FROM stock_lotes WHERE id = ?",
                String.class, loteId);
        assertThat(fechaVenc).isEqualTo("2027-06-30");

        // FEFO con sucursal ahora debe encontrar el lote
        List<StockLote> disponibles = stockLoteRepository.findDisponiblesConSucursal(
                prodId, TENANT_LOTES, sucursalLotesId, LocalDate.now());
        assertThat(disponibles).hasSize(1);
        assertThat(disponibles.get(0).getId()).isEqualTo(loteId);
    }

    // ── IT-REG-5: Rollback ante fallo en lotes ────────────────────────────────

    @Test
    @Order(5)
    @DisplayName("IT-REG-5 — rollback: si asignarSucursalDondeEsNulo falla, no queda stock en sucursal")
    void it5_rollback_siLotesFalla_noQuedaronCambios() {
        Long umId = jdbc.queryForObject("SELECT id FROM unidad_medida WHERE tenant_id IS NULL LIMIT 1", Long.class);

        jdbc.update("INSERT INTO productos (nombre, stock_actual, costo_unitario, precio_venta, " +
                "unidad_medida_id, activo, tenant_id, tipo) " +
                "VALUES ('Producto RB', 10, 1.00, 2.00, ?, true, ?, 'PRODUCTO')",
                umId, TENANT_ROLLBACK);

        // El spy hace fallar la llamada a asignarSucursalDondeEsNulo
        doThrow(new RuntimeException("Error simulado en lotes"))
                .when(stockLoteRepository)
                .asignarSucursalDondeEsNulo(anyLong(), anyString());

        TenantContext.setCurrentTenant(TENANT_ROLLBACK);
        try {
            sucursalService.regularizarDatosSucursalPrincipal(TENANT_ROLLBACK);
        } catch (RuntimeException ignored) {
            // excepción esperada
        }

        // La transacción debe haberse revertido: no deben existir filas en producto_stock_sucursal
        Integer countStock = jdbc.queryForObject(
                "SELECT COUNT(*) FROM producto_stock_sucursal WHERE tenant_id = ?",
                Integer.class, TENANT_ROLLBACK);
        assertThat(countStock).isZero();
    }

    // ── IT-REG-6: Idempotencia ────────────────────────────────────────────────

    @Test
    @Order(6)
    @DisplayName("IT-REG-6 — idempotencia: ejecutar dos veces no duplica ni altera los datos")
    void it6_idempotencia_dosEjecucionesNoDuplicanDatos() {
        Long umId = jdbc.queryForObject("SELECT id FROM unidad_medida WHERE tenant_id IS NULL LIMIT 1", Long.class);

        Long prodId = jdbc.queryForObject(
                "INSERT INTO productos (nombre, stock_actual, costo_unitario, precio_venta, " +
                "unidad_medida_id, activo, tenant_id, tipo) " +
                "VALUES ('Prod Idem', 50, 2.00, 4.00, ?, true, ?, 'PRODUCTO') RETURNING id",
                Long.class, umId, TENANT_IDEM);

        Long rolId = jdbc.queryForObject("SELECT id FROM roles WHERE nombre = 'ADMIN' LIMIT 1", Long.class);
        Long userLocalId = jdbc.queryForObject(
                "INSERT INTO usuarios (email, contraseña, nombre, rol_id, activo, tenant_id) " +
                "VALUES ('idem@test.com', 'x', 'Idem User', ?, true, ?) RETURNING id",
                Long.class, rolId, TENANT_IDEM);

        Long movId = jdbc.queryForObject(
                "INSERT INTO movimientos_inventario (producto_id, cantidad, tipo, usuario_id, tenant_id) " +
                "VALUES (?, 50, 'COMPRA', ?, ?) RETURNING id",
                Long.class, prodId, userLocalId, TENANT_IDEM);

        jdbc.update("INSERT INTO stock_lotes (tenant_id, movimiento_id, producto_id, " +
                "stock_inicial, stock_actual) VALUES (?, ?, ?, 50, 50)",
                TENANT_IDEM, movId, prodId);

        TenantContext.setCurrentTenant(TENANT_IDEM);

        // Primera ejecución
        sucursalService.regularizarDatosSucursalPrincipal(TENANT_IDEM);

        Integer stockPrimera = jdbc.queryForObject(
                "SELECT stock_actual FROM producto_stock_sucursal " +
                "WHERE producto_id = ? AND sucursal_id = ?",
                Integer.class, prodId, sucursalIdemId);
        Integer countPrimera = jdbc.queryForObject(
                "SELECT COUNT(*) FROM producto_stock_sucursal WHERE tenant_id = ?",
                Integer.class, TENANT_IDEM);

        // Segunda ejecución — no debe fallar ni duplicar
        sucursalService.regularizarDatosSucursalPrincipal(TENANT_IDEM);

        Integer stockSegunda = jdbc.queryForObject(
                "SELECT stock_actual FROM producto_stock_sucursal " +
                "WHERE producto_id = ? AND sucursal_id = ?",
                Integer.class, prodId, sucursalIdemId);
        Integer countSegunda = jdbc.queryForObject(
                "SELECT COUNT(*) FROM producto_stock_sucursal WHERE tenant_id = ?",
                Integer.class, TENANT_IDEM);

        assertThat(stockSegunda).isEqualTo(stockPrimera);
        assertThat(countSegunda).isEqualTo(countPrimera);

        // Los lotes también deben tener sucursal asignada (solo una vez)
        Integer countLotesSinSucursal = jdbc.queryForObject(
                "SELECT COUNT(*) FROM stock_lotes WHERE tenant_id = ? AND sucursal_id IS NULL",
                Integer.class, TENANT_IDEM);
        assertThat(countLotesSinSucursal).isZero();
    }
}
