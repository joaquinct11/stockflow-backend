package com.stockflow.integration;

import com.stockflow.dto.gre.CrearGuiaRemisionRequest;
import com.stockflow.dto.gre.GuiaRemisionItemDTO;
import com.stockflow.dto.gre.lucode.LucodeGREResponse;
import com.stockflow.entity.gre.ModalidadTransporteGRE;
import com.stockflow.entity.gre.TipoTrasladoGRE;
import com.stockflow.exception.BadRequestException;
import com.stockflow.repository.GuiaRemisionRepository;
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
 * Tests de integración para F-16: validación de stock en emisión GRE DELIVERY legacy.
 *
 * Cubre escenarios NO incluidos en F-14 (que solo prueba SUCURSALES con PSS):
 *
 * f01 — DELIVERY legacy, stock global = 0: BadRequestException, Lucode no llamado
 * f02 — DELIVERY legacy, stock insuficiente: BadRequestException, Lucode no llamado
 * f03 — DELIVERY legacy, stock exactamente suficiente: emisión pasa, Lucode llamado
 * f04 — DELIVERY con ventaId: validarStockParaEmision omitida aunque stock sea 0
 */
@SpringBootTest
@ActiveProfiles("test")
@TestInstance(TestInstance.Lifecycle.PER_CLASS)
@TestMethodOrder(MethodOrderer.OrderAnnotation.class)
class F16StockValidacionGRETest {

    @ServiceConnection
    static final PostgreSQLContainer<?> postgres = new PostgreSQLContainer<>("postgres:16");

    static {
        postgres.start();
    }

    @MockBean
    LucodeGREService lucodeGREService;

    @Autowired private GuiaRemisionService greService;
    @Autowired private GuiaRemisionRepository greRepo;
    @Autowired private JdbcTemplate jdbc;

    private static final String TENANT = "f16-stock-a";

    private Long productoId;
    private Long productoConVentaId;
    private Long ventaF04Id;

    @BeforeAll
    void setup() {
        jdbc.execute("INSERT INTO tenants (tenant_id, nombre, activo, rubro) " +
                "VALUES ('" + TENANT + "', 'F16 Test', true, 'OTRO') ON CONFLICT (tenant_id) DO NOTHING");
        jdbc.execute("UPDATE tenants SET ubigeo_despacho = '150101', direccion_despacho = 'Av. Test 123, Lima', " +
                "ose_token = 'tok-f16-test' WHERE tenant_id = '" + TENANT + "'");

        Long umId = jdbc.queryForObject(
                "SELECT id FROM unidad_medida WHERE tenant_id IS NULL LIMIT 1", Long.class);

        productoId = jdbc.queryForObject(
                "INSERT INTO productos (nombre, stock_actual, costo_unitario, precio_venta, " +
                "unidad_medida_id, activo, tenant_id, version) " +
                "VALUES ('F16 Producto', 10, 5.00, 10.00, " + umId + ", true, '" + TENANT + "', 0) RETURNING id",
                Long.class);

        productoConVentaId = jdbc.queryForObject(
                "INSERT INTO productos (nombre, stock_actual, costo_unitario, precio_venta, " +
                "unidad_medida_id, activo, tenant_id, version) " +
                "VALUES ('F16 ProdVenta', 0, 5.00, 10.00, " + umId + ", true, '" + TENANT + "', 0) RETURNING id",
                Long.class);

        ventaF04Id = jdbc.queryForObject(
                "INSERT INTO ventas (total, estado, tenant_id, created_at) " +
                "VALUES (10.00, 'COMPLETADA', '" + TENANT + "', NOW()) RETURNING id",
                Long.class);
    }

    @BeforeEach
    void limpiarYConfigurarMock() {
        jdbc.execute("DELETE FROM guia_remision_eventos WHERE tenant_id = '" + TENANT + "'");
        jdbc.execute("DELETE FROM guias_remision WHERE tenant_id = '" + TENANT + "'");

        LucodeGREResponse successResp = new LucodeGREResponse();
        successResp.setSuccess(true);
        successResp.setMessage("OK mock");
        Mockito.reset(lucodeGREService);
        when(lucodeGREService.emitirGRE(any(), any())).thenReturn(successResp);
    }

    // ─── Helpers ─────────────────────────────────────────────────────────────

    private void setStockGlobal(Long prodId, int stock) {
        jdbc.update("UPDATE productos SET stock_actual = ? WHERE id = ?", stock, prodId);
    }

    private Long crearBorradorDelivery(Long prodId, int cantidad) {
        return crearBorradorDelivery(prodId, cantidad, null);
    }

    private Long crearBorradorDelivery(Long prodId, int cantidad, Long ventaId) {
        TenantContext.setCurrentTenant(TENANT);
        TenantContext.setCurrentUserId(1L);
        try {
            CrearGuiaRemisionRequest req = new CrearGuiaRemisionRequest();
            req.setTipoTraslado(TipoTrasladoGRE.DELIVERY);
            req.setMotivoTrasladoCodigo("01");
            req.setModalidadTransporte(ModalidadTransporteGRE.PRIVADO);
            req.setFechaTraslado(LocalDate.now());
            req.setDestinatarioRazonSocial("Cliente F16");
            req.setVentaId(ventaId);
            GuiaRemisionItemDTO item = new GuiaRemisionItemDTO();
            item.setProductoId(prodId);
            item.setCantidad(new BigDecimal(cantidad));
            req.setItems(List.of(item));
            return greService.crearBorrador(req).getId();
        } finally {
            TenantContext.clear();
        }
    }

    private void emitir(Long guiaId) {
        TenantContext.setCurrentTenant(TENANT);
        TenantContext.setCurrentUserId(1L);
        try {
            greService.emitirGRE(guiaId);
        } finally {
            TenantContext.clear();
        }
    }

    // ─── Tests ───────────────────────────────────────────────────────────────

    @Test
    @Order(1)
    void f01_deliveryLegacy_stockCero_lanzaBadRequest_lucodeNoLlamado() {
        setStockGlobal(productoId, 0);

        Long guiaId = crearBorradorDelivery(productoId, 1);

        assertThatThrownBy(() -> emitir(guiaId))
                .isInstanceOf(BadRequestException.class)
                .hasMessageContaining("stock");

        verify(lucodeGREService, never()).emitirGRE(any(), any());
    }

    @Test
    @Order(2)
    void f02_deliveryLegacy_stockInsuficiente_lanzaBadRequest_lucodeNoLlamado() {
        setStockGlobal(productoId, 3);

        Long guiaId = crearBorradorDelivery(productoId, 5); // pide 5, solo hay 3

        assertThatThrownBy(() -> emitir(guiaId))
                .isInstanceOf(BadRequestException.class)
                .hasMessageContaining("stock");

        verify(lucodeGREService, never()).emitirGRE(any(), any());
    }

    @Test
    @Order(3)
    void f03_deliveryLegacy_stockExactamenteSuficiente_emiteCorrectamente() {
        setStockGlobal(productoId, 5);

        Long guiaId = crearBorradorDelivery(productoId, 5); // pide exactamente el stock

        assertThatCode(() -> emitir(guiaId)).doesNotThrowAnyException();

        verify(lucodeGREService, times(1)).emitirGRE(any(), any());
    }

    @Test
    @Order(4)
    void f04_deliveryConVentaId_omiteValidacionStock_lucodeLlamado() {
        // productoConVentaId tiene stock_actual=0, pero la GRE lleva ventaId
        // → validarStockParaEmision se omite (F-15 ya tomó el stock al crear la venta)
        Long guiaId = crearBorradorDelivery(productoConVentaId, 2, ventaF04Id);

        assertThatCode(() -> emitir(guiaId)).doesNotThrowAnyException();

        verify(lucodeGREService, times(1)).emitirGRE(any(), any());
    }
}
