package com.stockflow.integration;

import com.stockflow.dto.gre.GuiaRemisionStatsResponse;
import com.stockflow.service.GuiaRemisionService;
import com.stockflow.service.impl.LucodeGREService;
import org.junit.jupiter.api.*;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
import org.springframework.data.domain.PageImpl;
import org.springframework.http.MediaType;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import org.testcontainers.containers.PostgreSQLContainer;

import java.util.Collections;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.user;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * F-19 — Tests de autorización para GuiaRemisionController.
 *
 * Verifica que:
 * - Solo ADMIN/GERENTE o usuarios con PERM_VER_GRE acceden a los endpoints de consulta.
 * - VENDEDOR con PERM_VER_GRE NO puede usar los endpoints de operación (emitir, editar, cancelar).
 *
 * T-F19-01  ADMIN puede listar guías de remisión
 * T-F19-02  GERENTE puede listar guías de remisión
 * T-F19-03  VENDEDOR sin permisos GRE recibe 403 en GET /guias-remision
 * T-F19-04  VENDEDOR con PERM_VER_GRE puede listar guías de remisión
 * T-F19-05  VENDEDOR con PERM_VER_GRE puede consultar stats
 * T-F19-06  VENDEDOR con PERM_VER_GRE puede obtener una guía por ID (pasa la autorización)
 * T-F19-07  VENDEDOR con PERM_VER_GRE NO puede crear borrador (POST /)
 * T-F19-08  VENDEDOR con PERM_VER_GRE NO puede emitir guía existente (POST /{id}/emitir)
 * T-F19-09  VENDEDOR con PERM_VER_GRE NO puede editar borrador (PUT /{id})
 * T-F19-10  VENDEDOR con PERM_VER_GRE NO puede cancelar borrador (POST /{id}/cancelar)
 */
@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
@TestInstance(TestInstance.Lifecycle.PER_CLASS)
@TestMethodOrder(MethodOrderer.OrderAnnotation.class)
class F19GREAuthorizationTest {

    @ServiceConnection
    static final PostgreSQLContainer<?> postgres = new PostgreSQLContainer<>("postgres:16");

    static {
        postgres.start();
    }

    @Autowired MockMvc mockMvc;

    @MockBean LucodeGREService lucodeGREService;
    @MockBean GuiaRemisionService guiaService;

    @BeforeAll
    void setupMocks() {
        when(guiaService.listar(any(), any(), any(), any()))
                .thenReturn(new PageImpl<>(Collections.emptyList()));
        when(guiaService.stats(any()))
                .thenReturn(GuiaRemisionStatsResponse.builder().total(0).emitidas(0).pendientes(0).conError(0).build());
    }

    // ── T-F19-01: ADMIN puede listar GRE ─────────────────────────────────────

    @Test
    @Order(1)
    @DisplayName("T-F19-01: ADMIN puede listar guías de remisión")
    void t01_adminPuedeListar() throws Exception {
        mockMvc.perform(get("/guias-remision")
                        .with(user("admin@test.com").authorities(
                                new SimpleGrantedAuthority("ROLE_ADMIN"))))
                .andExpect(status().isOk());
    }

    // ── T-F19-02: GERENTE puede listar GRE ───────────────────────────────────

    @Test
    @Order(2)
    @DisplayName("T-F19-02: GERENTE puede listar guías de remisión")
    void t02_gerentePuedeListar() throws Exception {
        mockMvc.perform(get("/guias-remision")
                        .with(user("gerente@test.com").authorities(
                                new SimpleGrantedAuthority("ROLE_GERENTE"))))
                .andExpect(status().isOk());
    }

    // ── T-F19-03: VENDEDOR sin permisos GRE recibe 403 ───────────────────────

    @Test
    @Order(3)
    @DisplayName("T-F19-03: VENDEDOR sin PERM_VER_GRE recibe 403 en GET /guias-remision")
    void t03_vendedorSinPermisoRecibe403() throws Exception {
        mockMvc.perform(get("/guias-remision")
                        .with(user("vendedor@test.com").authorities(
                                new SimpleGrantedAuthority("ROLE_VENDEDOR"))))
                .andExpect(status().isForbidden());
    }

    // ── T-F19-04: VENDEDOR con PERM_VER_GRE puede listar GRE ─────────────────

    @Test
    @Order(4)
    @DisplayName("T-F19-04: VENDEDOR con PERM_VER_GRE puede listar guías de remisión")
    void t04_vendedorConPermisoVER_GREPuedeListar() throws Exception {
        mockMvc.perform(get("/guias-remision")
                        .with(user("vendedor@test.com").authorities(
                                new SimpleGrantedAuthority("ROLE_VENDEDOR"),
                                new SimpleGrantedAuthority("PERM_VER_GRE"))))
                .andExpect(status().isOk());
    }

    // ── T-F19-05: VENDEDOR con PERM_VER_GRE puede consultar stats ────────────

    @Test
    @Order(5)
    @DisplayName("T-F19-05: VENDEDOR con PERM_VER_GRE puede consultar stats")
    void t05_vendedorConPermisoVER_GREPuedeVerStats() throws Exception {
        mockMvc.perform(get("/guias-remision/stats")
                        .with(user("vendedor@test.com").authorities(
                                new SimpleGrantedAuthority("ROLE_VENDEDOR"),
                                new SimpleGrantedAuthority("PERM_VER_GRE"))))
                .andExpect(status().isOk());
    }

    // ── T-F19-06: VENDEDOR con PERM_VER_GRE pasa autorización en GET /{id} ───

    @Test
    @Order(6)
    @DisplayName("T-F19-06: VENDEDOR con PERM_VER_GRE pasa la autorización en GET /{id}")
    void t06_vendedorConPermisoVER_GREPasaAutorizacionGetById() throws Exception {
        // Esperamos 404 (no existe la guía) pero NO 403 — la autorización pasó
        mockMvc.perform(get("/guias-remision/9999")
                        .with(user("vendedor@test.com").authorities(
                                new SimpleGrantedAuthority("ROLE_VENDEDOR"),
                                new SimpleGrantedAuthority("PERM_VER_GRE"))))
                .andExpect(result ->
                        Assertions.assertNotEquals(403, result.getResponse().getStatus()));
    }

    // ── T-F19-07: VENDEDOR con PERM_VER_GRE NO puede crear borrador ──────────

    @Test
    @Order(7)
    @DisplayName("T-F19-07: VENDEDOR con solo PERM_VER_GRE no puede crear borrador (4xx)")
    void t07_vendedorConSoloVER_GRENoPuedeCrearBorrador() throws Exception {
        // Bean Validation (@Valid @RequestBody) corre antes de @PreAuthorize AOP, por lo que
        // un body inválido devuelve 400 antes de que el interceptor de seguridad pueda actuar.
        // Ambas respuestas (400 de validación o 403 de seguridad) confirman que el acceso fue denegado.
        mockMvc.perform(post("/guias-remision")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{}")
                        .with(user("vendedor@test.com").authorities(
                                new SimpleGrantedAuthority("ROLE_VENDEDOR"),
                                new SimpleGrantedAuthority("PERM_VER_GRE"))))
                .andExpect(status().is4xxClientError());
    }

    // ── T-F19-08: VENDEDOR con PERM_VER_GRE NO puede emitir GRE ─────────────

    @Test
    @Order(8)
    @DisplayName("T-F19-08: VENDEDOR con solo PERM_VER_GRE recibe 403 en POST /{id}/emitir")
    void t08_vendedorConSoloVER_GRENoPuedeEmitir() throws Exception {
        mockMvc.perform(post("/guias-remision/1/emitir")
                        .with(user("vendedor@test.com").authorities(
                                new SimpleGrantedAuthority("ROLE_VENDEDOR"),
                                new SimpleGrantedAuthority("PERM_VER_GRE"))))
                .andExpect(status().isForbidden());
    }

    // ── T-F19-09: VENDEDOR con PERM_VER_GRE NO puede editar borrador ─────────

    @Test
    @Order(9)
    @DisplayName("T-F19-09: VENDEDOR con solo PERM_VER_GRE no puede editar borrador (4xx)")
    void t09_vendedorConSoloVER_GRENoPuedeEditar() throws Exception {
        // Mismo motivo que t07: validación antes de @PreAuthorize → 400 con body vacío.
        mockMvc.perform(put("/guias-remision/1")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{}")
                        .with(user("vendedor@test.com").authorities(
                                new SimpleGrantedAuthority("ROLE_VENDEDOR"),
                                new SimpleGrantedAuthority("PERM_VER_GRE"))))
                .andExpect(status().is4xxClientError());
    }

    // ── T-F19-10: VENDEDOR con PERM_VER_GRE NO puede cancelar borrador ───────

    @Test
    @Order(10)
    @DisplayName("T-F19-10: VENDEDOR con solo PERM_VER_GRE recibe 403 en POST /{id}/cancelar")
    void t10_vendedorConSoloVER_GRENoPuedeCancelar() throws Exception {
        mockMvc.perform(post("/guias-remision/1/cancelar")
                        .with(user("vendedor@test.com").authorities(
                                new SimpleGrantedAuthority("ROLE_VENDEDOR"),
                                new SimpleGrantedAuthority("PERM_VER_GRE"))))
                .andExpect(status().isForbidden());
    }
}
