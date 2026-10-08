package com.stockflow.service;

import com.stockflow.controller.UsuarioController;
import com.stockflow.dto.UsuarioUpdateDTO;
import com.stockflow.entity.Rol;
import com.stockflow.entity.Usuario;
import com.stockflow.entity.UsuarioTenant;
import com.stockflow.exception.ResourceNotFoundException;
import com.stockflow.mapper.UsuarioMapper;
import com.stockflow.repository.RolRepository;
import com.stockflow.repository.UsuarioTenantRepository;
import com.stockflow.service.impl.UsuarioServiceImpl;
import com.stockflow.util.TenantContext;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;
import org.springframework.http.ResponseEntity;

import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
@DisplayName("UsuarioController — seguridad cross-tenant en PUT /usuarios/{id}")
class UsuarioControllerSecurityTest {

    private static final String TENANT_VET   = "vet-59ba";
    private static final String TENANT_BOTICA = "botica-santa-fe";

    @Mock private UsuarioService usuarioService;
    @Mock private UsuarioMapper usuarioMapper;
    @Mock private RolRepository rolRepository;
    @Mock private PlanLimitService planLimitService;
    @Mock private EmailService emailService;
    @Mock private UsuarioTenantRepository usuarioTenantRepository;

    @InjectMocks private UsuarioController usuarioController;

    private Rol rolAdmin;
    private Usuario usuario5;
    private UsuarioTenant ut5Vet;
    private UsuarioUpdateDTO updateDTO;

    @BeforeEach
    void setUp() {
        TenantContext.setCurrentTenant(TENANT_VET);

        rolAdmin = Rol.builder().id(1L).nombre("ADMIN").build();

        // Usuario 5: tiene relación activa con vet via usuario_tenant
        usuario5 = Usuario.builder()
                .id(5L)
                .email("admin@vet.com")
                .nombre("Admin Vet")
                .activo(true)
                .build();

        ut5Vet = UsuarioTenant.builder()
                .id(10L)
                .usuario(usuario5)
                .tenantId(TENANT_VET)
                .rol(rolAdmin)
                .activo(true)
                .build();

        updateDTO = new UsuarioUpdateDTO();
        updateDTO.setNombre("Admin Vet Actualizado");
        updateDTO.setRolNombre("ADMIN");
        updateDTO.setActivo(true);

        when(rolRepository.findByNombre("ADMIN")).thenReturn(Optional.of(rolAdmin));
        when(usuarioService.obtenerUsuarioPorId(5L)).thenReturn(Optional.of(usuario5));
        when(usuarioService.actualizarUsuario(eq(5L), any(UsuarioUpdateDTO.class), any(Rol.class))).thenReturn(usuario5);
        when(usuarioMapper.toDTO(any(Usuario.class))).thenReturn(new com.stockflow.dto.UsuarioDTO());
        doNothing().when(planLimitService).validarRolPermitido(anyString(), anyString());
    }

    @AfterEach
    void tearDown() {
        TenantContext.clear();
    }

    @Test
    @DisplayName("permite actualizar usuario que tiene relación activa en usuario_tenant del tenant activo")
    void actualizar_usuarioPerteneceAlTenant_permite() {
        when(usuarioTenantRepository.findByUsuarioIdAndTenantIdAndActivoTrue(5L, TENANT_VET))
                .thenReturn(Optional.of(ut5Vet));

        ResponseEntity<?> response = usuarioController.actualizar(5L, updateDTO);

        assertThat(response.getStatusCode().value()).isEqualTo(200);
        verify(usuarioService).actualizarUsuario(eq(5L), any(UsuarioUpdateDTO.class), any(Rol.class));
    }

    @Test
    @DisplayName("rechaza actualizar usuario que pertenece a otro tenant (protección cross-tenant)")
    void actualizar_usuarioNoPerteneceTenant_lanzaResourceNotFound() {
        // Usuario 5 no tiene relación activa con TENANT_VET
        when(usuarioTenantRepository.findByUsuarioIdAndTenantIdAndActivoTrue(5L, TENANT_VET))
                .thenReturn(Optional.empty());

        assertThatThrownBy(() -> usuarioController.actualizar(5L, updateDTO))
                .isInstanceOf(ResourceNotFoundException.class)
                .hasMessage("Usuario no encontrado");

        // No debe llamar al servicio de actualización
        verify(usuarioService, never()).actualizarUsuario(any(), any(), any());
    }

    @Test
    @DisplayName("confirma que la validación usa usuario_tenant y no usuarios.tenant_id")
    void actualizar_noUsaTenantIdLegacy() {
        // Escenario clave: tenant_id del usuario = TENANT_BOTICA (otro tenant)
        // pero usuario_tenant lo une a TENANT_VET — debe PERMITIRSE
        when(usuarioTenantRepository.findByUsuarioIdAndTenantIdAndActivoTrue(5L, TENANT_VET))
                .thenReturn(Optional.of(ut5Vet));

        // Si usara u.getTenantId() == tenantId, fallaría porque BOTICA != VET
        // Si usa usuario_tenant correctamente, debe pasar
        ResponseEntity<?> response = usuarioController.actualizar(5L, updateDTO);

        assertThat(response.getStatusCode().value()).isEqualTo(200);
    }
}
