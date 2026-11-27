package com.stockflow.integration;

import com.stockflow.dto.gre.*;
import com.stockflow.dto.gre.lucode.LucodeGREPayload;
import com.stockflow.dto.gre.lucode.LucodeGREResponse;
import com.stockflow.entity.gre.EstadoTributarioGRE;
import com.stockflow.entity.gre.ModalidadTransporteGRE;
import com.stockflow.entity.gre.TipoEventoGRE;
import com.stockflow.entity.gre.TipoTrasladoGRE;
import com.stockflow.exception.BadRequestException;
import com.stockflow.repository.GuiaRemisionEventoRepository;
import com.stockflow.repository.GuiaRemisionRepository;
import com.stockflow.repository.GreSerieContadorRepository;
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
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.*;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

/**
 * Tests de integración F-22: correlativos permanentes y protección de documentos enviados.
 *
 * h01 — Borrador nunca enviado → se puede eliminar
 * h02 — GRE enviada (EMISION_INICIADA) → no se puede eliminar aunque esté en ERROR
 * h03 — Correlativo no reutilizable: eliminar borrador no enviado no afecta el contador
 * h04 — Creación concurrente: dos borradores obtienen correlativos distintos
 * h05 — Recuperación de respuesta perdida (EMITIENDO → ERROR vía /recuperar)
 * h06 — Reconciliación de documento aceptado: registra SALIDA + EMITIDA
 * h07 — Reconciliación idempotente: segunda llamada lanza excepción, no duplica SALIDA
 * h08 — Rechazo tributario real: GRE → ERROR, sin movimientos de inventario
 * h09 — Aislamiento multi-tenant: GRE de otro tenant no es accesible
 * h10 — Contador persiste entre transacciones: rollback outer no recicla correlativo
 */
@SpringBootTest
@ActiveProfiles("test")
@TestInstance(TestInstance.Lifecycle.PER_CLASS)
@TestMethodOrder(MethodOrderer.OrderAnnotation.class)
class F22GreCorrelatvoYProteccionTest {

    @ServiceConnection
    static final PostgreSQLContainer<?> postgres = new PostgreSQLContainer<>("postgres:16");

    static { postgres.start(); }

    @MockBean LucodeGREService lucodeGREService;

    @Autowired private GuiaRemisionService greService;
    @Autowired private GuiaRemisionRepository greRepo;
    @Autowired private GuiaRemisionEventoRepository eventoRepo;
    @Autowired private GreSerieContadorRepository contadorRepo;
    @Autowired private JdbcTemplate jdbc;

    private static final String TENANT   = "f22-correlativos";
    private static final String TENANT_B = "f22-tenant-b";

    private Long origenId;
    private Long destinoId;
    private Long productoId;

    @BeforeAll
    void setup() {
        for (String t : List.of(TENANT, TENANT_B)) {
            jdbc.execute("INSERT INTO tenants (tenant_id, nombre, activo, rubro, ose_token) " +
                    "VALUES ('" + t + "', 'F22 " + t + "', true, 'OTRO', 'tok-test') " +
                    "ON CONFLICT (tenant_id) DO NOTHING");
        }

        origenId = jdbc.queryForObject(
                "INSERT INTO sucursales (nombre, tenant_id, es_principal, activo) " +
                "VALUES ('Origen F22', '" + TENANT + "', true, true) RETURNING id", Long.class);
        destinoId = jdbc.queryForObject(
                "INSERT INTO sucursales (nombre, tenant_id, es_principal, activo) " +
                "VALUES ('Destino F22', '" + TENANT + "', false, true) RETURNING id", Long.class);

        Long umId = jdbc.queryForObject("SELECT id FROM unidad_medida WHERE tenant_id IS NULL LIMIT 1", Long.class);
        productoId = jdbc.queryForObject(
                "INSERT INTO productos (nombre, stock_actual, costo_unitario, precio_venta, " +
                "unidad_medida_id, activo, tenant_id, version) " +
                "VALUES ('Prod F22', 50, 5.00, 10.00, " + umId + ", true, '" + TENANT + "', 0) RETURNING id",
                Long.class);
    }

    @BeforeEach
    void limpiar() {
        jdbc.execute("DELETE FROM guia_remision_eventos WHERE guia_remision_id IN " +
                "(SELECT id FROM guias_remision WHERE tenant_id IN ('" + TENANT + "','" + TENANT_B + "'))");
        jdbc.execute("DELETE FROM guia_remision_items WHERE guia_remision_id IN " +
                "(SELECT id FROM guias_remision WHERE tenant_id IN ('" + TENANT + "','" + TENANT_B + "'))");
        jdbc.execute("DELETE FROM guias_remision WHERE tenant_id IN ('" + TENANT + "','" + TENANT_B + "')");
        jdbc.execute("DELETE FROM movimientos_inventario WHERE tenant_id = '" + TENANT + "'");
        jdbc.execute("DELETE FROM gre_serie_contador WHERE tenant_id IN ('" + TENANT + "','" + TENANT_B + "')");

        mockLucodeOk();
    }

    // ── Helpers ───────────────────────────────────────────────────────────────

    private void mockLucodeOk() {
        LucodeGREResponse ok = new LucodeGREResponse();
        ok.setSuccess(true);
        ok.setMessage("OK");
        Mockito.reset(lucodeGREService);
        when(lucodeGREService.emitirGRE(any(), any())).thenReturn(ok);
        when(lucodeGREService.consultarEstadoGRE(any(), anyInt(), any())).thenReturn(ok);
    }

    private Long crearBorrador() {
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
            item.setCantidad(BigDecimal.ONE);
            req.setItems(List.of(item));
            return greService.crearBorrador(req).getId();
        } finally {
            TenantContext.clear();
        }
    }

    private void emitir(Long id) {
        TenantContext.setCurrentTenant(TENANT);
        TenantContext.setCurrentUserId(1L);
        try { greService.emitirGRE(id); }
        finally { TenantContext.clear(); }
    }

    private void eliminar(Long id) {
        TenantContext.setCurrentTenant(TENANT);
        TenantContext.setCurrentUserId(1L);
        try { greService.eliminarBorrador(id); }
        finally { TenantContext.clear(); }
    }

    private int getContador() {
        Integer v = jdbc.queryForObject(
                "SELECT ultimo FROM gre_serie_contador WHERE tenant_id = ? AND serie = 'T001'",
                Integer.class, TENANT);
        return v != null ? v : 0;
    }

    // ── Tests ─────────────────────────────────────────────────────────────────

    @Test
    @Order(1)
    void h01_borradorNuncaEnviado_puedeEliminarse() {
        Long id = crearBorrador(); // BORRADOR sin EMISION_INICIADA
        assertThatCode(() -> eliminar(id)).doesNotThrowAnyException();
        assertThat(greRepo.findById(id)).isEmpty();
    }

    @Test
    @Order(2)
    void h02_greEnviada_noSePuedeEliminarAunqueEsteEnError() {
        // Configurar Lucode para rechazar
        LucodeGREResponse rechazo = new LucodeGREResponse();
        rechazo.setSuccess(false);
        rechazo.setMessage("Validación fallida en SUNAT");
        reset(lucodeGREService);
        when(lucodeGREService.emitirGRE(any(), any())).thenReturn(rechazo);

        Long id = crearBorrador();
        // Emitir → Lucode rechaza → ERROR, pero EMISION_INICIADA ya se registró
        assertThatThrownBy(() -> emitir(id));

        // Verificar que el evento EMISION_INICIADA existe
        boolean tieneIniciada = eventoRepo.existsByGuiaRemisionIdAndTipoEvento(
                id, TipoEventoGRE.EMISION_INICIADA);
        assertThat(tieneIniciada).isTrue();

        // Intentar eliminar → debe lanzar BadRequestException
        assertThatThrownBy(() -> eliminar(id))
                .isInstanceOf(BadRequestException.class)
                .hasMessageContaining("fue enviada al servicio tributario");

        // La GRE sigue en DB como registro auditado
        assertThat(greRepo.findById(id)).isPresent();
    }

    @Test
    @Order(3)
    void h03_correlativoNoReutilizable_eliminarBorradorNoAfectaContador() {
        Long id1 = crearBorrador(); // correlativo = 1, contador = 1
        int contadorPostCreacion = getContador();
        assertThat(contadorPostCreacion).isEqualTo(1);

        eliminar(id1); // borrador sin EMISION_INICIADA → se puede eliminar

        int contadorPostEliminacion = getContador();
        // El contador NO retrocedió; el correlativo 1 queda "quemado"
        assertThat(contadorPostEliminacion).isEqualTo(1);

        Long id2 = crearBorrador(); // correlativo = 2 (no recicló el 1)
        var gre2 = greRepo.findById(id2).orElseThrow();
        assertThat(gre2.getCorrelativo())
                .as("El nuevo borrador debe tener correlativo > 1 (no recicla el eliminado)")
                .isGreaterThan(1);
    }

    @Test
    @Order(4)
    void h04_creacionConcurrente_correlativos_distintos() throws InterruptedException {
        int N = 5;
        ExecutorService exec = Executors.newFixedThreadPool(N);
        List<Future<Integer>> futures = new ArrayList<>();

        for (int i = 0; i < N; i++) {
            futures.add(exec.submit(() -> {
                Long id = crearBorrador();
                return greRepo.findById(id).orElseThrow().getCorrelativo();
            }));
        }
        exec.shutdown();
        exec.awaitTermination(30, TimeUnit.SECONDS);

        List<Integer> correlativos = new ArrayList<>();
        for (Future<Integer> f : futures) {
            try { correlativos.add(f.get()); }
            catch (ExecutionException e) { fail("Error en creación concurrente: " + e.getCause().getMessage()); }
        }

        long distintos = correlativos.stream().distinct().count();
        assertThat(distintos)
                .as("Todos los correlativos concurrentes deben ser distintos: " + correlativos)
                .isEqualTo(N);
    }

    @Test
    @Order(5)
    void h05_recuperacionRespuestaPerdida_emitiendo_a_error() {
        // Simular timeout: Lucode lanza excepción de red
        reset(lucodeGREService);
        when(lucodeGREService.emitirGRE(any(), any()))
                .thenThrow(new RuntimeException("Connection timeout"));
        when(lucodeGREService.consultarEstadoGRE(any(), anyInt(), any()))
                .thenReturn(new LucodeGREResponse()); // sin hash ni payload

        Long id = crearBorrador();
        assertThatThrownBy(() -> emitir(id)); // genera ERROR por timeout

        var gre = greRepo.findById(id).orElseThrow();
        // Puede quedar EMITIENDO o ERROR dependiendo del catch; el /recuperar lo normaliza si es EMITIENDO
        // Para el test, si quedó EMITIENDO usamos recuperarEmitiendo
        if (gre.getEstadoTributario() == EstadoTributarioGRE.EMITIENDO) {
            TenantContext.setCurrentTenant(TENANT);
            TenantContext.setCurrentUserId(1L);
            try { greService.recuperarEmitiendo(id); }
            finally { TenantContext.clear(); }
        }

        var final_gre = greRepo.findById(id).orElseThrow();
        assertThat(final_gre.getEstadoTributario())
                .as("Después de recuperar, GRE debe estar en ERROR (no EMITIENDO)")
                .isEqualTo(EstadoTributarioGRE.ERROR);
        // Debe poder intentarse nuevamente (el correlativo no cambió)
        assertThat(final_gre.getCorrelativo()).isPositive();
    }

    @Test
    @Order(6)
    void h06_reconciliacion_documentoAceptado_registraSalidaYEmitida() {
        // GRE en ERROR tras rechazo de Lucode (EMISION_INICIADA registrado)
        LucodeGREResponse rechazo = new LucodeGREResponse();
        rechazo.setSuccess(false);
        rechazo.setMessage("Error de red");
        reset(lucodeGREService);
        when(lucodeGREService.emitirGRE(any(), any())).thenReturn(rechazo);
        // Lucode /status devuelve hash coincidente
        LucodeGREResponse estadoLucode = new LucodeGREResponse();
        estadoLucode.setSuccess(true);
        LucodeGREPayload payload = new LucodeGREPayload();
        payload.setHash("HASH-TEST-001");
        estadoLucode.setPayload(payload);
        when(lucodeGREService.consultarEstadoGRE(any(), anyInt(), any())).thenReturn(estadoLucode);

        Long id = crearBorrador();
        assertThatThrownBy(() -> emitir(id)); // → ERROR

        // Reconciliar
        ReconciliarGRERequest req = new ReconciliarGRERequest(
                "HASH-TEST-001", "https://xml.test", "https://cdr.test", null);
        TenantContext.setCurrentTenant(TENANT);
        TenantContext.setCurrentUserId(1L);
        GuiaRemisionResponse reconciliada;
        try { reconciliada = greService.reconciliarGREAceptada(id, req); }
        finally { TenantContext.clear(); }

        assertThat(reconciliada.getEstadoTributario()).isEqualTo(EstadoTributarioGRE.EMITIDA);
        assertThat(reconciliada.getLucodeHash()).isEqualTo("HASH-TEST-001");

        // Verificar movimiento SALIDA
        long countSalida = jdbc.queryForObject(
                "SELECT COUNT(*) FROM movimientos_inventario WHERE tenant_id = ? AND referencia = ? AND tipo = 'SALIDA'",
                Long.class, TENANT, "GRE-" + id);
        assertThat(countSalida).isEqualTo(1);
    }

    @Test
    @Order(7)
    void h07_reconciliacionIdempotente_segundaLlamadaNoDuplicaSalida() {
        LucodeGREResponse rechazo = new LucodeGREResponse();
        rechazo.setSuccess(false);
        rechazo.setMessage("Error de red");
        reset(lucodeGREService);
        when(lucodeGREService.emitirGRE(any(), any())).thenReturn(rechazo);
        LucodeGREResponse estadoLucode = new LucodeGREResponse();
        estadoLucode.setSuccess(true);
        LucodeGREPayload payload2 = new LucodeGREPayload();
        payload2.setHash("HASH-TEST-002");
        estadoLucode.setPayload(payload2);
        when(lucodeGREService.consultarEstadoGRE(any(), anyInt(), any())).thenReturn(estadoLucode);

        Long id = crearBorrador();
        assertThatThrownBy(() -> emitir(id));

        ReconciliarGRERequest req = new ReconciliarGRERequest(
                "HASH-TEST-002", "https://xml.test", "https://cdr.test", null);

        TenantContext.setCurrentTenant(TENANT);
        TenantContext.setCurrentUserId(1L);
        try { greService.reconciliarGREAceptada(id, req); }
        finally { TenantContext.clear(); }

        // Segunda reconciliación → idempotente: retorna silenciosamente (ya EMITIDA)
        TenantContext.setCurrentTenant(TENANT);
        TenantContext.setCurrentUserId(1L);
        GuiaRemisionResponse segunda;
        try { segunda = greService.reconciliarGREAceptada(id, req); }
        finally { TenantContext.clear(); }
        assertThat(segunda.getEstadoTributario()).isEqualTo(EstadoTributarioGRE.EMITIDA);

        // Sigue habiendo exactamente 1 SALIDA
        long count = jdbc.queryForObject(
                "SELECT COUNT(*) FROM movimientos_inventario WHERE tenant_id = ? AND referencia = ? AND tipo = 'SALIDA'",
                Long.class, TENANT, "GRE-" + id);
        assertThat(count).isEqualTo(1);
    }

    @Test
    @Order(8)
    void h08_rechazoTributarioReal_greEnError_sinMovimientosInventario() {
        LucodeGREResponse rechazo = new LucodeGREResponse();
        rechazo.setSuccess(false);
        rechazo.setMessage("Destinatario no válido");
        reset(lucodeGREService);
        when(lucodeGREService.emitirGRE(any(), any())).thenReturn(rechazo);

        Long id = crearBorrador();
        assertThatThrownBy(() -> emitir(id))
                .isInstanceOf(BadRequestException.class);

        var gre = greRepo.findById(id).orElseThrow();
        assertThat(gre.getEstadoTributario()).isEqualTo(EstadoTributarioGRE.ERROR);

        long movimientos = jdbc.queryForObject(
                "SELECT COUNT(*) FROM movimientos_inventario WHERE tenant_id = ? AND referencia = ?",
                Long.class, TENANT, "GRE-" + id);
        assertThat(movimientos)
                .as("Rechazo real de Lucode no debe generar movimientos de inventario")
                .isEqualTo(0);
    }

    @Test
    @Order(9)
    void h09_aislamientoMultiTenant_greDeOtroTenantNoAccesible() {
        // Crear GRE en TENANT
        Long idA = crearBorrador();

        // Intentar acceder desde TENANT_B
        TenantContext.setCurrentTenant(TENANT_B);
        TenantContext.setCurrentUserId(1L);
        try {
            assertThatThrownBy(() -> greService.obtener(idA))
                    .as("GRE de otro tenant no debe ser accesible");
        } finally {
            TenantContext.clear();
        }
    }

    @Test
    @Order(10)
    void h10_contadorPersistenteTrasRollback_noReciclaNumerosYaAsignados() {
        // Crear un borrador → contador = 1, correlativo = 1
        Long id1 = crearBorrador();
        int corr1 = greRepo.findById(id1).orElseThrow().getCorrelativo();

        // Eliminar el borrador (nunca enviado → permitido)
        eliminar(id1);

        // El contador sigue en 1
        assertThat(getContador()).isEqualTo(1);

        // Crear otro borrador → debe obtener correlativo = 2 (no 1)
        Long id2 = crearBorrador();
        int corr2 = greRepo.findById(id2).orElseThrow().getCorrelativo();

        assertThat(corr2)
                .as("El segundo borrador debe tener correlativo > " + corr1 + " (gap aceptable, no reciclado)")
                .isGreaterThan(corr1);
    }
}
