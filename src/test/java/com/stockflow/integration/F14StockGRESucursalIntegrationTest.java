package com.stockflow.integration;

import com.stockflow.controller.ProductoController;
import com.stockflow.dto.ProductoDTO;
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
 * Tests de integración para F-14: stock por sucursal en selector GRE y validación de emisión.
 *
 * f01 — /buscar-gre sin sucursalId: retorna Producto.stockActual (global)
 * f02 — /buscar-gre con sucursalId: retorna PSS de la sucursal (misma fuente que Movimientos)
 * f03 — /buscar-gre con sucursalId sin PSS: fallback a Producto.stockActual
 * f04 — /buscar-gre: tenant isolation (no devuelve productos de otros tenants)
 * f05 — SUCURSALES, PSS suficiente: validación pasa, llega a emisión
 * f06 — SUCURSALES, PSS insuficiente aunque global tenga: lanza BadRequestException
 * f07 — SUCURSALES, PSS distribuido: origen sin stock, destino con stock → lanza BadRequestException
 * f13 — /buscar-gre con PSS=0: retorna 0 (stock real de la sucursal)
 * f14 — SUCURSALES, sin PSS distribuido + global suficiente: emite
 * f15 — Principal con PSS distribuido: emisión usa PSS correcto
 * f08 — SUCURSALES, PSS suficiente aunque global insuficiente: validación pasa
 * f09 — DELIVERY con sucursal: usa stock global (no PSS), PSS=0 no bloquea
 * f10 — SUCURSALES múltiples items, uno insuficiente: excepción con nombre del producto
 * f11 — SUCURSALES, exactamente el stock disponible: validación pasa
 * f12 — SUCURSALES, sin lotes pero con PSS: usa PSS correctamente
 */
@SpringBootTest
@ActiveProfiles("test")
@TestInstance(TestInstance.Lifecycle.PER_CLASS)
@TestMethodOrder(MethodOrderer.OrderAnnotation.class)
class F14StockGRESucursalIntegrationTest {

    @ServiceConnection
    static final PostgreSQLContainer<?> postgres = new PostgreSQLContainer<>("postgres:16");

    static {
        postgres.start();
    }

    @MockBean
    LucodeGREService lucodeGREService;

    @Autowired private ProductoController productoController;
    @Autowired private GuiaRemisionService greService;
    @Autowired private GuiaRemisionRepository greRepo;
    @Autowired private JdbcTemplate jdbc;

    private static final String TENANT   = "f14-stock-a";
    private static final String TENANT_B = "f14-stock-b";

    private Long sucursalOrigenId;
    private Long sucursalDestinoId;
    private Long productoId;
    private Long productoId2;

    @BeforeAll
    void setup() {
        jdbc.execute("INSERT INTO tenants (tenant_id, nombre, activo, rubro) " +
                "VALUES ('" + TENANT   + "', 'F14 A', true, 'OTRO') ON CONFLICT (tenant_id) DO NOTHING");
        jdbc.execute("INSERT INTO tenants (tenant_id, nombre, activo, rubro) " +
                "VALUES ('" + TENANT_B + "', 'F14 B', true, 'OTRO') ON CONFLICT (tenant_id) DO NOTHING");
        jdbc.execute("UPDATE tenants SET ose_token = 'tok-f14-test' WHERE tenant_id = '" + TENANT   + "'");
        jdbc.execute("UPDATE tenants SET ose_token = 'tok-f14-test' WHERE tenant_id = '" + TENANT_B + "'");

        sucursalOrigenId = jdbc.queryForObject(
                "INSERT INTO sucursales (nombre, tenant_id, es_principal, activo) " +
                "VALUES ('Almacén F14', '" + TENANT + "', true, true) RETURNING id", Long.class);
        sucursalDestinoId = jdbc.queryForObject(
                "INSERT INTO sucursales (nombre, tenant_id, es_principal, activo) " +
                "VALUES ('Tienda F14',  '" + TENANT + "', false, true) RETURNING id", Long.class);

        Long umId = jdbc.queryForObject(
                "SELECT id FROM unidad_medida WHERE tenant_id IS NULL LIMIT 1", Long.class);

        productoId = jdbc.queryForObject(
                "INSERT INTO productos (nombre, stock_actual, costo_unitario, precio_venta, " +
                "unidad_medida_id, activo, tenant_id, version) " +
                "VALUES ('F14 ProdA', 50, 5.00, 10.00, " + umId + ", true, '" + TENANT + "', 0) RETURNING id",
                Long.class);
        productoId2 = jdbc.queryForObject(
                "INSERT INTO productos (nombre, stock_actual, costo_unitario, precio_venta, " +
                "unidad_medida_id, activo, tenant_id, version) " +
                "VALUES ('F14 ProdB', 30, 3.00, 7.00, " + umId + ", true, '" + TENANT + "', 0) RETURNING id",
                Long.class);
    }

    @BeforeEach
    void limpiarYConfigurarMock() {
        jdbc.execute("DELETE FROM guia_remision_eventos WHERE tenant_id IN ('" + TENANT + "', '" + TENANT_B + "')");
        jdbc.execute("DELETE FROM guias_remision WHERE tenant_id IN ('" + TENANT + "', '" + TENANT_B + "')");
        jdbc.execute("DELETE FROM producto_stock_sucursal WHERE tenant_id = '" + TENANT + "'");
        jdbc.execute("DELETE FROM stock_lotes WHERE tenant_id = '" + TENANT + "'");

        LucodeGREResponse successResp = new LucodeGREResponse();
        successResp.setSuccess(true);
        successResp.setMessage("Emitido correctamente (mock)");
        Mockito.reset(lucodeGREService);
        when(lucodeGREService.emitirGRE(any(), any())).thenReturn(successResp);
    }

    // ─── Helpers ─────────────────────────────────────────────────────────────

    private void setPSS(Long productoId, Long sucursalId, int stock) {
        jdbc.update(
            "INSERT INTO producto_stock_sucursal (producto_id, sucursal_id, tenant_id, stock_actual) " +
            "VALUES (?, ?, ?, ?) ON CONFLICT (producto_id, sucursal_id) DO UPDATE SET stock_actual = ?",
            productoId, sucursalId, TENANT, stock, stock);
    }

    private List<ProductoDTO> buscarGre(String nombre, Long sucursalId) {
        var auth = new UsernamePasswordAuthenticationToken(
                "testuser", null, List.of(new SimpleGrantedAuthority("ROLE_ADMIN")));
        SecurityContextHolder.getContext().setAuthentication(auth);
        TenantContext.setCurrentTenant(TENANT);
        try {
            ResponseEntity<List<ProductoDTO>> resp = productoController.buscarParaGre(nombre, sucursalId);
            return resp.getBody();
        } finally {
            TenantContext.clear();
            SecurityContextHolder.clearContext();
        }
    }

    private Long crearBorradorSucursales(Long productoId, int cantidad) {
        TenantContext.setCurrentTenant(TENANT);
        TenantContext.setCurrentUserId(1L);
        try {
            CrearGuiaRemisionRequest req = new CrearGuiaRemisionRequest();
            req.setTipoTraslado(TipoTrasladoGRE.SUCURSALES);
            req.setMotivoTrasladoCodigo("04");
            req.setModalidadTransporte(ModalidadTransporteGRE.PRIVADO);
            req.setFechaTraslado(LocalDate.now());
            req.setSucursalOrigenId(sucursalOrigenId);
            req.setSucursalDestinoId(sucursalDestinoId);
            GuiaRemisionItemDTO item = new GuiaRemisionItemDTO();
            item.setProductoId(productoId);
            item.setCantidad(new BigDecimal(cantidad));
            req.setItems(List.of(item));
            return greService.crearBorrador(req).getId();
        } finally {
            TenantContext.clear();
        }
    }

    private void emitirComoTenant(Long guiaId) {
        TenantContext.setCurrentTenant(TENANT);
        TenantContext.setCurrentUserId(1L);
        try {
            greService.emitirGRE(guiaId);
        } finally {
            TenantContext.clear();
        }
    }

    // ─── Tests f01-f04: endpoint /buscar-gre ──────────────────────────────────

    @Test
    @Order(1)
    void f01_buscarGreSinSucursalId_retornaStockGlobal() {
        List<ProductoDTO> result = buscarGre("F14 ProdA", null);

        assertThat(result).isNotEmpty();
        ProductoDTO dto = result.stream()
                .filter(p -> p.getId().equals(productoId)).findFirst().orElseThrow();
        assertThat(dto.getStockActual())
                .as("buscarGre retorna Producto.stockActual (misma fuente que Kardex)")
                .isEqualTo(50);
    }

    @Test
    @Order(2)
    void f02_buscarGreConSucursalId_retornaStockPSS() {
        // buscarGre con sucursalId retorna el stock PSS de esa sucursal (igual que Movimientos)
        setPSS(productoId, sucursalOrigenId, 12);

        List<ProductoDTO> result = buscarGre("F14 ProdA", sucursalOrigenId);
        ProductoDTO dto = result.stream()
                .filter(p -> p.getId().equals(productoId)).findFirst().orElseThrow();
        assertThat(dto.getStockActual())
                .as("buscarGre con sucursalId retorna PSS (12), no stockActual global (50)")
                .isEqualTo(12);
    }

    @Test
    @Order(3)
    void f03_buscarGre_sinPSS_retornaStockGlobal() {
        // Sin PSS registrado para la sucursal → fallback a Producto.stockActual
        List<ProductoDTO> result = buscarGre("F14 ProdA", sucursalOrigenId);
        ProductoDTO dto = result.stream()
                .filter(p -> p.getId().equals(productoId)).findFirst().orElseThrow();
        assertThat(dto.getStockActual())
                .as("Sin PSS para la sucursal, buscarGre retorna Producto.stockActual=50")
                .isEqualTo(50);
    }

    @Test
    @Order(4)
    void f04_buscarGre_tenantIsolation() {
        Long umId = jdbc.queryForObject(
                "SELECT id FROM unidad_medida WHERE tenant_id IS NULL LIMIT 1", Long.class);
        jdbc.update(
                "INSERT INTO productos (nombre, stock_actual, costo_unitario, precio_venta, " +
                "unidad_medida_id, activo, tenant_id, version) " +
                "VALUES ('F14 ProdB-only', 20, 1.00, 2.00, ?, true, ?, 0)",
                umId, TENANT_B);

        List<ProductoDTO> result = buscarGre("F14 ProdB-only", null);
        assertThat(result)
                .as("Tenant A no debe ver productos del Tenant B")
                .isEmpty();
    }

    // ─── Tests f05-f12: validarStockParaEmision en emitirGRE ─────────────────

    @Test
    @Order(5)
    void f05_sucursales_PssSuficiente_emisionAvanza() {
        setPSS(productoId, sucursalOrigenId, 20);
        Long guiaId = crearBorradorSucursales(productoId, 5);

        assertThatCode(() -> emitirComoTenant(guiaId))
                .doesNotThrowAnyException();
        verify(lucodeGREService, times(1))
                .emitirGRE(any(), any());
    }

    @Test
    @Order(6)
    void f06_sucursales_PssInsuficienteGlobalSuficiente_lanzaExcepcion() {
        setPSS(productoId, sucursalOrigenId, 2);
        // Producto.stockActual = 50 (global), pero PSS = 2
        Long guiaId = crearBorradorSucursales(productoId, 5);

        assertThatThrownBy(() -> emitirComoTenant(guiaId))
                .isInstanceOf(BadRequestException.class)
                .hasMessageContaining("Stock insuficiente");
        verifyNoInteractions(lucodeGREService);
    }

    @Test
    @Order(7)
    void f07_sucursales_PssConStockEnDestino_origenSinStock_lanzaExcepcion() {
        // PSS distribuido: destino tiene stock (10), origen tiene PSS=0 → origen=0 → falla
        setPSS(productoId, sucursalDestinoId, 10); // sum total PSS > 0 → modo distribución activo
        setPSS(productoId, sucursalOrigenId,  0);  // origen explícitamente en 0
        Long guiaId = crearBorradorSucursales(productoId, 1);

        assertThatThrownBy(() -> emitirComoTenant(guiaId))
                .as("PSS distribuido: origen=0 → debe lanzar BadRequestException (stock insuficiente)")
                .isInstanceOf(BadRequestException.class)
                .hasMessageContaining("Stock insuficiente");
        verifyNoInteractions(lucodeGREService);
    }

    @Test
    @Order(8)
    void f08_sucursales_PssSuficienteGlobalInsuficiente_emisionAvanza() {
        // PSS = 10, global = 1 → para SUCURSALES debe usar PSS
        jdbc.update("UPDATE productos SET stock_actual = 1 WHERE id = ?", productoId);
        setPSS(productoId, sucursalOrigenId, 10);
        Long guiaId = crearBorradorSucursales(productoId, 5);

        assertThatCode(() -> emitirComoTenant(guiaId))
                .as("PSS=10 debe bastar aunque global=1")
                .doesNotThrowAnyException();
        verify(lucodeGREService, times(1)).emitirGRE(any(), any());

        jdbc.update("UPDATE productos SET stock_actual = 50 WHERE id = ?", productoId);
    }

    @Test
    @Order(9)
    void f09_delivery_sigueUsandoStockGlobal_PssCeroNoBloquea() {
        setPSS(productoId, sucursalOrigenId, 0);
        // Producto.stockActual = 50, PSS = 0, pero DELIVERY usa el global

        TenantContext.setCurrentTenant(TENANT);
        TenantContext.setCurrentUserId(1L);
        Long guiaId;
        try {
            CrearGuiaRemisionRequest req = new CrearGuiaRemisionRequest();
            req.setTipoTraslado(TipoTrasladoGRE.DELIVERY);
            req.setMotivoTrasladoCodigo("01");
            req.setModalidadTransporte(ModalidadTransporteGRE.PRIVADO);
            req.setFechaTraslado(LocalDate.now());
            req.setSucursalOrigenId(sucursalOrigenId);
            req.setDestinatarioTipoDoc("6");
            req.setDestinatarioNumDoc("20100047218");
            req.setDestinatarioRazonSocial("Destinatario F14 SAC");
            GuiaRemisionItemDTO item = new GuiaRemisionItemDTO();
            item.setProductoId(productoId);
            item.setCantidad(new BigDecimal(5));
            req.setItems(List.of(item));
            guiaId = greService.crearBorrador(req).getId();
        } finally {
            TenantContext.clear();
        }

        assertThatCode(() -> emitirComoTenant(guiaId))
                .as("DELIVERY con global=50 y PSS=0 debe pasar validación de stock")
                .doesNotThrowAnyException();
        verify(lucodeGREService, times(1)).emitirGRE(any(), any());
    }

    @Test
    @Order(10)
    void f10_sucursales_multiplesItemsUnoInsuficiente_lanzaConNombreProducto() {
        setPSS(productoId,  sucursalOrigenId, 20);
        setPSS(productoId2, sucursalOrigenId, 2);

        TenantContext.setCurrentTenant(TENANT);
        TenantContext.setCurrentUserId(1L);
        Long guiaId;
        try {
            CrearGuiaRemisionRequest req = new CrearGuiaRemisionRequest();
            req.setTipoTraslado(TipoTrasladoGRE.SUCURSALES);
            req.setMotivoTrasladoCodigo("04");
            req.setModalidadTransporte(ModalidadTransporteGRE.PRIVADO);
            req.setFechaTraslado(LocalDate.now());
            req.setSucursalOrigenId(sucursalOrigenId);
            req.setSucursalDestinoId(sucursalDestinoId);
            GuiaRemisionItemDTO item1 = new GuiaRemisionItemDTO();
            item1.setProductoId(productoId);
            item1.setCantidad(new BigDecimal(5));  // OK: PSS=20
            GuiaRemisionItemDTO item2 = new GuiaRemisionItemDTO();
            item2.setProductoId(productoId2);
            item2.setCantidad(new BigDecimal(10)); // FALLA: PSS=2
            req.setItems(List.of(item1, item2));
            guiaId = greService.crearBorrador(req).getId();
        } finally {
            TenantContext.clear();
        }

        assertThatThrownBy(() -> emitirComoTenant(guiaId))
                .isInstanceOf(BadRequestException.class)
                .hasMessageContaining("F14 ProdB")
                .hasMessageContaining("Stock insuficiente");
        verifyNoInteractions(lucodeGREService);
    }

    @Test
    @Order(11)
    void f11_sucursales_exactamenteElStockDisponible_emisionAvanza() {
        setPSS(productoId, sucursalOrigenId, 7);
        Long guiaId = crearBorradorSucursales(productoId, 7);

        assertThatCode(() -> emitirComoTenant(guiaId))
                .as("Pedir exactamente el stock disponible (7) debe pasar")
                .doesNotThrowAnyException();
        verify(lucodeGREService, times(1)).emitirGRE(any(), any());
    }

    @Test
    @Order(12)
    void f12_sucursales_sinLotesPeroConPSS_usaPSS() {
        // Sin stock_lotes → stockLoteService.getStockVigente() devuelve null → debe usar PSS
        setPSS(productoId, sucursalOrigenId, 15);
        Long guiaId = crearBorradorSucursales(productoId, 10);

        assertThatCode(() -> emitirComoTenant(guiaId))
                .as("Sin lotes pero con PSS=15, solicitar 10 debe pasar")
                .doesNotThrowAnyException();
        verify(lucodeGREService, times(1)).emitirGRE(any(), any());
    }

    // ─── Tests f13-f15: stock global/legacy y aislamiento entre ramas ───────────

    @Test
    @Order(13)
    void f13_buscarGre_conPSSCero_retornaStockPSSCero() {
        // PSS=0 significa sin stock en esa sucursal (puede ser real o inicializado vacío).
        // buscarGre muestra el PSS real para que el usuario sepa que no hay stock disponible.
        setPSS(productoId, sucursalOrigenId, 0);
        setPSS(productoId, sucursalDestinoId, 0);

        List<ProductoDTO> result = buscarGre("F14 ProdA", sucursalOrigenId);
        ProductoDTO dto = result.stream()
                .filter(p -> p.getId().equals(productoId)).findFirst().orElseThrow();
        assertThat(dto.getStockActual())
                .as("PSS=0 en la sucursal: buscarGre retorna 0 (sin stock en esa sucursal)")
                .isEqualTo(0);
    }

    @Test
    @Order(14)
    void f14_emision_sinPSSEnNingunaSucursal_globalSuficiente_avanza() {
        // Sin PSS en ninguna sucursal → fallback a stock global (50) → 5 unidades pasan
        Long guiaId = crearBorradorSucursales(productoId, 5);

        assertThatCode(() -> emitirComoTenant(guiaId))
                .as("Sin PSS en ninguna sucursal: global=50, cantidad=5 → debe emitir")
                .doesNotThrowAnyException();
        verify(lucodeGREService, times(1)).emitirGRE(any(), any());
    }

    @Test
    @Order(15)
    void f15_emision_pssDistribuido_origenConStock_avanza() {
        // PSS distribuido correctamente: origen=5, suma>0 → emitir 3 desde origen
        setPSS(productoId, sucursalOrigenId,  5);
        setPSS(productoId, sucursalDestinoId, 2);
        Long guiaId = crearBorradorSucursales(productoId, 3);

        assertThatCode(() -> emitirComoTenant(guiaId))
                .as("PSS distribuido: origen=5, cantidad=3 → debe emitir")
                .doesNotThrowAnyException();
        verify(lucodeGREService, times(1)).emitirGRE(any(), any());
    }
}
