package com.stockflow.integration;

import com.stockflow.dto.gre.GuiaRemisionListResponse;
import com.stockflow.service.GuiaRemisionService;
import com.stockflow.util.TenantContext;
import org.junit.jupiter.api.*;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.ActiveProfiles;
import org.testcontainers.containers.PostgreSQLContainer;

import static org.assertj.core.api.Assertions.*;

/**
 * Pruebas de integración para GuiaRemisionService.listar().
 *
 * Verifica que la consulta paginada inicializa correctamente las relaciones
 * sucursalOrigen y sucursalDestino (LAZY) sin LazyInitializationException
 * cuando open-in-view=false.
 *
 * g01 — Listar guía SUCURSALES: nombres de origen y destino presentes
 * g02 — Listar guía DELIVERY: sin sucursales, con destinatario
 * g03 — Listar guía con sucursalOrigen pero sin destino (nulo permitido)
 * g04 — Paginación: múltiples guías, page/size/totalElements correctos
 * g05 — Serialización completa: todos los campos del DTO mapeados
 * g06 — Tenant A no ve guías del Tenant B
 * g07 — Listar guía SUCURSALES tipo PRIVADO y DELIVERY tipo PUBLICO
 */
@SpringBootTest
@ActiveProfiles("test")
@TestInstance(TestInstance.Lifecycle.PER_CLASS)
@TestMethodOrder(MethodOrderer.OrderAnnotation.class)
class GuiaRemisionListIntegrationTest {

    @ServiceConnection
    static final PostgreSQLContainer<?> postgres = new PostgreSQLContainer<>("postgres:16");

    static {
        postgres.start();
    }

    @Autowired private GuiaRemisionService greService;
    @Autowired private JdbcTemplate jdbc;

    private static final String TENANT_A = "gre-list-a";
    private static final String TENANT_B = "gre-list-b";

    private Long sucursalOrigenId;
    private Long sucursalDestinoId;

    @BeforeAll
    void setup() {
        jdbc.execute("INSERT INTO tenants (tenant_id, nombre, activo, rubro) " +
                "VALUES ('" + TENANT_A + "', 'GRE List A', true, 'OTRO') " +
                "ON CONFLICT (tenant_id) DO NOTHING");
        jdbc.execute("INSERT INTO tenants (tenant_id, nombre, activo, rubro) " +
                "VALUES ('" + TENANT_B + "', 'GRE List B', true, 'OTRO') " +
                "ON CONFLICT (tenant_id) DO NOTHING");

        sucursalOrigenId = jdbc.queryForObject(
                "INSERT INTO sucursales (nombre, tenant_id, es_principal, activo) " +
                "VALUES ('Almacén Central', '" + TENANT_A + "', true, true) RETURNING id",
                Long.class);

        sucursalDestinoId = jdbc.queryForObject(
                "INSERT INTO sucursales (nombre, tenant_id, es_principal, activo) " +
                "VALUES ('Tienda Norte', '" + TENANT_A + "', false, true) RETURNING id",
                Long.class);
    }

    @BeforeEach
    void limpiarGuias() {
        jdbc.execute("DELETE FROM guias_remision WHERE tenant_id IN ('" + TENANT_A + "', '" + TENANT_B + "')");
    }

    // ─── Helpers ───────────────────────────────────────────────────────────────

    private Long insertarGuiaSucursales(Long origenId, Long destinoId) {
        return jdbc.queryForObject(
                "INSERT INTO guias_remision " +
                "(tenant_id, serie, correlativo, tipo_traslado, motivo_traslado_codigo, " +
                " modalidad_transporte, fecha_traslado, estado_tributario, estado_logistico, " +
                " sucursal_origen_id, sucursal_destino_id, unidad_medida_peso, idempotency_key) " +
                "VALUES ('" + TENANT_A + "', 'T001', 1, 'SUCURSALES', '04', " +
                " 'PRIVADO', CURRENT_DATE, 'BORRADOR', 'PENDIENTE', " +
                " " + origenId + ", " + (destinoId != null ? destinoId : "NULL") + ", 'KGM', " +
                " gen_random_uuid()) RETURNING id",
                Long.class);
    }

    private Long insertarGuiaDelivery() {
        return jdbc.queryForObject(
                "INSERT INTO guias_remision " +
                "(tenant_id, serie, correlativo, tipo_traslado, motivo_traslado_codigo, " +
                " modalidad_transporte, fecha_traslado, estado_tributario, estado_logistico, " +
                " destinatario_tipo_doc, destinatario_num_doc, destinatario_razon_social, " +
                " unidad_medida_peso, idempotency_key) " +
                "VALUES ('" + TENANT_A + "', 'T001', 2, 'DELIVERY', '01', " +
                " 'PUBLICO', CURRENT_DATE, 'BORRADOR', 'PENDIENTE', " +
                " '6', '20100047218', 'Cliente Delivery SAC', " +
                " 'KGM', gen_random_uuid()) RETURNING id",
                Long.class);
    }

    private Page<GuiaRemisionListResponse> listarComoTenantA(int page, int size) {
        TenantContext.setCurrentTenant(TENANT_A);
        try {
            return greService.listar(null, null, null, PageRequest.of(page, size, Sort.by("createdAt").descending()));
        } finally {
            TenantContext.clear();
        }
    }

    // ─── Tests ─────────────────────────────────────────────────────────────────

    @Test
    @Order(1)
    void g01_listarGuiaSucursales_conOrigenYDestino() {
        insertarGuiaSucursales(sucursalOrigenId, sucursalDestinoId);

        Page<GuiaRemisionListResponse> page = listarComoTenantA(0, 20);

        assertThat(page.getTotalElements()).isEqualTo(1);
        GuiaRemisionListResponse dto = page.getContent().get(0);

        assertThat(dto.getSucursalOrigenNombre())
                .as("Nombre de sucursal origen debe estar presente (no LazyInitializationException)")
                .isEqualTo("Almacén Central");
        assertThat(dto.getSucursalDestinoNombre())
                .as("Nombre de sucursal destino debe estar presente")
                .isEqualTo("Tienda Norte");
        assertThat(dto.getDestinatarioRazonSocial()).isNull();
    }

    @Test
    @Order(2)
    void g02_listarGuiaDelivery_sinSucursales_conDestinatario() {
        insertarGuiaDelivery();

        Page<GuiaRemisionListResponse> page = listarComoTenantA(0, 20);

        assertThat(page.getTotalElements()).isEqualTo(1);
        GuiaRemisionListResponse dto = page.getContent().get(0);

        assertThat(dto.getSucursalOrigenNombre()).isNull();
        assertThat(dto.getSucursalDestinoNombre()).isNull();
        assertThat(dto.getDestinatarioRazonSocial()).isEqualTo("Cliente Delivery SAC");
    }

    @Test
    @Order(3)
    void g03_listarGuiaConSoloOrigenSinDestino() {
        insertarGuiaSucursales(sucursalOrigenId, null);

        Page<GuiaRemisionListResponse> page = listarComoTenantA(0, 20);

        assertThat(page.getTotalElements()).isEqualTo(1);
        GuiaRemisionListResponse dto = page.getContent().get(0);

        assertThat(dto.getSucursalOrigenNombre())
                .as("Nombre de origen presente aunque destino sea nulo")
                .isEqualTo("Almacén Central");
        assertThat(dto.getSucursalDestinoNombre()).isNull();
    }

    @Test
    @Order(4)
    void g04_paginacion_multiplesGuias() {
        // Insertar 5 guías para probar paginación
        for (int i = 1; i <= 5; i++) {
            jdbc.update(
                "INSERT INTO guias_remision " +
                "(tenant_id, serie, correlativo, tipo_traslado, motivo_traslado_codigo, " +
                " modalidad_transporte, fecha_traslado, estado_tributario, estado_logistico, " +
                " sucursal_origen_id, sucursal_destino_id, unidad_medida_peso, idempotency_key) " +
                "VALUES (?, 'T001', ?, 'SUCURSALES', '04', " +
                " 'PRIVADO', CURRENT_DATE, 'BORRADOR', 'PENDIENTE', " +
                " ?, ?, 'KGM', gen_random_uuid())",
                TENANT_A, 100 + i, sucursalOrigenId, sucursalDestinoId);
        }

        TenantContext.setCurrentTenant(TENANT_A);
        try {
            Page<GuiaRemisionListResponse> pagina1 = greService.listar(
                    null, null, null, PageRequest.of(0, 2, Sort.by("createdAt").descending()));
            Page<GuiaRemisionListResponse> pagina2 = greService.listar(
                    null, null, null, PageRequest.of(1, 2, Sort.by("createdAt").descending()));

            assertThat(pagina1.getTotalElements()).isEqualTo(5);
            assertThat(pagina1.getTotalPages()).isEqualTo(3);
            assertThat(pagina1.getContent()).hasSize(2);
            assertThat(pagina2.getContent()).hasSize(2);

            // Cada item de pagina1 debe tener nombres resueltos (sin lazy error)
            pagina1.getContent().forEach(dto ->
                assertThat(dto.getSucursalOrigenNombre()).isEqualTo("Almacén Central"));
        } finally {
            TenantContext.clear();
        }
    }

    @Test
    @Order(5)
    void g05_serializacionCompleta_todosLosCamposPresentes() {
        insertarGuiaSucursales(sucursalOrigenId, sucursalDestinoId);

        GuiaRemisionListResponse dto = listarComoTenantA(0, 20).getContent().get(0);

        assertThat(dto.getId()).isNotNull();
        assertThat(dto.getNumeroCompleto()).isEqualTo("T001-1");
        assertThat(dto.getTipoTraslado()).isNotNull();
        assertThat(dto.getModalidadTransporte()).isNotNull();
        assertThat(dto.getFechaTraslado()).isNotNull();
        assertThat(dto.getEstadoTributario()).isNotNull();
        assertThat(dto.getEstadoLogistico()).isNotNull();
        assertThat(dto.getCreatedAt()).isNotNull();
        assertThat(dto.getSucursalOrigenNombre()).isNotNull();
        assertThat(dto.getSucursalDestinoNombre()).isNotNull();
    }

    @Test
    @Order(6)
    void g06_tenantANoVeGuiasDelTenantB() {
        // Insertar guías en ambos tenants
        insertarGuiaSucursales(sucursalOrigenId, sucursalDestinoId);

        // Sucursal del tenant B
        Long sucBId = jdbc.queryForObject(
                "INSERT INTO sucursales (nombre, tenant_id, es_principal, activo) " +
                "VALUES ('Almacén B', '" + TENANT_B + "', true, true) RETURNING id",
                Long.class);
        jdbc.update(
                "INSERT INTO guias_remision " +
                "(tenant_id, serie, correlativo, tipo_traslado, motivo_traslado_codigo, " +
                " modalidad_transporte, fecha_traslado, estado_tributario, estado_logistico, " +
                " sucursal_origen_id, unidad_medida_peso, idempotency_key) " +
                "VALUES (?, 'T001', 1, 'SUCURSALES', '04', " +
                " 'PRIVADO', CURRENT_DATE, 'BORRADOR', 'PENDIENTE', " +
                " ?, 'KGM', gen_random_uuid())",
                TENANT_B, sucBId);

        // Tenant A solo debe ver sus propias guías
        Page<GuiaRemisionListResponse> pageA = listarComoTenantA(0, 20);
        assertThat(pageA.getTotalElements())
                .as("Tenant A solo debe ver sus propias guías")
                .isEqualTo(1);

        // Tenant B solo debe ver sus propias guías
        TenantContext.setCurrentTenant(TENANT_B);
        try {
            Page<GuiaRemisionListResponse> pageB = greService.listar(
                    null, null, null, PageRequest.of(0, 20, Sort.by("createdAt").descending()));
            assertThat(pageB.getTotalElements())
                    .as("Tenant B solo debe ver sus propias guías")
                    .isEqualTo(1);
        } finally {
            TenantContext.clear();
        }
    }

    @Test
    @Order(7)
    void g07_modalidades_sucursalesYDelivery_correctamenteMapeadas() {
        insertarGuiaSucursales(sucursalOrigenId, sucursalDestinoId); // PRIVADO + SUCURSALES
        insertarGuiaDelivery();                                        // PUBLICO + DELIVERY

        TenantContext.setCurrentTenant(TENANT_A);
        try {
            Page<GuiaRemisionListResponse> page = greService.listar(
                    null, null, null, PageRequest.of(0, 20, Sort.by("correlativo").ascending()));

            assertThat(page.getTotalElements()).isEqualTo(2);

            GuiaRemisionListResponse sucursales = page.getContent().stream()
                    .filter(g -> "SUCURSALES".equals(g.getTipoTraslado().name()))
                    .findFirst().orElseThrow();
            GuiaRemisionListResponse delivery = page.getContent().stream()
                    .filter(g -> "DELIVERY".equals(g.getTipoTraslado().name()))
                    .findFirst().orElseThrow();

            assertThat(sucursales.getModalidadTransporte().name()).isEqualTo("PRIVADO");
            assertThat(sucursales.getSucursalOrigenNombre()).isEqualTo("Almacén Central");
            assertThat(sucursales.getSucursalDestinoNombre()).isEqualTo("Tienda Norte");

            assertThat(delivery.getModalidadTransporte().name()).isEqualTo("PUBLICO");
            assertThat(delivery.getDestinatarioRazonSocial()).isEqualTo("Cliente Delivery SAC");
        } finally {
            TenantContext.clear();
        }
    }
}
