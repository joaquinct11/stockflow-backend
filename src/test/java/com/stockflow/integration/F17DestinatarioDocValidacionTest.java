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
 * Tests de integración para F-17: validación de tipo y número de documento
 * del destinatario al crear un borrador GRE DELIVERY.
 *
 * f01 — DNI válido (8 dígitos) → borrador creado
 * f02 — DNI con 7 dígitos → BadRequestException
 * f03 — RUC válido (20467534026) → borrador creado
 * f04 — RUC con DV incorrecto (20467534025) → BadRequestException
 * f05 — RUC con 10 dígitos → BadRequestException
 * f06 — Sin documento ('-') con numDoc vacío → borrador creado
 * f07 — Carnet ext. con valor alfanumérico → borrador creado
 * f08 — SUCURSALES no valida documento del destinatario
 */
@SpringBootTest
@ActiveProfiles("test")
@TestInstance(TestInstance.Lifecycle.PER_CLASS)
@TestMethodOrder(MethodOrderer.OrderAnnotation.class)
class F17DestinatarioDocValidacionTest {

    @ServiceConnection
    static final PostgreSQLContainer<?> postgres = new PostgreSQLContainer<>("postgres:16");

    static {
        postgres.start();
    }

    @MockBean
    LucodeGREService lucodeGREService;

    @Autowired private GuiaRemisionService greService;
    @Autowired private JdbcTemplate jdbc;

    private static final String TENANT = "f17-doc-a";
    private Long productoId;

    @BeforeAll
    void setup() {
        jdbc.execute("INSERT INTO tenants (tenant_id, nombre, activo, rubro) " +
                "VALUES ('" + TENANT + "', 'F17 Test', true, 'OTRO') ON CONFLICT (tenant_id) DO NOTHING");

        Long umId = jdbc.queryForObject(
                "SELECT id FROM unidad_medida WHERE tenant_id IS NULL LIMIT 1", Long.class);

        productoId = jdbc.queryForObject(
                "INSERT INTO productos (nombre, stock_actual, costo_unitario, precio_venta, " +
                "unidad_medida_id, activo, tenant_id, version) " +
                "VALUES ('F17 Producto', 100, 5.00, 10.00, " + umId + ", true, '" + TENANT + "', 0) RETURNING id",
                Long.class);

        // Configurar punto de despacho para que crearBorrador no falle por F-10
        jdbc.update("UPDATE tenants SET ubigeo_despacho = '150101', direccion_despacho = 'Almacén F17' " +
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

    // ─── Helper ──────────────────────────────────────────────────────────────

    private void crearBorradorDelivery(String tipoDoc, String numDoc) {
        TenantContext.setCurrentTenant(TENANT);
        TenantContext.setCurrentUserId(1L);
        try {
            CrearGuiaRemisionRequest req = new CrearGuiaRemisionRequest();
            req.setTipoTraslado(TipoTrasladoGRE.DELIVERY);
            req.setMotivoTrasladoCodigo("01");
            req.setModalidadTransporte(ModalidadTransporteGRE.PRIVADO);
            req.setFechaTraslado(LocalDate.now());
            req.setDestinatarioRazonSocial("Cliente F17");
            req.setDestinatarioTipoDoc(tipoDoc);
            req.setDestinatarioNumDoc(numDoc);
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
    void f01_dniValido_borradorCreado() {
        assertThatCode(() -> crearBorradorDelivery("1", "12345678"))
                .doesNotThrowAnyException();
    }

    @Test
    @Order(2)
    void f02_dniSietedigitos_lanzaBadRequest() {
        assertThatThrownBy(() -> crearBorradorDelivery("1", "1234567"))
                .isInstanceOf(BadRequestException.class)
                .hasMessageContaining("8 dígitos");
    }

    @Test
    @Order(3)
    void f03_rucValido_borradorCreado() {
        // 20467534026 — RUC de Claro Perú (dígito verificador correcto)
        assertThatCode(() -> crearBorradorDelivery("6", "20467534026"))
                .doesNotThrowAnyException();
    }

    @Test
    @Order(4)
    void f04_rucDvIncorrecto_ahoraAceptado_f18_1() {
        // F-18.1: se eliminó la verificación del dígito verificador.
        // 20467534025 (DV=5, pero el correcto es 6) ahora es válido por formato.
        assertThatCode(() -> crearBorradorDelivery("6", "20467534025"))
                .doesNotThrowAnyException();
    }

    @Test
    @Order(5)
    void f05_rucDiezDigitos_lanzaBadRequest() {
        assertThatThrownBy(() -> crearBorradorDelivery("6", "2046753402"))
                .isInstanceOf(BadRequestException.class)
                .hasMessageContaining("11 dígitos");
    }

    @Test
    @Order(6)
    void f06_sinDocumento_numDocVacio_borradorCreado() {
        assertThatCode(() -> crearBorradorDelivery("-", ""))
                .doesNotThrowAnyException();
    }

    @Test
    @Order(7)
    void f07_carnetExtranjeria_valorAlfanumerico_borradorCreado() {
        assertThatCode(() -> crearBorradorDelivery("4", "C01234567"))
                .doesNotThrowAnyException();
    }

    @Test
    @Order(8)
    void f08_sucursales_noValidaDocDestinatario() {
        // SUCURSALES no lleva destinatario, la validación no debe ejecutarse
        TenantContext.setCurrentTenant(TENANT);
        TenantContext.setCurrentUserId(1L);
        try {
            // Crear dos sucursales para satisfacer la validación de SUCURSALES
            Long umId = jdbc.queryForObject(
                    "SELECT id FROM unidad_medida WHERE tenant_id IS NULL LIMIT 1", Long.class);
            Long s1Id = jdbc.queryForObject(
                    "INSERT INTO sucursales (nombre, activo, tenant_id) " +
                    "VALUES ('S1 F17', true, '" + TENANT + "') RETURNING id", Long.class);
            Long s2Id = jdbc.queryForObject(
                    "INSERT INTO sucursales (nombre, activo, tenant_id) " +
                    "VALUES ('S2 F17', true, '" + TENANT + "') RETURNING id", Long.class);

            CrearGuiaRemisionRequest req = new CrearGuiaRemisionRequest();
            req.setTipoTraslado(TipoTrasladoGRE.SUCURSALES);
            req.setMotivoTrasladoCodigo("04");
            req.setModalidadTransporte(ModalidadTransporteGRE.PRIVADO);
            req.setFechaTraslado(LocalDate.now());
            req.setSucursalOrigenId(s1Id);
            req.setSucursalDestinoId(s2Id);
            req.setDestinatarioTipoDoc("1");
            req.setDestinatarioNumDoc("INVALIDO"); // si validara, fallaría
            GuiaRemisionItemDTO item = new GuiaRemisionItemDTO();
            item.setProductoId(productoId);
            item.setCantidad(BigDecimal.ONE);
            req.setItems(List.of(item));

            assertThatCode(() -> greService.crearBorrador(req))
                    .doesNotThrowAnyException();
        } finally {
            TenantContext.clear();
        }
    }
}
