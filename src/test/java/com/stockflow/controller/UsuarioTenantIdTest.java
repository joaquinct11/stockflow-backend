package com.stockflow.controller;

import com.stockflow.dto.CrearUsuarioResult;
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
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

/**
 * Verifica que tenantId en UsuarioDTO se hidrata desde TenantContext,
 * no desde Usuario (que ya no tiene esa columna tras V103).
 */
@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
@DisplayName("UsuarioController — tenantId hidratado desde TenantContext")
class UsuarioTenantIdTest {

    private static final String TENANT_VET   = "vet-ca35";
    private static final String TENANT_BOTICA = "botica-7f2a";
    private static final Long   USUARIO_ID   = 42L;
    private static final Long   SUCURSAL_19  = 19L;
    private static final String EMAIL        = "ana@test.com";

    @Mock private UsuarioService          usuarioService;
    @Mock private UsuarioMapper           usuarioMapper;
    @Mock private RolRepository           rolRepository;
    @Mock private PlanLimitService        planLimitService;
    @Mock private EmailService            emailService;
    @Mock private UsuarioTenantRepository usuarioTenantRepository;

    @InjectMocks private UsuarioController usuarioController;

    private Rol rolAdmin;
    private Rol rolVendedor;
    private Usuario usuario;
    private UsuarioTenant utVet;
    private UsuarioTenant utBotica;

    @BeforeEach
    void setUp() {
        rolAdmin    = Rol.builder().id(1L).nombre("ADMIN").build();
        rolVendedor = Rol.builder().id(2L).nombre("VENDEDOR").build();

        usuario = Usuario.builder()
                .id(USUARIO_ID).email(EMAIL).nombre("Ana").activo(true).build();

        utVet    = UsuarioTenant.builder().id(10L).usuario(usuario)
                .tenantId(TENANT_VET).rol(rolAdmin).sucursalId(SUCURSAL_19).activo(true).build();
        utBotica = UsuarioTenant.builder().id(11L).usuario(usuario)
                .tenantId(TENANT_BOTICA).rol(rolVendedor).sucursalId(null).activo(true).build();

        when(usuarioMapper.toDTO(any(Usuario.class))).thenAnswer(inv -> {
            Usuario u = inv.getArgument(0);
            return UsuarioDTO.builder().id(u.getId()).email(u.getEmail()).nombre(u.getNombre()).build();
        });
        doNothing().when(planLimitService).validarRolPermitido(anyString(), anyString());
        doNothing().when(planLimitService).validarLimiteUsuarios(anyString());
    }

    @AfterEach
    void tearDown() {
        TenantContext.clear();
    }

    // ── A: GET /usuarios → tenantId correcto ──────────────────────────────────

    @Test
    @DisplayName("A — GET /usuarios → tenantId hidratado desde TenantContext")
    void obtenerTodos_tenantIdHidratado() {
        TenantContext.setCurrentTenant(TENANT_VET);
        when(usuarioTenantRepository.findByTenantIdAndActivoTrue(TENANT_VET))
                .thenReturn(List.of(utVet));

        List<UsuarioDTO> result = usuarioController.obtenerTodos().getBody();

        assertThat(result).hasSize(1);
        assertThat(result.get(0).getTenantId()).isEqualTo(TENANT_VET);
    }

    // ── B: GET /usuarios → tenantId no nulo ───────────────────────────────────

    @Test
    @DisplayName("B — GET /usuarios → tenantId no es null")
    void obtenerTodos_tenantIdNoEsNull() {
        TenantContext.setCurrentTenant(TENANT_VET);
        when(usuarioTenantRepository.findByTenantIdAndActivoTrue(TENANT_VET))
                .thenReturn(List.of(utVet));

        List<UsuarioDTO> result = usuarioController.obtenerTodos().getBody();

        assertThat(result.get(0).getTenantId()).isNotNull();
    }

    // ── C: GET /usuarios/{id} → tenantId correcto ─────────────────────────────

    @Test
    @DisplayName("C — GET /usuarios/{id} → tenantId correcto desde TenantContext")
    void obtenerPorId_tenantIdCorrecto() {
        TenantContext.setCurrentTenant(TENANT_VET);
        when(usuarioTenantRepository.findByUsuarioIdAndTenantIdAndActivoTrue(USUARIO_ID, TENANT_VET))
                .thenReturn(Optional.of(utVet));

        ResponseEntity<UsuarioDTO> resp = usuarioController.obtenerPorId(USUARIO_ID);

        assertThat(resp.getBody()).isNotNull();
        assertThat(resp.getBody().getTenantId()).isEqualTo(TENANT_VET);
    }

    // ── D: GET /usuarios/email/{email} → tenantId correcto ────────────────────

    @Test
    @DisplayName("D — GET /usuarios/email/{email} → tenantId correcto desde TenantContext")
    void obtenerPorEmail_tenantIdCorrecto() {
        TenantContext.setCurrentTenant(TENANT_VET);
        when(usuarioService.obtenerUsuarioPorEmail(EMAIL)).thenReturn(Optional.of(usuario));
        when(usuarioTenantRepository.findByUsuarioIdAndTenantIdAndActivoTrue(USUARIO_ID, TENANT_VET))
                .thenReturn(Optional.of(utVet));

        ResponseEntity<UsuarioDTO> resp = usuarioController.obtenerPorEmail(EMAIL);

        assertThat(resp.getBody()).isNotNull();
        assertThat(resp.getBody().getTenantId()).isEqualTo(TENANT_VET);
    }

    // ── E: PUT /usuarios/{id} → tenantId correcto ─────────────────────────────

    @Test
    @DisplayName("E — PUT /usuarios/{id} → tenantId correcto en respuesta")
    void actualizar_tenantIdCorrecto() {
        TenantContext.setCurrentTenant(TENANT_VET);
        UsuarioUpdateDTO updateDTO = new UsuarioUpdateDTO();
        updateDTO.setRolNombre("ADMIN");
        updateDTO.setSucursalId(SUCURSAL_19);

        when(usuarioTenantRepository.findByUsuarioIdAndTenantIdAndActivoTrue(USUARIO_ID, TENANT_VET))
                .thenReturn(Optional.of(utVet));
        when(rolRepository.findByNombre("ADMIN")).thenReturn(Optional.of(rolAdmin));
        when(usuarioService.actualizarUsuario(eq(USUARIO_ID), any(UsuarioUpdateDTO.class), eq(rolAdmin)))
                .thenReturn(usuario);

        ResponseEntity<UsuarioDTO> resp = usuarioController.actualizar(USUARIO_ID, updateDTO);

        assertThat(resp.getBody()).isNotNull();
        assertThat(resp.getBody().getTenantId()).isEqualTo(TENANT_VET);
    }

    // ── F: tenantId aislado entre tenants ─────────────────────────────────────

    @Test
    @DisplayName("F — mismo usuario: tenantId refleja el tenant activo (aislamiento)")
    void obtenerTodos_tenantIdAisladoPorTenant() {
        TenantContext.setCurrentTenant(TENANT_VET);
        when(usuarioTenantRepository.findByTenantIdAndActivoTrue(TENANT_VET))
                .thenReturn(List.of(utVet));
        List<UsuarioDTO> enVet = usuarioController.obtenerTodos().getBody();

        TenantContext.setCurrentTenant(TENANT_BOTICA);
        when(usuarioTenantRepository.findByTenantIdAndActivoTrue(TENANT_BOTICA))
                .thenReturn(List.of(utBotica));
        List<UsuarioDTO> enBotica = usuarioController.obtenerTodos().getBody();

        assertThat(enVet.get(0).getTenantId()).isEqualTo(TENANT_VET);
        assertThat(enBotica.get(0).getTenantId()).isEqualTo(TENANT_BOTICA);
    }

    // ── G: tenantId nunca proviene de Usuario (usuario.tenantId no existe) ─────

    @Test
    @DisplayName("G — tenantId en DTO no proviene de Usuario sino de TenantContext")
    void tenantIdNoVieneDeLaEntidadUsuario() {
        // Usuario no tiene campo tenantId — el DTO obtiene el valor solo si el controller lo setea
        TenantContext.setCurrentTenant(TENANT_VET);
        when(usuarioTenantRepository.findByTenantIdAndActivoTrue(TENANT_VET))
                .thenReturn(List.of(utVet));

        List<UsuarioDTO> result = usuarioController.obtenerTodos().getBody();

        // El mapper devuelve un DTO sin tenantId; solo el controller puede haberlo seteado
        assertThat(result.get(0).getTenantId()).isNotNull();
        assertThat(result.get(0).getTenantId()).isEqualTo(TENANT_VET);
    }

    // ── H: lista vacía no explota ─────────────────────────────────────────────

    @Test
    @DisplayName("H — lista vacía devuelve lista vacía sin NPE")
    void obtenerTodos_listaVacia() {
        TenantContext.setCurrentTenant(TENANT_VET);
        when(usuarioTenantRepository.findByTenantIdAndActivoTrue(TENANT_VET))
                .thenReturn(List.of());

        List<UsuarioDTO> result = usuarioController.obtenerTodos().getBody();

        assertThat(result).isEmpty();
    }

    // ── I: todos los campos esenciales presentes en un solo request ───────────

    @Test
    @DisplayName("I — GET /usuarios/{id} devuelve tenantId, rolNombre y sucursalId juntos")
    void obtenerPorId_todosLosCamposContextualesPresentes() {
        TenantContext.setCurrentTenant(TENANT_VET);
        when(usuarioTenantRepository.findByUsuarioIdAndTenantIdAndActivoTrue(USUARIO_ID, TENANT_VET))
                .thenReturn(Optional.of(utVet));

        UsuarioDTO body = usuarioController.obtenerPorId(USUARIO_ID).getBody();

        assertThat(body).isNotNull();
        assertThat(body.getTenantId()).isEqualTo(TENANT_VET);
        assertThat(body.getRolNombre()).isEqualTo("ADMIN");
        assertThat(body.getSucursalId()).isEqualTo(SUCURSAL_19);
    }
}
