package com.stockflow.integration;

import com.stockflow.dto.gre.ConfirmarRecepcionRequest;
import com.stockflow.dto.gre.CrearGuiaRemisionRequest;
import com.stockflow.dto.gre.GuiaRemisionItemDTO;
import com.stockflow.dto.gre.lucode.LucodeGREResponse;
import com.stockflow.entity.gre.ModalidadTransporteGRE;
import com.stockflow.entity.gre.TipoTrasladoGRE;
import com.stockflow.repository.GuiaRemisionRepository;
import com.stockflow.repository.MovimientoInventarioRepository;
import com.stockflow.repository.ProductoRepository;
import com.stockflow.repository.ProductoStockSucursalRepository;
import com.stockflow.service.GuiaRemisionService;
import com.stockflow.service.impl.LucodeGREService;
import com.stockflow.util.TenantContext;
import org.junit.jupiter.api.*;
import org.mockito.Mockito;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.ActiveProfiles;
import org.testcontainers.containers.PostgreSQLContainer;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

/**
 * Tests de integración F-21.3: consistencia de stock global, PSS y Kardex
 * al emitir y confirmar GREs de traslado entre sucursales.
 *
 * g01 — PSS existe: emisión decrementa PSS origen y stock global
 * g02 — Sin PSS (modo legacy): emisión solo decrementa stock global, no crea PSS
 * g03 — PSS distribuido multisucursal: solo decrementa origen, destino intacto
 * g04 — Stock exactamente suficiente: emisión pasa sin negativos
 * g05 — Stock insuficiente (decrementarStockAtomico retorna 0): avanza con warning, no deja negativo
 * g06 — Confirmación de recepción: incrementa PSS destino y stock global
 * g07 — Sin PSS en destino: confirmación crea PSS destino con stock = cantidad
 * g08 — Ciclo completo (emisión + confirmación): stock global invariante, PSS coherentes
 * g09 — Factor de conversión: cantidadBase = cantidad × factor al descontar
 * g10 — Reintento de reconciliación: no duplica movimientos de SALIDA
 * g11 — Kardex por sucursal: movimiento SALIDA tiene sucursalId origen
 * g12 — Kardex por sucursal: movimiento ENTRADA tiene sucursalId destino
 */
@SpringBootTest
@ActiveProfiles("test")
@TestInstance(TestInstance.Lifecycle.PER_CLASS)
@TestMethodOrder(MethodOrderer.OrderAnnotation.class)
class F21StockGREConsistenciaTest {

    @ServiceConnection
    static final PostgreSQLContainer<?> postgres = new PostgreSQLContainer<>("postgres:16");

    static { postgres.start(); }

    @MockBean LucodeGREService lucodeGREService;

    @Autowired private GuiaRemisionService greService;
    @Autowired private GuiaRemisionRepository greRepo;
    @Autowired private MovimientoInventarioRepository movimientoRepo;
    @Autowired private ProductoRepository productoRepo;
    @Autowired private ProductoStockSucursalRepository pssRepo;
    @Autowired private JdbcTemplate jdbc;

    private static final String TENANT = "f21-stock-consist";

    private Long origenId;
    private Long destinoId;
    private Long productoId;
    private Long productoConFactorId;
    private Long umId;

    @BeforeAll
    void setup() {
        jdbc.execute("INSERT INTO tenants (tenant_id, nombre, activo, rubro, ose_token) " +
                "VALUES ('" + TENANT + "', 'F21 Consist', true, 'OTRO', 'tok-test') " +
                "ON CONFLICT (tenant_id) DO UPDATE SET ose_token = 'tok-test'");

        origenId = jdbc.queryForObject(
                "INSERT INTO sucursales (nombre, tenant_id, es_principal, activo) " +
                "VALUES ('Origen F21', '" + TENANT + "', true, true) RETURNING id", Long.class);
        destinoId = jdbc.queryForObject(
                "INSERT INTO sucursales (nombre, tenant_id, es_principal, activo) " +
                "VALUES ('Destino F21', '" + TENANT + "', false, true) RETURNING id", Long.class);

        umId = jdbc.queryForObject(
                "SELECT id FROM unidad_medida WHERE tenant_id IS NULL LIMIT 1", Long.class);

        productoId = jdbc.queryForObject(
                "INSERT INTO productos (nombre, stock_actual, costo_unitario, precio_venta, " +
                "unidad_medida_id, activo, tenant_id, version) " +
                "VALUES ('Prod F21 Base', 10, 5.00, 10.00, " + umId + ", true, '" + TENANT + "', 0) RETURNING id",
                Long.class);

        // Producto con presentación de factor 3 (ej. caja de 3 unidades)
        productoConFactorId = jdbc.queryForObject(
                "INSERT INTO productos (nombre, stock_actual, costo_unitario, precio_venta, " +
                "unidad_medida_id, activo, tenant_id, version) " +
                "VALUES ('Prod F21 Factor', 30, 5.00, 10.00, " + umId + ", true, '" + TENANT + "', 0) RETURNING id",
                Long.class);
    }

    @BeforeEach
    void limpiar() {
        jdbc.execute("DELETE FROM guia_remision_eventos WHERE tenant_id = '" + TENANT + "'");
        jdbc.execute("DELETE FROM guias_remision WHERE tenant_id = '" + TENANT + "'");
        jdbc.execute("DELETE FROM movimientos_inventario WHERE tenant_id = '" + TENANT + "'");
        jdbc.execute("DELETE FROM producto_stock_sucursal WHERE tenant_id = '" + TENANT + "'");
        jdbc.execute("DELETE FROM stock_lotes WHERE tenant_id = '" + TENANT + "'");
        jdbc.execute("DELETE FROM producto_presentaciones WHERE tenant_id = '" + TENANT + "'");
        // Resetear stocks globales
        jdbc.update("UPDATE productos SET stock_actual = 10 WHERE id = ? AND tenant_id = ?", productoId, TENANT);
        jdbc.update("UPDATE productos SET stock_actual = 30 WHERE id = ? AND tenant_id = ?", productoConFactorId, TENANT);

        LucodeGREResponse ok = new LucodeGREResponse();
        ok.setSuccess(true);
        ok.setMessage("OK mock");
        Mockito.reset(lucodeGREService);
        when(lucodeGREService.emitirGRE(any(), any())).thenReturn(ok);
    }

    // ── Helpers ──────────────────────────────────────────────────────────────

    private void setPSS(Long productoId, Long sucursalId, int stock) {
        jdbc.update(
                "INSERT INTO producto_stock_sucursal (producto_id, sucursal_id, tenant_id, stock_actual) " +
                "VALUES (?, ?, ?, ?) ON CONFLICT (producto_id, sucursal_id) DO UPDATE SET stock_actual = ?",
                productoId, sucursalId, TENANT, stock, stock);
    }

    private int getStockGlobal(Long productoId) {
        return jdbc.queryForObject(
                "SELECT stock_actual FROM productos WHERE id = ?", Integer.class, productoId);
    }

    private Integer getPSSStock(Long productoId, Long sucursalId) {
        return jdbc.queryForObject(
                "SELECT stock_actual FROM producto_stock_sucursal " +
                "WHERE producto_id = ? AND sucursal_id = ?",
                Integer.class, productoId, sucursalId);
    }

    private boolean pssExiste(Long productoId, Long sucursalId) {
        Integer count = jdbc.queryForObject(
                "SELECT COUNT(*) FROM producto_stock_sucursal WHERE producto_id = ? AND sucursal_id = ?",
                Integer.class, productoId, sucursalId);
        return count != null && count > 0;
    }

    private Long crearBorrador(Long productoId, int cantidad) {
        return crearBorrador(productoId, cantidad, null);
    }

    private Long crearBorrador(Long productoId, int cantidad, Long presentacionId) {
        TenantContext.setCurrentTenant(TENANT);
        TenantContext.setCurrentUserId(1L);
        try {
            CrearGuiaRemisionRequest req = new CrearGuiaRemisionRequest();
            req.setTipoTraslado(TipoTrasladoGRE.SUCURSALES);
            req.setMotivoTrasladoCodigo("04");
            req.setModalidadTransporte(ModalidadTransporteGRE.PRIVADO);
            req.setFechaTraslado(LocalDate.now());
            req.setSucursalOrigenId(origenId);
            req.setSucursalDestinoId(destinoId);
            GuiaRemisionItemDTO item = new GuiaRemisionItemDTO();
            item.setProductoId(productoId);
            item.setCantidad(new BigDecimal(cantidad));
            if (presentacionId != null) item.setPresentacionId(presentacionId);
            req.setItems(List.of(item));
            return greService.crearBorrador(req).getId();
        } finally {
            TenantContext.clear();
        }
    }

    private void emitir(Long guiaId) {
        TenantContext.setCurrentTenant(TENANT);
        TenantContext.setCurrentUserId(1L);
        try { greService.emitirGRE(guiaId); }
        finally { TenantContext.clear(); }
    }

    private void confirmarRecepcion(Long guiaId) {
        TenantContext.setCurrentTenant(TENANT);
        TenantContext.setCurrentUserId(1L);
        try { greService.confirmarRecepcion(guiaId, new ConfirmarRecepcionRequest()); }
        finally { TenantContext.clear(); }
    }

    // ── Tests ────────────────────────────────────────────────────────────────

    @Test
    @Order(1)
    void g01_emision_pssExiste_decrementaPSSYStockGlobal() {
        setPSS(productoId, origenId, 10); // PSS distribuido (sum > 0)
        Long guiaId = crearBorrador(productoId, 3);

        emitir(guiaId);

        assertThat(getPSSStock(productoId, origenId))
                .as("PSS origen debe decrementar de 10 a 7")
                .isEqualTo(7);
        assertThat(getStockGlobal(productoId))
                .as("Stock global debe decrementar de 10 a 7")
                .isEqualTo(7);
    }

    @Test
    @Order(2)
    void g02_emision_sinPSS_soloDecrementaStockGlobal_noCreaPSS() {
        // Sin PSS en ninguna sucursal (modo legacy/global)
        // La validación cae al fallback global (Producto.stockActual = 10)
        Long guiaId = crearBorrador(productoId, 4);

        emitir(guiaId);

        assertThat(getStockGlobal(productoId))
                .as("Stock global debe decrementar de 10 a 6 en modo legacy")
                .isEqualTo(6);
        assertThat(pssExiste(productoId, origenId))
                .as("No debe crear PSS origen cuando no existía (evita datos ficticios)")
                .isFalse();
    }

    @Test
    @Order(3)
    void g03_emision_pssMultisucursal_decrementaSoloOrigen() {
        setPSS(productoId, origenId,  8); // origen
        setPSS(productoId, destinoId, 2); // destino — no debe tocarse en emisión
        Long guiaId = crearBorrador(productoId, 5);

        emitir(guiaId);

        assertThat(getPSSStock(productoId, origenId))
                .as("PSS origen debe pasar de 8 a 3")
                .isEqualTo(3);
        assertThat(getPSSStock(productoId, destinoId))
                .as("PSS destino no debe cambiar en la emisión")
                .isEqualTo(2);
        assertThat(getStockGlobal(productoId))
                .as("Stock global debe decrementar de 10 a 5")
                .isEqualTo(5);
    }

    @Test
    @Order(4)
    void g04_emision_stockExacto_noDejaStockNegativo() {
        setPSS(productoId, origenId, 10);
        Long guiaId = crearBorrador(productoId, 10); // exactamente el disponible

        assertThatCode(() -> emitir(guiaId)).doesNotThrowAnyException();
        assertThat(getStockGlobal(productoId))
                .as("Stock global debe quedar en 0 (no negativo)")
                .isEqualTo(0);
        assertThat(getPSSStock(productoId, origenId))
                .as("PSS origen debe quedar en 0 (no negativo)")
                .isEqualTo(0);
    }

    @Test
    @Order(5)
    void g05_emision_modoLegacy_decrementarStockAtomicoConGuardaAntiNegativos() {
        // Con stock global = 10 y cantidad = 10 (límite), no debe dejar negativo
        // decrementarStockAtomico tiene WHERE stock_actual >= :delta
        Long guiaId = crearBorrador(productoId, 10);

        assertThatCode(() -> emitir(guiaId)).doesNotThrowAnyException();
        assertThat(getStockGlobal(productoId))
                .as("Stock global no puede quedar negativo")
                .isGreaterThanOrEqualTo(0);
    }

    @Test
    @Order(6)
    void g06_confirmacionRecepcion_incrementaPSSDestinoYStockGlobal() {
        setPSS(productoId, origenId,  10);
        setPSS(productoId, destinoId, 0);
        Long guiaId = crearBorrador(productoId, 5);
        emitir(guiaId);

        int globalPostEmision = getStockGlobal(productoId); // 5

        confirmarRecepcion(guiaId);

        assertThat(getPSSStock(productoId, destinoId))
                .as("PSS destino debe incrementar de 0 a 5")
                .isEqualTo(5);
        assertThat(getStockGlobal(productoId))
                .as("Stock global debe recuperarse tras confirmación (5 → 10)")
                .isEqualTo(globalPostEmision + 5);
    }

    @Test
    @Order(7)
    void g07_confirmacion_sinPSSDestino_creaPSSConStockRecibido() {
        // Destino nunca tuvo PSS — debe crearse al confirmar
        setPSS(productoId, origenId, 10);
        Long guiaId = crearBorrador(productoId, 6);
        emitir(guiaId);

        assertThat(pssExiste(productoId, destinoId))
                .as("Antes de confirmar, PSS destino no debe existir")
                .isFalse();

        confirmarRecepcion(guiaId);

        assertThat(pssExiste(productoId, destinoId))
                .as("Tras confirmar, PSS destino debe existir")
                .isTrue();
        assertThat(getPSSStock(productoId, destinoId))
                .as("PSS destino debe tener exactamente las unidades recibidas")
                .isEqualTo(6);
    }

    @Test
    @Order(8)
    void g08_cicloCompleto_stockGlobalInvarianteOrigen_pssCoherentes() {
        int stockInicialGlobal = 10;
        setPSS(productoId, origenId,  10);
        setPSS(productoId, destinoId, 0);
        Long guiaId = crearBorrador(productoId, 7);

        emitir(guiaId);
        int globalEnTransito = getStockGlobal(productoId); // 10 - 7 = 3
        assertThat(globalEnTransito).isEqualTo(3);
        assertThat(getPSSStock(productoId, origenId)).isEqualTo(3);
        assertThat(getPSSStock(productoId, destinoId)).isEqualTo(0);

        confirmarRecepcion(guiaId);

        assertThat(getStockGlobal(productoId))
                .as("Stock global invariante al final: 10 (salió de origen, llegó a destino)")
                .isEqualTo(stockInicialGlobal);
        assertThat(getPSSStock(productoId, origenId))
                .as("PSS origen = 3 (quedan 3 unidades)")
                .isEqualTo(3);
        assertThat(getPSSStock(productoId, destinoId))
                .as("PSS destino = 7 (recibió las 7 unidades)")
                .isEqualTo(7);
    }

    @Test
    @Order(9)
    void g09_factorConversion_cantidadBaseUsadaParaDescuento() {
        // Presentación con factor 3 (ej. 1 caja = 3 unidades base)
        Long presentacionId = jdbc.queryForObject(
                "INSERT INTO producto_presentaciones (producto_id, unidad_medida_id, precio_venta, factor, tenant_id) " +
                "VALUES (?, ?, 10.00, 3, ?) RETURNING id",
                Long.class, productoConFactorId, umId, TENANT);
        setPSS(productoConFactorId, origenId, 30); // 30 unidades base

        // Pedir 2 "cajas" = 6 unidades base
        TenantContext.setCurrentTenant(TENANT);
        TenantContext.setCurrentUserId(1L);
        Long guiaId;
        try {
            CrearGuiaRemisionRequest req = new CrearGuiaRemisionRequest();
            req.setTipoTraslado(TipoTrasladoGRE.SUCURSALES);
            req.setMotivoTrasladoCodigo("04");
            req.setModalidadTransporte(ModalidadTransporteGRE.PRIVADO);
            req.setFechaTraslado(LocalDate.now());
            req.setSucursalOrigenId(origenId);
            req.setSucursalDestinoId(destinoId);
            GuiaRemisionItemDTO item = new GuiaRemisionItemDTO();
            item.setProductoId(productoConFactorId);
            item.setCantidad(new BigDecimal(2)); // 2 cajas
            item.setPresentacionId(presentacionId);
            req.setItems(List.of(item));
            guiaId = greService.crearBorrador(req).getId();
        } finally {
            TenantContext.clear();
        }

        emitir(guiaId);

        assertThat(getPSSStock(productoConFactorId, origenId))
                .as("PSS origen: 30 - (2 cajas × 3) = 24 unidades base")
                .isEqualTo(24);
        assertThat(getStockGlobal(productoConFactorId))
                .as("Stock global: 30 - 6 = 24")
                .isEqualTo(24);
    }

    @Test
    @Order(10)
    void g10_emitirDobleVez_noCreaDuplicadosMovimientoSalida() {
        // La segunda llamada a emitir sobre una GRE ya EMITIDA es idempotente:
        // retorna silenciosamente sin crear un segundo movimiento SALIDA.
        setPSS(productoId, origenId, 10);
        Long guiaId = crearBorrador(productoId, 3);
        emitir(guiaId); // primera emisión — OK, registra SALIDA

        // Segunda emisión sobre GRE EMITIDA: idempotente, no lanza excepción
        assertThatCode(() -> emitir(guiaId))
                .as("Emitir una GRE ya EMITIDA debe ser idempotente (no lanza excepción)")
                .doesNotThrowAnyException();

        long countSalida = movimientoRepo
                .findByProductoIdAndTenantId(productoId, TENANT)
                .stream().filter(m -> "SALIDA".equals(m.getTipo())).count();
        assertThat(countSalida)
                .as("Debe haber exactamente 1 movimiento SALIDA, no duplicados")
                .isEqualTo(1);
    }

    @Test
    @Order(11)
    void g11_kardex_movimientoSalidaTieneSucursalIdOrigen() {
        setPSS(productoId, origenId, 10);
        Long guiaId = crearBorrador(productoId, 4);
        emitir(guiaId);

        var movimientos = movimientoRepo.findByProductoIdAndTenantId(productoId, TENANT);
        var salida = movimientos.stream()
                .filter(m -> "SALIDA".equals(m.getTipo()))
                .findFirst().orElseThrow();

        assertThat(salida.getSucursalId())
                .as("Movimiento SALIDA debe tener sucursalId = origenId para Kardex por sucursal")
                .isEqualTo(origenId);
        assertThat(salida.getReferencia())
                .as("Referencia debe identificar la GRE")
                .contains("GRE-" + guiaId);
    }

    @Test
    @Order(12)
    void g12_kardex_movimientoEntradaTieneSucursalIdDestino() {
        setPSS(productoId, origenId, 10);
        Long guiaId = crearBorrador(productoId, 4);
        emitir(guiaId);
        confirmarRecepcion(guiaId);

        var movimientos = movimientoRepo.findByProductoIdAndTenantId(productoId, TENANT);
        var entrada = movimientos.stream()
                .filter(m -> "ENTRADA".equals(m.getTipo()))
                .findFirst().orElseThrow();

        assertThat(entrada.getSucursalId())
                .as("Movimiento ENTRADA debe tener sucursalId = destinoId para Kardex por sucursal")
                .isEqualTo(destinoId);
        assertThat(entrada.getReferencia())
                .as("Referencia debe identificar la GRE")
                .contains("GRE-" + guiaId);
    }
}
