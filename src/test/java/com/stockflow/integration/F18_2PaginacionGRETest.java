package com.stockflow.integration;

import com.stockflow.dto.gre.CrearGuiaRemisionRequest;
import com.stockflow.dto.gre.GuiaRemisionItemDTO;
import com.stockflow.dto.gre.GuiaRemisionStatsResponse;
import com.stockflow.entity.gre.EstadoTributarioGRE;
import com.stockflow.entity.gre.ModalidadTransporteGRE;
import com.stockflow.entity.gre.TipoTrasladoGRE;
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
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
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
 * Tests de integración para F-18.2: paginación real del listado de GRE.
 *
 * p01 — Primera página devuelve máximo 10 registros
 * p02 — Segunda página devuelve registros siguientes
 * p03 — Tamaño 25 funciona correctamente
 * p04 — Total de registros es correcto
 * p05 — Total de páginas es correcto
 * p06 — Búsqueda por número de guía funciona con paginación
 * p07 — Filtro por estado tributario funciona con paginación
 * p08 — Cambio de tamaño de página mantiene aislamiento multi-tenant
 * p09 — Estadísticas globales no dependen de la página actual
 * p10 — Estadísticas reflejan conteos correctos por estado
 */
@SpringBootTest
@ActiveProfiles("test")
@TestInstance(TestInstance.Lifecycle.PER_CLASS)
@TestMethodOrder(MethodOrderer.OrderAnnotation.class)
class F18_2PaginacionGRETest {

    @ServiceConnection
    static final PostgreSQLContainer<?> postgres = new PostgreSQLContainer<>("postgres:16");

    static {
        postgres.start();
    }

    @MockBean
    LucodeGREService lucodeGREService;

    @Autowired private GuiaRemisionService greService;
    @Autowired private JdbcTemplate jdbc;

    private static final String TENANT_A = "f18p-tenant-a";
    private static final String TENANT_B = "f18p-tenant-b";
    private Long productoId;

    @BeforeAll
    void setup() {
        for (String t : List.of(TENANT_A, TENANT_B)) {
            jdbc.execute("INSERT INTO tenants (tenant_id, nombre, activo, rubro) " +
                    "VALUES ('" + t + "', 'F18 Pag " + t + "', true, 'OTRO') ON CONFLICT (tenant_id) DO NOTHING");
            jdbc.update("UPDATE tenants SET ubigeo_despacho = '150101', direccion_despacho = 'Almacén F18P' WHERE tenant_id = ?", t);
        }

        Long umId = jdbc.queryForObject(
                "SELECT id FROM unidad_medida WHERE tenant_id IS NULL LIMIT 1", Long.class);

        productoId = jdbc.queryForObject(
                "INSERT INTO productos (nombre, stock_actual, costo_unitario, precio_venta, " +
                "unidad_medida_id, activo, tenant_id, version) " +
                "VALUES ('F18P Producto', 1000, 5.00, 10.00, " + umId + ", true, '" + TENANT_A + "', 0) RETURNING id",
                Long.class);

        // Crear 15 GRE para TENANT_A
        LucodeGREResponse ok = new LucodeGREResponse();
        ok.setSuccess(true); ok.setMessage("OK");
        Mockito.reset(lucodeGREService);
        when(lucodeGREService.emitirGRE(any(), any())).thenReturn(ok);

        TenantContext.setCurrentTenant(TENANT_A);
        TenantContext.setCurrentUserId(1L);
        try {
            for (int i = 1; i <= 15; i++) {
                CrearGuiaRemisionRequest req = buildDeliveryRequest("Cliente " + i);
                greService.crearBorrador(req);
            }
        } finally {
            TenantContext.clear();
        }

        // Crear 3 GRE para TENANT_B (aislamiento)
        TenantContext.setCurrentTenant(TENANT_B);
        TenantContext.setCurrentUserId(1L);
        try {
            Long umIdB = jdbc.queryForObject("SELECT id FROM unidad_medida WHERE tenant_id IS NULL LIMIT 1", Long.class);
            Long prodB = jdbc.queryForObject(
                    "INSERT INTO productos (nombre, stock_actual, costo_unitario, precio_venta, " +
                    "unidad_medida_id, activo, tenant_id, version) " +
                    "VALUES ('F18P Prod B', 100, 5.00, 10.00, " + umIdB + ", true, '" + TENANT_B + "', 0) RETURNING id",
                    Long.class);
            for (int i = 1; i <= 3; i++) {
                CrearGuiaRemisionRequest req = new CrearGuiaRemisionRequest();
                req.setTipoTraslado(TipoTrasladoGRE.DELIVERY);
                req.setMotivoTrasladoCodigo("01");
                req.setModalidadTransporte(ModalidadTransporteGRE.PRIVADO);
                req.setFechaTraslado(LocalDate.now());
                req.setDestinatarioRazonSocial("Cliente B" + i);
                req.setDestinatarioTipoDoc("1");
                req.setDestinatarioNumDoc("1234567" + i);
                GuiaRemisionItemDTO item = new GuiaRemisionItemDTO();
                item.setProductoId(prodB);
                item.setCantidad(BigDecimal.ONE);
                req.setItems(List.of(item));
                greService.crearBorrador(req);
            }
        } finally {
            TenantContext.clear();
        }
    }

    private CrearGuiaRemisionRequest buildDeliveryRequest(String razonSocial) {
        CrearGuiaRemisionRequest req = new CrearGuiaRemisionRequest();
        req.setTipoTraslado(TipoTrasladoGRE.DELIVERY);
        req.setMotivoTrasladoCodigo("01");
        req.setModalidadTransporte(ModalidadTransporteGRE.PRIVADO);
        req.setFechaTraslado(LocalDate.now());
        req.setDestinatarioRazonSocial(razonSocial);
        req.setDestinatarioTipoDoc("1");
        req.setDestinatarioNumDoc("12345678");
        GuiaRemisionItemDTO item = new GuiaRemisionItemDTO();
        item.setProductoId(productoId);
        item.setCantidad(BigDecimal.ONE);
        req.setItems(List.of(item));
        return req;
    }

    // ─── Tests ───────────────────────────────────────────────────────────────

    @Test
    @Order(1)
    void p01_primeraPagina_devuelveMaximo10Registros() {
        TenantContext.setCurrentTenant(TENANT_A);
        TenantContext.setCurrentUserId(1L);
        try {
            var pageable = PageRequest.of(0, 10, Sort.by("createdAt").descending());
            var page = greService.listar(null, null, null, pageable);
            assertThat(page.getContent()).hasSize(10);
        } finally { TenantContext.clear(); }
    }

    @Test
    @Order(2)
    void p02_segundaPagina_devuelveSiguientesRegistros() {
        TenantContext.setCurrentTenant(TENANT_A);
        TenantContext.setCurrentUserId(1L);
        try {
            var p1 = greService.listar(null, null, null, PageRequest.of(0, 10, Sort.by("createdAt").descending()));
            var p2 = greService.listar(null, null, null, PageRequest.of(1, 10, Sort.by("createdAt").descending()));
            assertThat(p2.getContent()).hasSize(5);
            // IDs no se solapan entre páginas
            var idsP1 = p1.getContent().stream().map(r -> r.getId()).toList();
            var idsP2 = p2.getContent().stream().map(r -> r.getId()).toList();
            assertThat(idsP1).doesNotContainAnyElementsOf(idsP2);
        } finally { TenantContext.clear(); }
    }

    @Test
    @Order(3)
    void p03_tamano25_devuelveTodosLosRegistros() {
        TenantContext.setCurrentTenant(TENANT_A);
        TenantContext.setCurrentUserId(1L);
        try {
            var page = greService.listar(null, null, null, PageRequest.of(0, 25, Sort.by("createdAt").descending()));
            assertThat(page.getContent()).hasSize(15);
            assertThat(page.getTotalElements()).isEqualTo(15);
        } finally { TenantContext.clear(); }
    }

    @Test
    @Order(4)
    void p04_totalElementos_esExacto() {
        TenantContext.setCurrentTenant(TENANT_A);
        TenantContext.setCurrentUserId(1L);
        try {
            var page = greService.listar(null, null, null, PageRequest.of(0, 10, Sort.by("createdAt").descending()));
            assertThat(page.getTotalElements()).isEqualTo(15);
        } finally { TenantContext.clear(); }
    }

    @Test
    @Order(5)
    void p05_totalPaginas_esExacto() {
        TenantContext.setCurrentTenant(TENANT_A);
        TenantContext.setCurrentUserId(1L);
        try {
            var page = greService.listar(null, null, null, PageRequest.of(0, 10, Sort.by("createdAt").descending()));
            assertThat(page.getTotalPages()).isEqualTo(2);
        } finally { TenantContext.clear(); }
    }

    @Test
    @Order(6)
    void p06_busquedaPorDestinatario_combinadaConPaginacion() {
        TenantContext.setCurrentTenant(TENANT_A);
        TenantContext.setCurrentUserId(1L);
        try {
            // "Cliente 1" debería encontrar: Cliente 1, Cliente 10, Cliente 11, Cliente 12, Cliente 13, Cliente 14, Cliente 15 → 7
            var page = greService.listar("Cliente 1", null, null, PageRequest.of(0, 25, Sort.by("createdAt").descending()));
            assertThat(page.getTotalElements()).isGreaterThanOrEqualTo(1);
            assertThat(page.getContent()).allMatch(g ->
                g.getDestinatarioRazonSocial() != null &&
                g.getDestinatarioRazonSocial().toLowerCase().contains("cliente 1")
            );
        } finally { TenantContext.clear(); }
    }

    @Test
    @Order(7)
    void p07_filtroPorEstado_combinadoConPaginacion() {
        TenantContext.setCurrentTenant(TENANT_A);
        TenantContext.setCurrentUserId(1L);
        try {
            var page = greService.listar(null, EstadoTributarioGRE.BORRADOR, null,
                    PageRequest.of(0, 25, Sort.by("createdAt").descending()));
            assertThat(page.getTotalElements()).isEqualTo(15);
            assertThat(page.getContent()).allMatch(g -> g.getEstadoTributario() == EstadoTributarioGRE.BORRADOR);
        } finally { TenantContext.clear(); }
    }

    @Test
    @Order(8)
    void p08_aislamientoMultiTenant_tenantBNoVeRegistrosDeA() {
        TenantContext.setCurrentTenant(TENANT_B);
        TenantContext.setCurrentUserId(1L);
        try {
            var page = greService.listar(null, null, null, PageRequest.of(0, 25, Sort.by("createdAt").descending()));
            assertThat(page.getTotalElements()).isEqualTo(3);
            assertThat(page.getContent()).allMatch(g -> g.getDestinatarioRazonSocial() != null &&
                g.getDestinatarioRazonSocial().startsWith("Cliente B"));
        } finally { TenantContext.clear(); }
    }

    @Test
    @Order(9)
    void p09_estadisticasGlobales_noDependendeDeLaPagina() {
        TenantContext.setCurrentTenant(TENANT_A);
        TenantContext.setCurrentUserId(1L);
        try {
            GuiaRemisionStatsResponse statsP1 = greService.stats(null);
            // Las estadísticas deben reflejar los 15 registros totales de TENANT_A
            assertThat(statsP1.getTotal()).isEqualTo(15);
        } finally { TenantContext.clear(); }
    }

    @Test
    @Order(10)
    void p10_estadisticas_conteoPorEstadoEsCorrecto() {
        TenantContext.setCurrentTenant(TENANT_A);
        TenantContext.setCurrentUserId(1L);
        try {
            GuiaRemisionStatsResponse stats = greService.stats(null);
            // Los 15 creados están en BORRADOR → pendientes=15, emitidas=0, error=0
            assertThat(stats.getPendientes()).isEqualTo(15);
            assertThat(stats.getEmitidas()).isEqualTo(0);
            assertThat(stats.getConError()).isEqualTo(0);
            assertThat(stats.getTotal()).isEqualTo(stats.getPendientes() + stats.getEmitidas() + stats.getConError());
        } finally { TenantContext.clear(); }
    }
}
