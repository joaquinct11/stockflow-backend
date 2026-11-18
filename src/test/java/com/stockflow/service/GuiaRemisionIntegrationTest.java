package com.stockflow.service;

import com.stockflow.dto.gre.*;
import com.stockflow.entity.gre.*;
import com.stockflow.repository.GuiaRemisionRepository;
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
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

/**
 * Tests de integración para el módulo GRE con Testcontainers (PostgreSQL real).
 * LucodeGREService se mockea — no se realizan emisiones reales al sandbox.
 *
 * Precondición: Docker disponible en el entorno de CI.
 */
@SpringBootTest
@ActiveProfiles("test")
@TestInstance(TestInstance.Lifecycle.PER_CLASS)
@TestMethodOrder(MethodOrderer.OrderAnnotation.class)
@Testcontainers
class GuiaRemisionIntegrationTest {

    @Container
    @ServiceConnection
    static PostgreSQLContainer<?> postgres = new PostgreSQLContainer<>("postgres:16");

    static {
        postgres.start();
    }

    @Autowired private GuiaRemisionService    guiaService;
    @Autowired private GuiaRemisionRepository guiaRepo;
    @Autowired private JdbcTemplate           jdbc;

    @MockBean  private LucodeGREService lucodeService;

    private static final String TENANT_ID = "integ-test-tenant";
    private Long guiaBorradorId;

    @BeforeAll
    void setupTenant() {
        // Tenant mínimo (sin columna plan — el plan viene de suscripciones)
        jdbc.update("""
            INSERT INTO tenants (tenant_id, nombre, activo, ruc, serie_guia, ose_token, created_at)
            VALUES (?, 'Empresa Integración', true, '20999999901', 'T001', 'tok_sandbox', NOW())
            ON CONFLICT (tenant_id) DO NOTHING
            """, TENANT_ID);

        // Usuario mínimo requerido como FK de suscripciones
        jdbc.update("""
            INSERT INTO usuarios (email, contraseña, nombre, activo, created_at)
            VALUES ('integ-test@test.com', 'pwd_hash', 'Usuario Integ', true, NOW())
            ON CONFLICT (email) DO NOTHING
            """);
        Long usuarioId = jdbc.queryForObject(
                "SELECT id FROM usuarios WHERE email = 'integ-test@test.com'", Long.class);

        // Suscripción PRO — PlanLimitService.isBasico() la consulta por tenant_id
        Integer suscExists = jdbc.queryForObject(
                "SELECT COUNT(*) FROM suscripciones WHERE tenant_id = ?", Integer.class, TENANT_ID);
        if (suscExists == null || suscExists == 0) {
            jdbc.update("""
                INSERT INTO suscripciones (usuario_principal_id, tenant_id, plan_id, precio_mensual, estado, created_at)
                VALUES (?, ?, 'PRO', 0.00, 'ACTIVA', NOW())
                """, usuarioId, TENANT_ID);
        }

        // Producto de prueba: usar columnas reales y lookup de unidad_medida global
        Long umId = jdbc.queryForObject(
                "SELECT id FROM unidad_medida WHERE tenant_id IS NULL LIMIT 1", Long.class);
        jdbc.update("""
            INSERT INTO productos (id, tenant_id, nombre, precio_venta, costo_unitario, stock_actual, activo, unidad_medida_id, created_at)
            VALUES (9001, ?, 'Producto Integ Test', 10.00, 5.00, 100, true, ?, NOW())
            ON CONFLICT (id) DO NOTHING
            """, TENANT_ID, umId);

        TenantContext.setCurrentTenant(TENANT_ID);
        TenantContext.setCurrentUserId(1L);
    }

    @AfterAll
    void cleanup() {
        TenantContext.clear();
    }

    // ── IT-1: Crear borrador ────────────────────────────────────────────────────

    @Test
    @Order(1)
    @DisplayName("IT-1: Crear borrador GRE persiste en DB con estado BORRADOR")
    void crearBorrador_persiste_enDB() {
        GuiaRemisionResponse resp = guiaService.crearBorrador(buildRequest("idem-integ-001"));

        assertThat(resp.getId()).isNotNull();
        assertThat(resp.getEstadoTributario()).isEqualTo(EstadoTributarioGRE.BORRADOR);
        assertThat(resp.getSerie()).isEqualTo("T001");
        assertThat(resp.getCorrelativo()).isGreaterThan(0);

        this.guiaBorradorId = resp.getId();

        // Verificar en DB real
        var guia = guiaRepo.findByIdAndTenantId(guiaBorradorId, TENANT_ID);
        assertThat(guia).isPresent();
        assertThat(guia.get().getEstadoTributario()).isEqualTo(EstadoTributarioGRE.BORRADOR);
    }

    // ── IT-2: Idempotencia de creación ──────────────────────────────────────────

    @Test
    @Order(2)
    @DisplayName("IT-2: Mismo idempotency_key devuelve la guía existente sin duplicar")
    void crearBorrador_mismaKey_esIdempotente() {
        // Crear con la misma key de IT-1
        GuiaRemisionResponse resp = guiaService.crearBorrador(buildRequest("idem-integ-001"));

        assertThat(resp.getId()).isEqualTo(guiaBorradorId);

        // Exactamente 1 registro con esa key en DB
        Integer count = jdbc.queryForObject(
                "SELECT COUNT(*) FROM guias_remision WHERE idempotency_key = ? AND tenant_id = ?",
                Integer.class, "idem-integ-001", TENANT_ID);
        assertThat(count).isEqualTo(1);
    }

    // ── IT-3: Acceso universal — BÁSICO + cualquier rubro puede crear GRE ───────

    @Test
    @Order(3)
    @DisplayName("IT-3: BÁSICO + RESTAURANTE puede crear GRE (acceso universal)")
    void planBasico_rubroRestaurante_permitido() {
        jdbc.update("UPDATE suscripciones SET plan_id = 'BASICO' WHERE tenant_id = ?", TENANT_ID);
        jdbc.update("UPDATE tenants SET rubro = 'RESTAURANTE' WHERE tenant_id = ?", TENANT_ID);
        try {
            GuiaRemisionResponse resp = guiaService.crearBorrador(buildRequest("idem-integ-basico"));
            assertThat(resp.getEstadoTributario()).isEqualTo(EstadoTributarioGRE.BORRADOR);
        } finally {
            jdbc.update("UPDATE suscripciones SET plan_id = 'PRO' WHERE tenant_id = ?", TENANT_ID);
            jdbc.update("UPDATE tenants SET rubro = 'OTRO' WHERE tenant_id = ?", TENANT_ID);
        }
    }

    // ── IT-4: Emisión con Lucode exitoso ────────────────────────────────────────

    @Test
    @Order(4)
    @DisplayName("IT-4: Emisión con Lucode exitoso cambia estado a EMITIDA en DB")
    void emitir_lucodeExito_estadoEmitidaEnDB() {
        var payload = new com.stockflow.dto.gre.lucode.LucodeGREPayload();
        payload.setHash("hashInteg001");
        payload.setXml("https://sandbox.apisunat.pe/xml/T001-00000001.xml");
        var resp = new com.stockflow.dto.gre.lucode.LucodeGREResponse();
        resp.setSuccess(true);
        resp.setPayload(payload);
        when(lucodeService.emitirGRE(any(), anyString())).thenReturn(resp);

        GuiaRemisionResponse emitida = guiaService.emitirGRE(guiaBorradorId);

        assertThat(emitida.getEstadoTributario()).isEqualTo(EstadoTributarioGRE.EMITIDA);
        assertThat(emitida.getLucodeHash()).isEqualTo("hashInteg001");

        // Verificar en DB
        var guia = guiaRepo.findByIdAndTenantId(guiaBorradorId, TENANT_ID);
        assertThat(guia.get().getEstadoTributario()).isEqualTo(EstadoTributarioGRE.EMITIDA);
        assertThat(guia.get().getLucodeHash()).isEqualTo("hashInteg001");
    }

    // ── IT-5: Emisión idempotente ───────────────────────────────────────────────

    @Test
    @Order(5)
    @DisplayName("IT-5: Segunda llamada a emitir guía ya EMITIDA no rellamada a Lucode")
    void emitir_yaEmitida_idempotente() {
        // Resetear mock para detectar si se rellamó
        Mockito.reset(lucodeService);

        GuiaRemisionResponse resp = guiaService.emitirGRE(guiaBorradorId);
        assertThat(resp.getEstadoTributario()).isEqualTo(EstadoTributarioGRE.EMITIDA);

        verifyNoInteractions(lucodeService);
    }

    // ── IT-6: Lucode rechaza — estado ERROR, DB consistente ────────────────────

    @Test
    @Order(6)
    @DisplayName("IT-6: Lucode rechaza → estado ERROR en DB, sin movimiento de inventario")
    void emitir_lucodeRechaza_estadoErrorEnDB() {
        // Crear nueva guía para esta prueba
        GuiaRemisionResponse nueva = guiaService.crearBorrador(buildRequest("idem-integ-006"));
        Long nuevaId = nueva.getId();

        var resp = new com.stockflow.dto.gre.lucode.LucodeGREResponse();
        resp.setSuccess(false);
        resp.setMessage("Serie inválida según SUNAT");
        when(lucodeService.emitirGRE(any(), anyString())).thenReturn(resp);

        assertThatThrownBy(() -> guiaService.emitirGRE(nuevaId))
                .hasMessageContaining("Serie inválida");

        var guia = guiaRepo.findByIdAndTenantId(nuevaId, TENANT_ID);
        assertThat(guia.get().getEstadoTributario()).isEqualTo(EstadoTributarioGRE.ERROR);

        // Sin movimientos de inventario para esta guía
        Integer movs = jdbc.queryForObject(
                "SELECT COUNT(*) FROM movimientos_inventario WHERE referencia = ?",
                Integer.class, "GRE-" + nuevaId);
        assertThat(movs).isEqualTo(0);
    }

    // ── IT-7: Confirmar recepción ───────────────────────────────────────────────

    @Test
    @Order(7)
    @DisplayName("IT-7: Confirmar recepción de guía emitida cambia estado logístico a RECIBIDA")
    void confirmarRecepcion_guiaEmitida_estadoRecibida() {
        // La guía de IT-4 ya está EMITIDA
        ConfirmarRecepcionRequest req = new ConfirmarRecepcionRequest();
        req.setObservaciones("Recibido en almacén central");

        GuiaRemisionResponse resp = guiaService.confirmarRecepcion(guiaBorradorId, req);

        assertThat(resp.getEstadoLogistico()).isEqualTo(EstadoLogisticoGRE.RECIBIDA);

        var guia = guiaRepo.findByIdAndTenantId(guiaBorradorId, TENANT_ID);
        assertThat(guia.get().getEstadoLogistico()).isEqualTo(EstadoLogisticoGRE.RECIBIDA);
    }

    // ── IT-8: Cancelar borrador ─────────────────────────────────────────────────

    @Test
    @Order(8)
    @DisplayName("IT-8: Cancelar borrador cambia estado a CANCELADA en DB")
    void cancelarBorrador_persiste_enDB() {
        GuiaRemisionResponse nueva = guiaService.crearBorrador(buildRequest("idem-integ-008"));
        Long nuevaId = nueva.getId();

        GuiaRemisionResponse resp = guiaService.cancelarBorrador(nuevaId);
        assertThat(resp.getEstadoTributario()).isEqualTo(EstadoTributarioGRE.CANCELADA);

        var guia = guiaRepo.findByIdAndTenantId(nuevaId, TENANT_ID);
        assertThat(guia.get().getEstadoTributario()).isEqualTo(EstadoTributarioGRE.CANCELADA);
    }

    // ── IT-9: Cancelar guía emitida bloqueado ──────────────────────────────────

    @Test
    @Order(9)
    @DisplayName("IT-9: Cancelar guía EMITIDA lanza BadRequestException")
    void cancelarEmitida_bloqueado() {
        // La guía principal ya fue emitida en IT-4
        assertThatThrownBy(() -> guiaService.cancelarBorrador(guiaBorradorId))
                .hasMessageContaining("BORRADOR");
    }

    // ── IT-10: Aislamiento de tenant ───────────────────────────────────────────

    @Test
    @Order(10)
    @DisplayName("IT-10: Guía de otro tenant no es visible para el tenant actual")
    void aislamiento_tenant_otroTenantNoVisible() {
        // Insertar tenant "otro-tenant" para satisfacer el FK de guias_remision
        jdbc.update("""
            INSERT INTO tenants (tenant_id, nombre, activo, ruc, created_at)
            VALUES ('otro-tenant', 'Otro Tenant', true, '20000000001', NOW())
            ON CONFLICT (tenant_id) DO NOTHING
            """);

        // Insertar guía de otro tenant directamente en DB
        jdbc.update("""
            INSERT INTO guias_remision (tenant_id, serie, correlativo, tipo_traslado, motivo_traslado_codigo,
                modalidad_transporte, fecha_traslado, estado_tributario, estado_logistico,
                idempotency_key, created_at, updated_at)
            VALUES ('otro-tenant', 'T001', 999, 'DELIVERY', '01',
                'PRIVADO', NOW(), 'BORRADOR', 'PENDIENTE', 'idem-otro-tenant', NOW(), NOW())
            """);

        Integer idOtroTenant = jdbc.queryForObject(
                "SELECT id FROM guias_remision WHERE idempotency_key = 'idem-otro-tenant'",
                Integer.class);

        assertThatThrownBy(() -> guiaService.obtener(idOtroTenant.longValue()))
                .isInstanceOf(com.stockflow.exception.ResourceNotFoundException.class);
    }

    // ── Helper ─────────────────────────────────────────────────────────────────

    private CrearGuiaRemisionRequest buildRequest(String iKey) {
        CrearGuiaRemisionRequest req = new CrearGuiaRemisionRequest();
        req.setIdempotencyKey(iKey);
        req.setTipoTraslado(TipoTrasladoGRE.DELIVERY);
        req.setMotivoTrasladoCodigo("01");
        req.setModalidadTransporte(ModalidadTransporteGRE.PRIVADO);
        req.setFechaTraslado(LocalDate.now().plusDays(1));
        req.setDestinatarioTipoDoc("6");
        req.setDestinatarioNumDoc("20100000001");
        req.setDestinatarioRazonSocial("Cliente Integración Test");
        req.setDestinatarioDireccion("Av. Test 123");

        GuiaRemisionItemDTO item = new GuiaRemisionItemDTO();
        item.setProductoId(9001L);
        item.setDescripcion("Producto Integ Test");
        item.setCantidad(BigDecimal.ONE);
        req.setItems(List.of(item));

        TransporteDTO td = new TransporteDTO();
        td.setDireccionPartida("Almacén Central");
        td.setDireccionLlegada("Tienda Norte");
        req.setTransporte(td);

        return req;
    }
}
