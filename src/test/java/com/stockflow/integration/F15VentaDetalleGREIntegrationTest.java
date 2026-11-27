package com.stockflow.integration;

import com.stockflow.controller.VentaController;
import com.stockflow.dto.VentaDetalleGREDTO;
import com.stockflow.dto.gre.CrearGuiaRemisionRequest;
import com.stockflow.dto.gre.GuiaRemisionItemDTO;
import com.stockflow.dto.gre.TransporteDTO;
import com.stockflow.dto.gre.lucode.LucodeGREResponse;
import com.stockflow.entity.gre.ModalidadTransporteGRE;
import com.stockflow.entity.gre.TipoTrasladoGRE;
import com.stockflow.exception.BadRequestException;
import com.stockflow.exception.ResourceNotFoundException;
import com.stockflow.repository.VentaRepository;
import com.stockflow.service.GuiaRemisionService;
import com.stockflow.service.impl.LucodeGREService;
import com.stockflow.util.TenantContext;
import org.junit.jupiter.api.*;
import org.mockito.Mockito;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
import org.springframework.http.ResponseEntity;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.test.context.ActiveProfiles;
import org.testcontainers.containers.PostgreSQLContainer;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

/**
 * Tests de integración para F-15: vinculación de GRE DELIVERY motivo 01 con venta existente.
 *
 * f01 — detalle-para-gre con cliente: retorna nombre, tipoDoc (DNI→"1"), numDoc, dirección e items
 * f02 — detalle-para-gre sin cliente (consumidor final): clienteNombre="Sin nombre", tipoDoc y numDoc null
 * f03 — detalle-para-gre venta ANULADA: lanza BadRequestException
 * f04 — detalle-para-gre venta de otro tenant: lanza ResourceNotFoundException
 * f05 — crearBorrador con ventaId: guía queda con venta_id correcto en BD
 * f06 — crearBorrador con venta ANULADA: lanza BadRequestException
 * f07 — crearBorrador con venta de otro tenant: lanza ResourceNotFoundException
 * f08 — emitirGRE con venta vinculada: stock del producto NO se descuenta (POS ya lo hizo)
 * f09 — buscarVentas excluye ventas ANULADAS del resultado
 * f10 — buscarVentas encuentra ventas por nombre parcial de cliente
 * f11 — detalle-para-gre con cliente RUC: tipoDoc mapeado a "6"
 * f12 — detalle-para-gre items con factor > 1: factor se incluye correctamente
 */
@SpringBootTest
@ActiveProfiles("test")
@TestInstance(TestInstance.Lifecycle.PER_CLASS)
@TestMethodOrder(MethodOrderer.OrderAnnotation.class)
class F15VentaDetalleGREIntegrationTest {

    @ServiceConnection
    static final PostgreSQLContainer<?> postgres = new PostgreSQLContainer<>("postgres:16");

    static {
        postgres.start();
    }

    @MockBean
    LucodeGREService lucodeGREService;

    @Autowired private VentaController ventaController;
    @Autowired private GuiaRemisionService greService;
    @Autowired private VentaRepository ventaRepository;
    @Autowired private JdbcTemplate jdbc;

    private static final String TENANT_A = "f15-gre-a";
    private static final String TENANT_B = "f15-gre-b";

    private Long productoId;
    private Long productoId2;
    private Long clienteIdDni;
    private Long clienteIdRuc;
    private Long ventaConClienteDni;
    private Long ventaSinCliente;
    private Long ventaAnulada;
    private Long ventaTenantB;

    @BeforeAll
    void setup() {
        jdbc.execute("INSERT INTO tenants (tenant_id, nombre, activo, rubro) VALUES ('"
                + TENANT_A + "', 'F15 A', true, 'OTRO') ON CONFLICT (tenant_id) DO NOTHING");
        jdbc.execute("INSERT INTO tenants (tenant_id, nombre, activo, rubro) VALUES ('"
                + TENANT_B + "', 'F15 B', true, 'OTRO') ON CONFLICT (tenant_id) DO NOTHING");

        Long umId = jdbc.queryForObject(
                "SELECT id FROM unidad_medida WHERE tenant_id IS NULL LIMIT 1", Long.class);

        productoId = jdbc.queryForObject(
                "INSERT INTO productos (nombre, stock_actual, costo_unitario, precio_venta, " +
                "unidad_medida_id, activo, tenant_id, version) " +
                "VALUES ('F15-ProdA', 100, 5.00, 10.00, " + umId + ", true, '" + TENANT_A + "', 0) RETURNING id",
                Long.class);
        productoId2 = jdbc.queryForObject(
                "INSERT INTO productos (nombre, stock_actual, costo_unitario, precio_venta, " +
                "unidad_medida_id, activo, tenant_id, version) " +
                "VALUES ('F15-ProdB', 50, 3.00, 7.00, " + umId + ", true, '" + TENANT_A + "', 0) RETURNING id",
                Long.class);

        clienteIdDni = jdbc.queryForObject(
                "INSERT INTO clientes (nombre, tipo_documento, numero_documento, direccion, activo, tenant_id) " +
                "VALUES ('Juan García', 'DNI', '12345678', 'Av. Lima 123', true, '" + TENANT_A + "') RETURNING id",
                Long.class);
        clienteIdRuc = jdbc.queryForObject(
                "INSERT INTO clientes (nombre, tipo_documento, numero_documento, activo, tenant_id) " +
                "VALUES ('Empresa SAC', 'RUC', '20100000001', true, '" + TENANT_A + "') RETURNING id",
                Long.class);

        // Venta con cliente DNI, 2 productos distintos
        ventaConClienteDni = jdbc.queryForObject(
                "INSERT INTO ventas (total, estado, tenant_id, created_at, cliente_id) " +
                "VALUES (150.00, 'COMPLETADA', '" + TENANT_A + "', NOW(), " + clienteIdDni + ") RETURNING id",
                Long.class);
        jdbc.execute("INSERT INTO detalles_venta (venta_id, producto_id, cantidad, precio_unitario, subtotal, factor) " +
                "VALUES (" + ventaConClienteDni + ", " + productoId + ", 5, 10.00, 50.00, 1)");
        jdbc.execute("INSERT INTO detalles_venta (venta_id, producto_id, cantidad, precio_unitario, subtotal, factor) " +
                "VALUES (" + ventaConClienteDni + ", " + productoId2 + ", 10, 7.00, 70.00, 1)");

        // Venta sin cliente (consumidor final)
        ventaSinCliente = jdbc.queryForObject(
                "INSERT INTO ventas (total, estado, tenant_id, created_at) " +
                "VALUES (30.00, 'COMPLETADA', '" + TENANT_A + "', NOW()) RETURNING id",
                Long.class);
        jdbc.execute("INSERT INTO detalles_venta (venta_id, producto_id, cantidad, precio_unitario, subtotal, factor) " +
                "VALUES (" + ventaSinCliente + ", " + productoId + ", 3, 10.00, 30.00, 1)");

        // Venta ANULADA
        ventaAnulada = jdbc.queryForObject(
                "INSERT INTO ventas (total, estado, tenant_id, created_at) " +
                "VALUES (100.00, 'ANULADA', '" + TENANT_A + "', NOW()) RETURNING id",
                Long.class);
        jdbc.execute("INSERT INTO detalles_venta (venta_id, producto_id, cantidad, precio_unitario, subtotal, factor) " +
                "VALUES (" + ventaAnulada + ", " + productoId + ", 5, 10.00, 50.00, 1)");

        // Venta tenant B (aislamiento)
        Long prodBId = jdbc.queryForObject(
                "INSERT INTO productos (nombre, stock_actual, costo_unitario, precio_venta, " +
                "unidad_medida_id, activo, tenant_id, version) " +
                "VALUES ('F15-ProdB-B', 20, 2.00, 5.00, " + umId + ", true, '" + TENANT_B + "', 0) RETURNING id",
                Long.class);
        ventaTenantB = jdbc.queryForObject(
                "INSERT INTO ventas (total, estado, tenant_id, created_at) " +
                "VALUES (50.00, 'COMPLETADA', '" + TENANT_B + "', NOW()) RETURNING id",
                Long.class);
        jdbc.execute("INSERT INTO detalles_venta (venta_id, producto_id, cantidad, precio_unitario, subtotal, factor) " +
                "VALUES (" + ventaTenantB + ", " + prodBId + ", 5, 5.00, 25.00, 1)");
    }

    @BeforeEach
    void limpiarYConfigurarMock() {
        jdbc.execute("DELETE FROM guia_remision_eventos WHERE tenant_id IN ('" + TENANT_A + "', '" + TENANT_B + "')");
        jdbc.execute("DELETE FROM guias_remision WHERE tenant_id IN ('" + TENANT_A + "', '" + TENANT_B + "')");

        LucodeGREResponse successResp = new LucodeGREResponse();
        successResp.setSuccess(true);
        successResp.setMessage("OK mock");
        Mockito.reset(lucodeGREService);
        when(lucodeGREService.emitirGRE(any(), any())).thenReturn(successResp);
    }

    // ─── Helpers ─────────────────────────────────────────────────────────────

    private ResponseEntity<VentaDetalleGREDTO> detalleParaGRE(Long ventaId, String tenant) {
        var auth = new UsernamePasswordAuthenticationToken(
                "testuser", null, List.of(new SimpleGrantedAuthority("ROLE_ADMIN")));
        SecurityContextHolder.getContext().setAuthentication(auth);
        TenantContext.setCurrentTenant(tenant);
        try {
            return ventaController.detalleParaGRE(ventaId);
        } finally {
            TenantContext.clear();
            SecurityContextHolder.clearContext();
        }
    }

    private Long crearBorradorDeliveryConVenta(Long ventaId) {
        TenantContext.setCurrentTenant(TENANT_A);
        TenantContext.setCurrentUserId(1L);
        try {
            CrearGuiaRemisionRequest req = new CrearGuiaRemisionRequest();
            req.setTipoTraslado(TipoTrasladoGRE.DELIVERY);
            req.setMotivoTrasladoCodigo("01");
            req.setModalidadTransporte(ModalidadTransporteGRE.PRIVADO);
            req.setFechaTraslado(LocalDate.now());
            req.setVentaId(ventaId);
            GuiaRemisionItemDTO item = new GuiaRemisionItemDTO();
            item.setProductoId(productoId);
            item.setCantidad(new BigDecimal("5"));
            req.setItems(List.of(item));
            TransporteDTO t = new TransporteDTO();
            t.setDireccionPartida("Av. Partida 1");
            t.setDireccionLlegada("Av. Llegada 2");
            req.setTransporte(t);
            return greService.crearBorrador(req).getId();
        } finally {
            TenantContext.clear();
        }
    }

    private void emitirComoTenant(Long guiaId) {
        TenantContext.setCurrentTenant(TENANT_A);
        TenantContext.setCurrentUserId(1L);
        try {
            greService.emitirGRE(guiaId);
        } finally {
            TenantContext.clear();
        }
    }

    // ─── f01: detalle con cliente retorna cliente y todos sus items ───────────

    @Test
    @Order(1)
    void f01_detalleParaGRE_conCliente_retornaClienteYItems() {
        VentaDetalleGREDTO dto = detalleParaGRE(ventaConClienteDni, TENANT_A).getBody();

        assertThat(dto).isNotNull();
        assertThat(dto.getClienteNombre()).isEqualTo("Juan García");
        assertThat(dto.getClienteTipoDoc()).isEqualTo("1");        // DNI → código SUNAT "1"
        assertThat(dto.getClienteNumDoc()).isEqualTo("12345678");
        assertThat(dto.getClienteDireccion()).isEqualTo("Av. Lima 123");
        assertThat(dto.getItems()).hasSize(2);
        assertThat(dto.getItems()).anyMatch(i -> i.getProductoId().equals(productoId) && i.getCantidad() == 5);
        assertThat(dto.getItems()).anyMatch(i -> i.getProductoId().equals(productoId2) && i.getCantidad() == 10);
    }

    // ─── f02: venta sin cliente → "Sin nombre", tipoDoc y numDoc null ─────────

    @Test
    @Order(2)
    void f02_detalleParaGRE_sinCliente_retornaConsumidorFinal() {
        VentaDetalleGREDTO dto = detalleParaGRE(ventaSinCliente, TENANT_A).getBody();

        assertThat(dto).isNotNull();
        assertThat(dto.getClienteNombre()).isEqualTo("Sin nombre");
        assertThat(dto.getClienteTipoDoc()).isNull();
        assertThat(dto.getClienteNumDoc()).isNull();
        assertThat(dto.getItems()).hasSize(1);
        assertThat(dto.getItems().get(0).getProductoId()).isEqualTo(productoId);
        assertThat(dto.getItems().get(0).getCantidad()).isEqualTo(3);
    }

    // ─── f03: venta ANULADA → BadRequestException ─────────────────────────────

    @Test
    @Order(3)
    void f03_detalleParaGRE_ventaAnulada_lanzaBadRequest() {
        assertThatThrownBy(() -> detalleParaGRE(ventaAnulada, TENANT_A))
                .isInstanceOf(BadRequestException.class)
                .hasMessageContaining("anulada");
    }

    // ─── f04: venta de otro tenant → ResourceNotFoundException ───────────────

    @Test
    @Order(4)
    void f04_detalleParaGRE_ventaOtroTenant_lanzaNotFound() {
        assertThatThrownBy(() -> detalleParaGRE(ventaTenantB, TENANT_A))
                .isInstanceOf(ResourceNotFoundException.class);
    }

    // ─── f05: crearBorrador con ventaId: guía persiste con venta_id correcto ──

    @Test
    @Order(5)
    void f05_crearBorrador_conVenta_vinculaVentaId() {
        Long guiaId = crearBorradorDeliveryConVenta(ventaConClienteDni);

        Long ventaIdGuardado = jdbc.queryForObject(
                "SELECT venta_id FROM guias_remision WHERE id = " + guiaId, Long.class);
        assertThat(ventaIdGuardado).isEqualTo(ventaConClienteDni);
    }

    // ─── f06: crearBorrador con venta ANULADA → BadRequestException ───────────

    @Test
    @Order(6)
    void f06_crearBorrador_ventaAnulada_lanzaBadRequest() {
        assertThatThrownBy(() -> crearBorradorDeliveryConVenta(ventaAnulada))
                .isInstanceOf(BadRequestException.class)
                .hasMessageContaining("anulada");
    }

    // ─── f07: crearBorrador con venta de otro tenant → ResourceNotFoundException

    @Test
    @Order(7)
    void f07_crearBorrador_ventaOtroTenant_lanzaNotFound() {
        assertThatThrownBy(() -> crearBorradorDeliveryConVenta(ventaTenantB))
                .isInstanceOf(ResourceNotFoundException.class);
    }

    // ─── f08: emisión con venta vinculada NO descuenta stock ──────────────────

    @Test
    @Order(8)
    void f08_emitirGRE_conVenta_noDescontaStock() {
        int stockAntes = jdbc.queryForObject(
                "SELECT stock_actual FROM productos WHERE id = " + productoId, Integer.class);

        Long guiaId = crearBorradorDeliveryConVenta(ventaConClienteDni);
        emitirComoTenant(guiaId);

        int stockDespues = jdbc.queryForObject(
                "SELECT stock_actual FROM productos WHERE id = " + productoId, Integer.class);
        assertThat(stockDespues)
                .as("El stock no debe cambiar al emitir GRE con venta vinculada (el POS ya lo descontó)")
                .isEqualTo(stockAntes);
    }

    // ─── f09: buscarVentas excluye ventas ANULADAS ────────────────────────────

    @Test
    @Order(9)
    void f09_buscarVentas_excluyeAnuladas() {
        List<Object[]> rows = ventaRepository.buscarVentas(TENANT_A, String.valueOf(ventaAnulada));

        assertThat(rows).isEmpty();
    }

    // ─── f10: buscarVentas encuentra por nombre parcial de cliente ────────────

    @Test
    @Order(10)
    void f10_buscarVentas_porNombreClienteParcial() {
        List<Object[]> rows = ventaRepository.buscarVentas(TENANT_A, "Juan");

        assertThat(rows).isNotEmpty();
        boolean encontrado = rows.stream()
                .anyMatch(r -> ((Number) r[0]).longValue() == ventaConClienteDni);
        assertThat(encontrado)
                .as("buscarVentas debe encontrar la venta de 'Juan García' buscando por 'Juan'")
                .isTrue();
    }

    // ─── f11: tipo de documento RUC mapeado a código SUNAT "6" ───────────────

    @Test
    @Order(11)
    void f11_detalleParaGRE_tipoDocRUC_mapeadoA6() {
        Long ventaRuc = jdbc.queryForObject(
                "INSERT INTO ventas (total, estado, tenant_id, created_at, cliente_id) " +
                "VALUES (100.00, 'COMPLETADA', '" + TENANT_A + "', NOW(), " + clienteIdRuc + ") RETURNING id",
                Long.class);
        jdbc.execute("INSERT INTO detalles_venta (venta_id, producto_id, cantidad, precio_unitario, subtotal, factor) " +
                "VALUES (" + ventaRuc + ", " + productoId + ", 2, 10.00, 20.00, 1)");

        VentaDetalleGREDTO dto = detalleParaGRE(ventaRuc, TENANT_A).getBody();

        assertThat(dto).isNotNull();
        assertThat(dto.getClienteTipoDoc()).isEqualTo("6");        // RUC → código SUNAT "6"
        assertThat(dto.getClienteNumDoc()).isEqualTo("20100000001");
    }

    // ─── f12: factor > 1 en detalles se preserva en la respuesta ─────────────

    @Test
    @Order(12)
    void f12_detalleParaGRE_itemsConFactorMayorAUno() {
        Long ventaConFactor = jdbc.queryForObject(
                "INSERT INTO ventas (total, estado, tenant_id, created_at) " +
                "VALUES (200.00, 'COMPLETADA', '" + TENANT_A + "', NOW()) RETURNING id",
                Long.class);
        // Venta de 2 cajas, donde cada caja equivale a 20 unidades (factor=20)
        jdbc.execute("INSERT INTO detalles_venta (venta_id, producto_id, cantidad, precio_unitario, subtotal, factor) " +
                "VALUES (" + ventaConFactor + ", " + productoId + ", 2, 100.00, 200.00, 20)");

        VentaDetalleGREDTO dto = detalleParaGRE(ventaConFactor, TENANT_A).getBody();

        assertThat(dto).isNotNull();
        assertThat(dto.getItems()).hasSize(1);
        VentaDetalleGREDTO.ItemDTO item = dto.getItems().get(0);
        assertThat(item.getCantidad()).isEqualTo(2);
        assertThat(item.getFactor()).isEqualTo(20);
    }
}
