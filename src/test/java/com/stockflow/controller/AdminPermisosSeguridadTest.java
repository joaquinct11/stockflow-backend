package com.stockflow.controller;

import com.stockflow.config.RolePermissionDefaults;
import com.stockflow.exception.ForbiddenException;
import com.stockflow.mapper.UsuarioMapper;
import com.stockflow.repository.UsuarioTenantRepository;
import com.stockflow.service.PermisoService;
import com.stockflow.service.UsuarioPermisoService;
import com.stockflow.util.TenantContext;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.*;

/**
 * Verifica los guards de seguridad del endpoint PUT /admin/usuarios/{id}/permisos:
 * - Un ADMIN no puede modificar sus propios permisos.
 * - Un ADMIN puede modificar los permisos de otro usuario del mismo tenant.
 */
@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
@DisplayName("AdminController — guards de seguridad en asignación de permisos")
class AdminPermisosSeguridadTest {

    private static final String TENANT_ID  = "acme-t9x1";
    private static final Long   ADMIN_ID   = 10L;
    private static final Long   VENDEDOR_ID = 55L;

    @Mock private PermisoService          permisoService;
    @Mock private UsuarioPermisoService   usuarioPermisoService;
    @Mock private UsuarioMapper           usuarioMapper;
    @Mock private UsuarioTenantRepository usuarioTenantRepository;
    @Mock private RolePermissionDefaults  rolePermissionDefaults;

    @InjectMocks private AdminController adminController;

    @AfterEach
    void tearDown() {
        TenantContext.clear();
    }

    // ── A: ADMIN no puede modificar sus propios permisos ─────────────────────

    @Test
    @DisplayName("A — ADMIN que llama el endpoint con su propio ID recibe ForbiddenException")
    void adminNoModificaSusPropiasPermisos() {
        TenantContext.setCurrentTenant(TENANT_ID);
        TenantContext.setCurrentUserId(ADMIN_ID);

        assertThatThrownBy(() ->
                adminController.asignarPermisosAUsuario(ADMIN_ID, List.of("VER_COSTO_PRODUCTO"))
        )
                .isInstanceOf(ForbiddenException.class)
                .hasMessageContaining("No puedes modificar tus propios permisos");

        // El servicio nunca debe llamarse
        verify(usuarioPermisoService, never()).asignarPermisos(any(), any(), any());
    }

    // ── B: ADMIN puede modificar los permisos de otro usuario ────────────────

    @Test
    @DisplayName("B — ADMIN puede asignar permisos a otro usuario del tenant")
    void adminModificaPermisosDeOtroUsuario() {
        TenantContext.setCurrentTenant(TENANT_ID);
        TenantContext.setCurrentUserId(ADMIN_ID);

        when(usuarioPermisoService.obtenerPermisosCodigos(VENDEDOR_ID, TENANT_ID))
                .thenReturn(List.of("VER_COSTO_PRODUCTO"));

        var response = adminController.asignarPermisosAUsuario(
                VENDEDOR_ID, List.of("VER_COSTO_PRODUCTO"));

        assertThat(response.getStatusCode().value()).isEqualTo(200);
        verify(usuarioPermisoService).asignarPermisos(VENDEDOR_ID, List.of("VER_COSTO_PRODUCTO"), TENANT_ID);
    }

    // ── C: La validación usa el ID del contexto, no uno del request ──────────

    @Test
    @DisplayName("C — el ID del admin viene de TenantContext, no del cliente")
    void adminIdVieneDeTenantContext_noDelCliente() {
        // Admin 10 está autenticado pero envía una solicitud contra ID 10 (sí mismo)
        TenantContext.setCurrentTenant(TENANT_ID);
        TenantContext.setCurrentUserId(ADMIN_ID);

        // Aunque el body sea válido, el path var coincide con el admin → 403
        assertThatThrownBy(() ->
                adminController.asignarPermisosAUsuario(ADMIN_ID, List.of())
        ).isInstanceOf(ForbiddenException.class);
    }

    // ── D: Diferentes admins, mismo ID objetivo → el ID del contexto es el que manda ──

    @Test
    @DisplayName("D — un segundo admin (ID 20) puede modificar al usuario 55 sin bloqueo")
    void segundoAdminPuedeModificarMismoObjetivo() {
        Long otroAdminId = 20L;
        TenantContext.setCurrentTenant(TENANT_ID);
        TenantContext.setCurrentUserId(otroAdminId);  // admin diferente

        when(usuarioPermisoService.obtenerPermisosCodigos(VENDEDOR_ID, TENANT_ID))
                .thenReturn(List.of());

        var response = adminController.asignarPermisosAUsuario(VENDEDOR_ID, List.of());

        assertThat(response.getStatusCode().value()).isEqualTo(200);
    }
}
