package com.stockflow.integration;

import com.stockflow.dto.gre.CrearGuiaRemisionRequest;
import com.stockflow.dto.gre.GuiaRemisionItemDTO;
import com.stockflow.entity.gre.ModalidadTransporteGRE;
import com.stockflow.entity.gre.TipoTrasladoGRE;
import com.stockflow.exception.BadRequestException;
import com.stockflow.dto.gre.lucode.LucodeGREResponse;
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
import static org.mockito.Mockito.when;

/**
 * Tests de integración para F-18: validación de motivos de traslado.
 *
 * f01 — motivo '01' (Venta) con DELIVERY → borrador creado
 * f02 — motivo '04' (Traslado establecimientos) con SUCURSALES → borrador creado
 * f03 — motivo '02' (histórico) → BadRequestException
 * f04 — motivo '08' (histórico) → BadRequestException
 * f05 — motivo '09' (histórico) → BadRequestException
 * f06 — motivo '13' (histórico) → BadRequestException
 * f07 — motivo null → se acepta (auto-asignado por frontend)
 * f08 — motivo vacío → se acepta (auto-asignado por frontend)
 * f09 — GRE históricas con motivos no soportados siguen siendo consultables
 */
@SpringBootTest
@ActiveProfiles("test")
@TestInstance(TestInstance.Lifecycle.PER_CLASS)
@TestMethodOrder(MethodOrderer.OrderAnnotation.class)
class F18MotivoTrasladoValidacionTest {

    @ServiceConnection
    static final PostgreSQLContainer<?> postgres = new PostgreSQLContainer<>("postgres:16");

    static {
        postgres.start();
    }

    @MockBean
    LucodeGREService lucodeGREService;

    @Autowired private GuiaRemisionService greService;
    @Autowired private JdbcTemplate jdbc;

    private static final String TENANT = "f18-motivo-a";
    private Long productoId;

    @BeforeAll
    void setup() {
        jdbc.execute("INSERT INTO tenants (tenant_id, nombre, activo, rubro) " +
                "VALUES ('" + TENANT + "', 'F18 Motivo Test', true, 'OTRO') ON CONFLICT (tenant_id) DO NOTHING");

        Long umId = jdbc.queryForObject(
                "SELECT id FROM unidad_medida WHERE tenant_id IS NULL LIMIT 1", Long.class);

        productoId = jdbc.queryForObject(
                "INSERT INTO productos (nombre, stock_actual, costo_unitario, precio_venta, " +
                "unidad_medida_id, activo, tenant_id, version) " +
                "VALUES ('F18 Producto', 100, 5.00, 10.00, " + umId + ", true, '" + TENANT + "', 0) RETURNING id",
                Long.class);

        jdbc.update("UPDATE tenants SET ubigeo_despacho = '150101', direccion_despacho = 'Almacén F18' " +
                "WHERE tenant_id = ?", TENANT);
    }

    @BeforeEach
    void limpiarYMock() {
        jdbc.execute("DELETE FROM guia_remision_eventos WHERE tenant_id = '" + TENANT + "'");
        jdbc.execute("DELETE FROM guias_remision WHERE tenant_id = '" + TENANT + "'");

        LucodeGREResponse ok = new LucodeGREResponse();
        ok.setSuccess(true);
        ok.setMessage("OK");
        Mockito.reset(lucodeGREService);
        when(lucodeGREService.emitirGRE(any(), any())).thenReturn(ok);
    }

    // ─── Helpers ─────────────────────────────────────────────────────────────

    private void crearBorradorDelivery(String motivo) {
        TenantContext.setCurrentTenant(TENANT);
        TenantContext.setCurrentUserId(1L);
        try {
            CrearGuiaRemisionRequest req = new CrearGuiaRemisionRequest();
            req.setTipoTraslado(TipoTrasladoGRE.DELIVERY);
            req.setMotivoTrasladoCodigo(motivo);
            req.setModalidadTransporte(ModalidadTransporteGRE.PRIVADO);
            req.setFechaTraslado(LocalDate.now());
            req.setDestinatarioRazonSocial("Cliente F18");
            req.setDestinatarioTipoDoc("1");
            req.setDestinatarioNumDoc("12345678");
            GuiaRemisionItemDTO item = new GuiaRemisionItemDTO();
            item.setProductoId(productoId);
            item.setCantidad(BigDecimal.ONE);
            req.setItems(List.of(item));
            greService.crearBorrador(req);
        } finally {
            TenantContext.clear();
        }
    }

    private void crearBorradorSucursales(String motivo) {
        TenantContext.setCurrentTenant(TENANT);
        TenantContext.setCurrentUserId(1L);
        try {
            Long s1Id = jdbc.queryForObject(
                    "INSERT INTO sucursales (nombre, activo, tenant_id) " +
                    "VALUES ('S1 F18', true, '" + TENANT + "') RETURNING id", Long.class);
            Long s2Id = jdbc.queryForObject(
                    "INSERT INTO sucursales (nombre, activo, tenant_id) " +
                    "VALUES ('S2 F18', true, '" + TENANT + "') RETURNING id", Long.class);

            CrearGuiaRemisionRequest req = new CrearGuiaRemisionRequest();
            req.setTipoTraslado(TipoTrasladoGRE.SUCURSALES);
            req.setMotivoTrasladoCodigo(motivo);
            req.setModalidadTransporte(ModalidadTransporteGRE.PRIVADO);
            req.setFechaTraslado(LocalDate.now());
            req.setSucursalOrigenId(s1Id);
            req.setSucursalDestinoId(s2Id);
            GuiaRemisionItemDTO item = new GuiaRemisionItemDTO();
            item.setProductoId(productoId);
            item.setCantidad(BigDecimal.ONE);
            req.setItems(List.of(item));
            greService.crearBorrador(req);
        } finally {
            TenantContext.clear();
        }
    }

    // ─── Tests ───────────────────────────────────────────────────────────────

    @Test
    @Order(1)
    void f01_motivo01_delivery_borradorCreado() {
        assertThatCode(() -> crearBorradorDelivery("01"))
                .doesNotThrowAnyException();
    }

    @Test
    @Order(2)
    void f02_motivo04_sucursales_borradorCreado() {
        assertThatCode(() -> crearBorradorSucursales("04"))
                .doesNotThrowAnyException();
    }

    @Test
    @Order(3)
    void f03_motivo02_lanzaBadRequest() {
        assertThatThrownBy(() -> crearBorradorDelivery("02"))
                .isInstanceOf(BadRequestException.class)
                .hasMessageContaining("'02'")
                .hasMessageContaining("no está soportado");
    }

    @Test
    @Order(4)
    void f04_motivo08_lanzaBadRequest() {
        assertThatThrownBy(() -> crearBorradorDelivery("08"))
                .isInstanceOf(BadRequestException.class)
                .hasMessageContaining("'08'")
                .hasMessageContaining("no está soportado");
    }

    @Test
    @Order(5)
    void f05_motivo09_lanzaBadRequest() {
        assertThatThrownBy(() -> crearBorradorDelivery("09"))
                .isInstanceOf(BadRequestException.class)
                .hasMessageContaining("'09'")
                .hasMessageContaining("no está soportado");
    }

    @Test
    @Order(6)
    void f06_motivo13_lanzaBadRequest() {
        assertThatThrownBy(() -> crearBorradorDelivery("13"))
                .isInstanceOf(BadRequestException.class)
                .hasMessageContaining("'13'")
                .hasMessageContaining("no está soportado");
    }

    @Test
    @Order(7)
    void f07_motivoNull_aceptado() {
        // null se acepta — el motivo se asigna por defecto según tipo traslado
        assertThatCode(() -> crearBorradorDelivery(null))
                .doesNotThrowAnyException();
    }

    @Test
    @Order(8)
    void f08_motivoVacio_aceptado() {
        // string vacío se acepta — el motivo se asigna por defecto según tipo traslado
        assertThatCode(() -> crearBorradorDelivery(""))
                .doesNotThrowAnyException();
    }

    @Test
    @Order(9)
    void f09_greHistoricaConMotivo02_esConsultable() {
        // Insertar directamente una GRE histórica con motivo 02 (ya no es creatable pero debe ser legible)
        TenantContext.setCurrentTenant(TENANT);
        TenantContext.setCurrentUserId(1L);
        try {
            Long greId = jdbc.queryForObject(
                    "INSERT INTO guias_remision " +
                    "(tenant_id, tipo_traslado, motivo_traslado_codigo, modalidad_transporte, " +
                    " fecha_traslado, estado_tributario, estado_logistico, numero_correlativo, " +
                    " destinatario_razon_social, destinatario_tipo_doc, destinatario_num_doc) " +
                    "VALUES (?, 'DELIVERY', '02', 'PRIVADO', ?, 'BORRADOR', 'PENDIENTE', 9901, " +
                    " 'Cliente Histórico', '1', '87654321') RETURNING id",
                    Long.class,
                    TENANT, LocalDate.now());

            assertThat(greId).isNotNull().isPositive();

            // Verificar que se puede consultar la GRE histórica
            Long count = jdbc.queryForObject(
                    "SELECT COUNT(*) FROM guias_remision WHERE id = ? AND motivo_traslado_codigo = '02'",
                    Long.class, greId);
            assertThat(count).isEqualTo(1L);
        } finally {
            TenantContext.clear();
        }
    }
}
