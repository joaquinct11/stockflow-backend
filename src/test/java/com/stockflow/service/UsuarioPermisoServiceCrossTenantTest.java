package com.stockflow.service;

import com.stockflow.entity.Permiso;
import com.stockflow.entity.Usuario;
import com.stockflow.exception.ForbiddenException;
import com.stockflow.repository.PermisoRepository;
import com.stockflow.repository.UsuarioPermisoRepository;
import com.stockflow.repository.UsuarioRepository;
import com.stockflow.repository.UsuarioTenantRepository;
import com.stockflow.service.impl.UsuarioPermisoServiceImpl;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;

import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.*;

/**
 * Verifica el aislamiento multi-tenant en UsuarioPermisoServiceImpl.asignarPermisos:
 * - No se pueden asignar permisos a un usuario de otro tenant.
 * - Se pueden asignar permisos a un usuario del tenant correcto.
 */
@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
@DisplayName("UsuarioPermisoService — aislamiento multi-tenant en asignarPermisos")
class UsuarioPermisoServiceCrossTenantTest {

    private static final String TENANT_A   = "tenant-a-9f2c";
    private static final String TENANT_B   = "tenant-b-1d4e";
    private static final Long   USUARIO_ID = 77L;

    @Mock private UsuarioPermisoRepository usuarioPermisoRepository;
    @Mock private UsuarioRepository        usuarioRepository;
    @Mock private PermisoRepository        permisoRepository;
    @Mock private UsuarioTenantRepository  usuarioTenantRepository;

    @InjectMocks private UsuarioPermisoServiceImpl service;

    // ── A: usuario no pertenece al tenant → ForbiddenException ───────────────

    @Test
    @DisplayName("A — asignar permisos a usuario de otro tenant lanza ForbiddenException")
    void asignarPermisos_usuarioOtroTenant_lanzaForbidden() {
        // Usuario 77 NO pertenece al tenant B
        when(usuarioTenantRepository.existsByUsuarioIdAndTenantIdAndActivoTrue(USUARIO_ID, TENANT_B))
                .thenReturn(false);

        assertThatThrownBy(() ->
                service.asignarPermisos(USUARIO_ID, List.of("VER_COSTO_PRODUCTO"), TENANT_B)
        )
                .isInstanceOf(ForbiddenException.class)
                .hasMessageContaining("no pertenece al tenant");

        // Nunca debe tocar la tabla usuario_permisos
        verify(usuarioPermisoRepository, never()).deleteByUsuarioIdAndTenantId(any(), any());
        verify(usuarioPermisoRepository, never()).saveAll(any());
    }

    // ── B: usuario del tenant correcto → operación completa ──────────────────

    @Test
    @DisplayName("B — asignar permisos a usuario del tenant correcto ejecuta sin errores")
    void asignarPermisos_usuarioMismoTenant_ejecutaCorrectamente() {
        Usuario usuario = Usuario.builder().id(USUARIO_ID).email("v@test.com").nombre("Victor").activo(true).build();
        Permiso permiso = Permiso.builder().id(1L).nombre("VER_COSTO_PRODUCTO").build();

        when(usuarioTenantRepository.existsByUsuarioIdAndTenantIdAndActivoTrue(USUARIO_ID, TENANT_A))
                .thenReturn(true);
        when(usuarioRepository.findById(USUARIO_ID)).thenReturn(Optional.of(usuario));
        when(permisoRepository.findByNombreIn(List.of("VER_COSTO_PRODUCTO")))
                .thenReturn(List.of(permiso));

        service.asignarPermisos(USUARIO_ID, List.of("VER_COSTO_PRODUCTO"), TENANT_A);

        verify(usuarioPermisoRepository).deleteByUsuarioIdAndTenantId(USUARIO_ID, TENANT_A);
        verify(usuarioPermisoRepository).saveAll(any());
    }

    // ── C: lista vacía con usuario del tenant → elimina todos sin error ───────

    @Test
    @DisplayName("C — lista vacía con usuario del tenant elimina todos los permisos")
    void asignarPermisos_listaVacia_eliminaTodos() {
        Usuario usuario = Usuario.builder().id(USUARIO_ID).email("v@test.com").nombre("Victor").activo(true).build();

        when(usuarioTenantRepository.existsByUsuarioIdAndTenantIdAndActivoTrue(USUARIO_ID, TENANT_A))
                .thenReturn(true);
        when(usuarioRepository.findById(USUARIO_ID)).thenReturn(Optional.of(usuario));

        service.asignarPermisos(USUARIO_ID, List.of(), TENANT_A);

        verify(usuarioPermisoRepository).deleteByUsuarioIdAndTenantId(USUARIO_ID, TENANT_A);
        verify(usuarioPermisoRepository, never()).saveAll(any());
    }
}
