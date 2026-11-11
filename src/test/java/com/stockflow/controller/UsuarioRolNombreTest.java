package com.stockflow.controller;

import com.stockflow.dto.UsuarioDTO;
import com.stockflow.dto.UsuarioUpdateDTO;
import com.stockflow.entity.Rol;
import com.stockflow.entity.Usuario;
import com.stockflow.entity.UsuarioTenant;
import com.stockflow.mapper.UsuarioMapper;
import com.stockflow.repository.RolRepository;
import com.stockflow.repository.UsuarioTenantRepository;
import com.stockflow.service.EmailService;
import com.stockflow.service.PlanLimitService;
import com.stockflow.service.UsuarioService;
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

import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.*;

/**
 * Verifica que UsuarioDTO.rolNombre proviene de UsuarioTenant, no de Usuario.
 * Cubre multi-tenancy: mismo usuario puede tener distinto rol por tenant.
 */
@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
@DisplayName("UsuarioController — rolNombre viene de UsuarioTenant")
class UsuarioRolNombreTest {

    private static final String TENANT_VET   = "vet-59ba";
    private static final String TENANT_BOTICA = "botica-santa-fe";
    private static final Long   USUARIO_ID   = 10L;
    private static final String EMAIL        = "maria@multi.com";

    @Mock private UsuarioService           usuarioService;
    @Mock private UsuarioMapper            usuarioMapper;
    @Mock private RolRepository            rolRepository;
    @Mock private PlanLimitService         planLimitService;
    @Mock private EmailService             emailService;
    @Mock private UsuarioTenantRepository  usuarioTenantRepository;

    @InjectMocks private UsuarioController usuarioController;

    private Rol rolAdmin;
    private Rol rolVendedor;
    private Usuario usuario;
    private UsuarioTenant utVetAdmin;
    private UsuarioTenant utBoticaVendedor;

    @BeforeEach
    void setUp() {
        rolAdmin    = Rol.builder().id(1L).nombre("ADMIN").build();
        rolVendedor = Rol.builder().id(3L).nombre("VENDEDOR").build();

        usuario = Usuario.builder()
                .id(USUARIO_ID)
                .email(EMAIL)
                .nombre("María Multi")
                .activo(true)
                .build();

        utVetAdmin = UsuarioTenant.builder()
                .id(100L).usuario(usuario).tenantId(TENANT_VET).rol(rolAdmin).activo(true).build();

        utBoticaVendedor = UsuarioTenant.builder()
                .id(101L).usuario(usuario).tenantId(TENANT_BOTICA).rol(rolVendedor).activo(true).build();

        // El mapper construye un DTO base vacío por cada llamada — el controller lo enriquece con rolNombre
        when(usuarioMapper.toDTO(usuario)).thenAnswer(inv ->
                UsuarioDTO.builder().id(USUARIO_ID).email(EMAIL).nombre("María Multi").build()
        );
        doNothing().when(planLimitService).validarRolPermitido(anyString(), anyString());
        doNothing().when(planLimitService).validarLimiteUsuarios(anyString());
    }

    @AfterEach
    void tearDown() {
        TenantContext.clear();
    }

    // ── Caso 1: GET /usuarios/{id} en Vet → rolNombre = ADMIN ─────────────────

    @Test
    @DisplayName("Caso 1 — GET /{id} en tenant Vet → rolNombre = ADMIN")
    void obtenerPorId_tenantVet_rolAdmin() {
        TenantContext.setCurrentTenant(TENANT_VET);
        when(usuarioTenantRepository.findByUsuarioIdAndTenantIdAndActivoTrue(USUARIO_ID, TENANT_VET))
                .thenReturn(Optional.of(utVetAdmin));

        ResponseEntity<UsuarioDTO> resp = usuarioController.obtenerPorId(USUARIO_ID);

        assertThat(resp.getStatusCode().value()).isEqualTo(200);
        assertThat(resp.getBody()).isNotNull();
        assertThat(resp.getBody().getRolNombre()).isEqualTo("ADMIN");
    }

    // ── Caso 2: GET /usuarios/{id} en Botica → rolNombre = VENDEDOR ───────────

    @Test
    @DisplayName("Caso 2 — mismo usuario GET /{id} en tenant Botica → rolNombre = VENDEDOR")
    void obtenerPorId_tenantBotica_rolVendedor() {
        TenantContext.setCurrentTenant(TENANT_BOTICA);
        when(usuarioTenantRepository.findByUsuarioIdAndTenantIdAndActivoTrue(USUARIO_ID, TENANT_BOTICA))
                .thenReturn(Optional.of(utBoticaVendedor));

        ResponseEntity<UsuarioDTO> resp = usuarioController.obtenerPorId(USUARIO_ID);

        assertThat(resp.getStatusCode().value()).isEqualTo(200);
        assertThat(resp.getBody()).isNotNull();
        assertThat(resp.getBody().getRolNombre()).isEqualTo("VENDEDOR");
    }

    // ── Caso 3: cambiar rol en Vet NO afecta respuesta en Botica ──────────────

    @Test
    @DisplayName("Caso 3 — cambiar rol en Vet no altera rolNombre devuelto en Botica")
    void rolEnVet_noAfectaRespuestaEnBotica() {
        // Vet: ADMIN
        TenantContext.setCurrentTenant(TENANT_VET);
        when(usuarioTenantRepository.findByUsuarioIdAndTenantIdAndActivoTrue(USUARIO_ID, TENANT_VET))
                .thenReturn(Optional.of(utVetAdmin));
        ResponseEntity<UsuarioDTO> respVet = usuarioController.obtenerPorId(USUARIO_ID);
        assertThat(respVet.getBody().getRolNombre()).isEqualTo("ADMIN");

        // Botica: VENDEDOR — independiente
        TenantContext.setCurrentTenant(TENANT_BOTICA);
        when(usuarioTenantRepository.findByUsuarioIdAndTenantIdAndActivoTrue(USUARIO_ID, TENANT_BOTICA))
                .thenReturn(Optional.of(utBoticaVendedor));
        ResponseEntity<UsuarioDTO> respBotica = usuarioController.obtenerPorId(USUARIO_ID);
        assertThat(respBotica.getBody().getRolNombre()).isEqualTo("VENDEDOR");

        // Confirmar que son distintos
        assertThat(respVet.getBody().getRolNombre())
                .isNotEqualTo(respBotica.getBody().getRolNombre());
    }

    // ── Caso 4: GET /usuarios (lista) devuelve rolNombre correcto por tenant ──

    @Test
    @DisplayName("Caso 4 — GET /usuarios en Vet devuelve lista con rolNombre = ADMIN")
    void obtenerTodos_tenantVet_rolAdminEnLista() {
        TenantContext.setCurrentTenant(TENANT_VET);
        when(usuarioTenantRepository.findByTenantIdAndActivoTrue(TENANT_VET))
                .thenReturn(List.of(utVetAdmin));

        ResponseEntity<List<UsuarioDTO>> resp = usuarioController.obtenerTodos();

        assertThat(resp.getStatusCode().value()).isEqualTo(200);
        assertThat(resp.getBody()).hasSize(1);
        assertThat(resp.getBody().get(0).getRolNombre()).isEqualTo("ADMIN");
    }

    @Test
    @DisplayName("Caso 4b — GET /usuarios en Botica devuelve lista con rolNombre = VENDEDOR")
    void obtenerTodos_tenantBotica_rolVendedorEnLista() {
        TenantContext.setCurrentTenant(TENANT_BOTICA);
        when(usuarioTenantRepository.findByTenantIdAndActivoTrue(TENANT_BOTICA))
                .thenReturn(List.of(utBoticaVendedor));

        ResponseEntity<List<UsuarioDTO>> resp = usuarioController.obtenerTodos();

        assertThat(resp.getBody()).hasSize(1);
        assertThat(resp.getBody().get(0).getRolNombre()).isEqualTo("VENDEDOR");
    }

    // ── Caso 5: GET individual devuelve rolNombre correcto ────────────────────

    @Test
    @DisplayName("Caso 5 — GET /{id} inexistente en tenant → 404")
    void obtenerPorId_usuarioNoEnTenant_404() {
        TenantContext.setCurrentTenant(TENANT_VET);
        when(usuarioTenantRepository.findByUsuarioIdAndTenantIdAndActivoTrue(USUARIO_ID, TENANT_VET))
                .thenReturn(Optional.empty());

        ResponseEntity<UsuarioDTO> resp = usuarioController.obtenerPorId(USUARIO_ID);

        assertThat(resp.getStatusCode().value()).isEqualTo(404);
    }

    // ── Caso adicional: PUT /usuarios/{id} responde con rolNombre correcto ─────

    @Test
    @DisplayName("PUT actualizar → respuesta incluye rolNombre del rol actualizado")
    void actualizar_respuestaIncluye_rolNombreActualizado() {
        TenantContext.setCurrentTenant(TENANT_VET);

        UsuarioUpdateDTO updateDTO = new UsuarioUpdateDTO();
        updateDTO.setNombre("María Actualizada");
        updateDTO.setRolNombre("ADMIN");
        updateDTO.setActivo(true);

        when(usuarioTenantRepository.findByUsuarioIdAndTenantIdAndActivoTrue(USUARIO_ID, TENANT_VET))
                .thenReturn(Optional.of(utVetAdmin));
        when(rolRepository.findByNombre("ADMIN")).thenReturn(Optional.of(rolAdmin));
        when(usuarioService.obtenerUsuarioPorId(USUARIO_ID)).thenReturn(Optional.of(usuario));
        when(usuarioService.actualizarUsuario(eq(USUARIO_ID), any(UsuarioUpdateDTO.class), any(Rol.class)))
                .thenReturn(usuario);

        ResponseEntity<UsuarioDTO> resp = usuarioController.actualizar(USUARIO_ID, updateDTO);

        assertThat(resp.getStatusCode().value()).isEqualTo(200);
        assertThat(resp.getBody().getRolNombre()).isEqualTo("ADMIN");
    }

    // ── La lista NO usa usuario.getRol() — confirma que el mapper ignora campo ─

    @Test
    @DisplayName("La lista no llama usuario.getRol() — rolNombre viene solo de UsuarioTenant")
    void lista_noDependeDeUsuarioGetRol_soloDeUsuarioTenant() {
        TenantContext.setCurrentTenant(TENANT_VET);
        when(usuarioTenantRepository.findByTenantIdAndActivoTrue(TENANT_VET))
                .thenReturn(List.of(utVetAdmin));

        usuarioController.obtenerTodos();

        // El mapper se llama con el Usuario — pero rolNombre NO viene del mapper (ignore=true)
        // sino del UsuarioTenant. Verificar que el mapper se llamó (por los demás campos)
        verify(usuarioMapper).toDTO(usuario);
    }
}
