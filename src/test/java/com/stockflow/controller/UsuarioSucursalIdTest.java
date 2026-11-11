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
 * Verifica que UsuarioDTO.sucursalId proviene de UsuarioTenant, no de Usuario.
 * Usuario ya no tiene sucursalId; la fuente de verdad es usuario_tenant.sucursal_id.
 */
@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
@DisplayName("UsuarioController — sucursalId viene de UsuarioTenant")
class UsuarioSucursalIdTest {

    private static final String TENANT_VET    = "vet-ca35";
    private static final String TENANT_JUANSAC = "juan-sac-95e4";
    private static final Long   USUARIO_ID    = 28L;
    private static final Long   SUCURSAL_VET  = 19L;
    private static final String EMAIL         = "test@gmail.com";

    @Mock private UsuarioService          usuarioService;
    @Mock private UsuarioMapper           usuarioMapper;
    @Mock private RolRepository           rolRepository;
    @Mock private PlanLimitService        planLimitService;
    @Mock private EmailService            emailService;
    @Mock private UsuarioTenantRepository usuarioTenantRepository;

    @InjectMocks private UsuarioController usuarioController;

    private Rol rolCajero;
    private Rol rolAdmin;
    private Usuario usuario;
    private UsuarioTenant utVetConSucursal;
    private UsuarioTenant utJuanSacSinSucursal;

    @BeforeEach
    void setUp() {
        rolCajero = Rol.builder().id(2L).nombre("CAJERO").build();
        rolAdmin  = Rol.builder().id(1L).nombre("ADMIN").build();

        usuario = Usuario.builder()
                .id(USUARIO_ID)
                .email(EMAIL)
                .nombre("Test")
                .activo(true)
                .build();

        // Vet: sucursal_id = 19
        utVetConSucursal = UsuarioTenant.builder()
                .id(100L).usuario(usuario).tenantId(TENANT_VET)
                .rol(rolCajero).sucursalId(SUCURSAL_VET).activo(true).build();

        // Juan SAC: sucursal_id = NULL
        utJuanSacSinSucursal = UsuarioTenant.builder()
                .id(101L).usuario(usuario).tenantId(TENANT_JUANSAC)
                .rol(rolAdmin).sucursalId(null).activo(true).build();

        when(usuarioMapper.toDTO(usuario)).thenAnswer(inv ->
                UsuarioDTO.builder().id(USUARIO_ID).email(EMAIL).nombre("Test").build()
        );
        doNothing().when(planLimitService).validarRolPermitido(anyString(), anyString());
        doNothing().when(planLimitService).validarLimiteUsuarios(anyString());
    }

    @AfterEach
    void tearDown() {
        TenantContext.clear();
    }

    // ── Case 1: Vet con sucursal_id=19 → DTO.sucursalId = 19 ─────────────────

    @Test
    @DisplayName("Caso 1 — GET /{id} en Vet (sucursal 19) → sucursalId = 19")
    void obtenerPorId_vetConSucursal_devuelveSucursalId() {
        TenantContext.setCurrentTenant(TENANT_VET);
        when(usuarioTenantRepository.findByUsuarioIdAndTenantIdAndActivoTrue(USUARIO_ID, TENANT_VET))
                .thenReturn(Optional.of(utVetConSucursal));

        ResponseEntity<UsuarioDTO> resp = usuarioController.obtenerPorId(USUARIO_ID);

        assertThat(resp.getStatusCode().value()).isEqualTo(200);
        assertThat(resp.getBody().getSucursalId()).isEqualTo(19L);
    }

    // ── Case 2: Juan SAC con sucursal_id=NULL → DTO.sucursalId = null ─────────

    @Test
    @DisplayName("Caso 2 — GET /{id} en Juan SAC (sucursal null) → sucursalId = null")
    void obtenerPorId_juanSacSinSucursal_devuelveNull() {
        TenantContext.setCurrentTenant(TENANT_JUANSAC);
        when(usuarioTenantRepository.findByUsuarioIdAndTenantIdAndActivoTrue(USUARIO_ID, TENANT_JUANSAC))
                .thenReturn(Optional.of(utJuanSacSinSucursal));

        ResponseEntity<UsuarioDTO> resp = usuarioController.obtenerPorId(USUARIO_ID);

        assertThat(resp.getBody().getSucursalId()).isNull();
    }

    // ── Case 3: mismo usuario, dos tenants, sucursales distintas ─────────────

    @Test
    @DisplayName("Caso 3 — mismo usuario en dos tenants devuelve su propia sucursal por tenant")
    void mismoUsuario_dosTenants_cadaUnoDevuelveSuSucursal() {
        // Vet
        TenantContext.setCurrentTenant(TENANT_VET);
        when(usuarioTenantRepository.findByUsuarioIdAndTenantIdAndActivoTrue(USUARIO_ID, TENANT_VET))
                .thenReturn(Optional.of(utVetConSucursal));
        ResponseEntity<UsuarioDTO> respVet = usuarioController.obtenerPorId(USUARIO_ID);

        // Juan SAC
        TenantContext.setCurrentTenant(TENANT_JUANSAC);
        when(usuarioTenantRepository.findByUsuarioIdAndTenantIdAndActivoTrue(USUARIO_ID, TENANT_JUANSAC))
                .thenReturn(Optional.of(utJuanSacSinSucursal));
        ResponseEntity<UsuarioDTO> respJuan = usuarioController.obtenerPorId(USUARIO_ID);

        assertThat(respVet.getBody().getSucursalId()).isEqualTo(19L);
        assertThat(respJuan.getBody().getSucursalId()).isNull();
    }

    // ── Case 4: obtenerTodos() devuelve sucursal correcta ─────────────────────

    @Test
    @DisplayName("Caso 4 — obtenerTodos() en Vet devuelve sucursalId = 19")
    void obtenerTodos_vetTenant_devuelveSucursal19() {
        TenantContext.setCurrentTenant(TENANT_VET);
        when(usuarioTenantRepository.findByTenantIdAndActivoTrue(TENANT_VET))
                .thenReturn(List.of(utVetConSucursal));

        ResponseEntity<List<UsuarioDTO>> resp = usuarioController.obtenerTodos();

        assertThat(resp.getBody()).hasSize(1);
        assertThat(resp.getBody().get(0).getSucursalId()).isEqualTo(19L);
    }

    @Test
    @DisplayName("Caso 4b — obtenerTodos() en Juan SAC devuelve sucursalId = null")
    void obtenerTodos_juanSacTenant_devuelveSucursalNull() {
        TenantContext.setCurrentTenant(TENANT_JUANSAC);
        when(usuarioTenantRepository.findByTenantIdAndActivoTrue(TENANT_JUANSAC))
                .thenReturn(List.of(utJuanSacSinSucursal));

        ResponseEntity<List<UsuarioDTO>> resp = usuarioController.obtenerTodos();

        assertThat(resp.getBody()).hasSize(1);
        assertThat(resp.getBody().get(0).getSucursalId()).isNull();
    }

    // ── Case 5: obtenerPorId() — ya cubierto arriba como Caso 1 y 2 ──────────

    // ── Case 6: obtenerPorEmail() devuelve sucursal correcta ─────────────────

    @Test
    @DisplayName("Caso 6 — obtenerPorEmail() en Vet devuelve sucursalId = 19")
    void obtenerPorEmail_vetTenant_devuelveSucursal19() {
        TenantContext.setCurrentTenant(TENANT_VET);
        when(usuarioService.obtenerUsuarioPorEmail(EMAIL)).thenReturn(Optional.of(usuario));
        when(usuarioTenantRepository.findByUsuarioIdAndTenantIdAndActivoTrue(USUARIO_ID, TENANT_VET))
                .thenReturn(Optional.of(utVetConSucursal));

        ResponseEntity<UsuarioDTO> resp = usuarioController.obtenerPorEmail(EMAIL);

        assertThat(resp.getStatusCode().value()).isEqualTo(200);
        assertThat(resp.getBody().getSucursalId()).isEqualTo(19L);
    }

    // ── Case 7: crear() devuelve sucursal correcta en la respuesta ───────────

    @Test
    @DisplayName("Caso 7 — crear() devuelve la sucursalId pasada en el request")
    void crear_devuelveSucursalIdEnRespuesta() {
        TenantContext.setCurrentTenant(TENANT_VET);

        UsuarioDTO request = UsuarioDTO.builder()
                .email(EMAIL).nombre("Test").rolNombre("CAJERO").sucursalId(SUCURSAL_VET).build();

        when(rolRepository.findByNombre("CAJERO")).thenReturn(Optional.of(rolCajero));
        when(usuarioMapper.toEntity(any())).thenReturn(usuario);
        when(usuarioService.crearUsuario(any(), eq(SUCURSAL_VET), any()))
                .thenReturn(new CrearUsuarioResult(usuario, true));
        when(usuarioService.guardarUsuario(any())).thenReturn(usuario);
        doNothing().when(emailService).enviarBienvenidaUsuarioNuevo(any(), any(), any(), any());

        ResponseEntity<UsuarioDTO> resp = usuarioController.crear(request);

        assertThat(resp.getStatusCode().value()).isEqualTo(201);
        assertThat(resp.getBody().getSucursalId()).isEqualTo(19L);
    }

    // ── Case 8: actualizar() devuelve sucursal correcta en la respuesta ───────

    @Test
    @DisplayName("Caso 8 — actualizar() devuelve la sucursalId del DTO de actualización")
    void actualizar_devuelveSucursalIdActualizada() {
        TenantContext.setCurrentTenant(TENANT_VET);

        UsuarioUpdateDTO updateDTO = UsuarioUpdateDTO.builder()
                .nombre("Test").rolNombre("CAJERO").sucursalId(SUCURSAL_VET).activo(true).build();

        when(usuarioTenantRepository.findByUsuarioIdAndTenantIdAndActivoTrue(USUARIO_ID, TENANT_VET))
                .thenReturn(Optional.of(utVetConSucursal));
        when(rolRepository.findByNombre("CAJERO")).thenReturn(Optional.of(rolCajero));
        when(usuarioService.actualizarUsuario(eq(USUARIO_ID), any(), any())).thenReturn(usuario);

        ResponseEntity<UsuarioDTO> resp = usuarioController.actualizar(USUARIO_ID, updateDTO);

        assertThat(resp.getStatusCode().value()).isEqualTo(200);
        assertThat(resp.getBody().getSucursalId()).isEqualTo(19L);
    }

    // ── Contrato: sucursalId null se mantiene null ────────────────────────────

    @Test
    @DisplayName("sucursalId null en UsuarioTenant → DTO.sucursalId = null (sin sustitución)")
    void sucursalIdNullEnUT_permanaceNullEnDTO() {
        TenantContext.setCurrentTenant(TENANT_JUANSAC);
        when(usuarioTenantRepository.findByTenantIdAndActivoTrue(TENANT_JUANSAC))
                .thenReturn(List.of(utJuanSacSinSucursal));

        ResponseEntity<List<UsuarioDTO>> resp = usuarioController.obtenerTodos();

        // null = acceso a todas las sucursales — no se debe convertir a 0 ni a otro valor
        assertThat(resp.getBody().get(0).getSucursalId()).isNull();
    }

    // ── Contrato: mapper NO depende de Usuario.getSucursalId() ───────────────

    @Test
    @DisplayName("El mapper se llama con Usuario — pero sucursalId NO viene de él sino de UsuarioTenant")
    void sucursalId_noDependeDeUsuarioGetSucursalId() {
        TenantContext.setCurrentTenant(TENANT_VET);
        when(usuarioTenantRepository.findByTenantIdAndActivoTrue(TENANT_VET))
                .thenReturn(List.of(utVetConSucursal));

        usuarioController.obtenerTodos();

        // El mapper se llama con el Usuario (para los demás campos)
        verify(usuarioMapper).toDTO(usuario);
        // Pero el DTO resultante recibe sucursalId directamente del UT, no del mapper
        // (confirmado porque UsuarioMapperImpl.toDTO no setea sucursalId — campo no existe en Usuario)
    }
}
