package com.stockflow.service;

import com.stockflow.dto.CrearUsuarioResult;
import com.stockflow.dto.DatosEliminacionDTO;
import com.stockflow.dto.DeleteAccountValidationDTO;
import com.stockflow.dto.UsuarioUpdateDTO;
import com.stockflow.entity.Rol;
import com.stockflow.entity.Suscripcion;
import com.stockflow.entity.Usuario;
import com.stockflow.entity.UsuarioTenant;
import com.stockflow.exception.BadRequestException;
import com.stockflow.exception.ConflictException;
import com.stockflow.exception.ForbiddenException;
import com.stockflow.exception.ResourceNotFoundException;
import com.stockflow.repository.SuscripcionRepository;
import com.stockflow.repository.UsuarioRepository;
import com.stockflow.repository.UsuarioTenantRepository;
import com.stockflow.service.impl.UsuarioServiceImpl;
import com.stockflow.util.TenantContext;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;
import org.springframework.security.crypto.password.PasswordEncoder;

import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class UsuarioServiceImplTest {

    @Mock private UsuarioRepository usuarioRepository;
    @Mock private UsuarioTenantRepository usuarioTenantRepository;
    @Mock private SuscripcionRepository suscripcionRepository;
    @Mock private PasswordEncoder passwordEncoder;
    @Mock private TenantService tenantService;
    @Mock private EmailService emailService;

    @InjectMocks private UsuarioServiceImpl usuarioService;

    private static final String TENANT_ID    = "farmacia-abc";
    private static final String OTRO_TENANT  = "botica-santa-fe";

    private Rol rolCajero;
    private Rol rolAdmin;
    private Usuario usuarioGuardado;

    @BeforeEach
    void setUp() {
        rolCajero = Rol.builder().id(2L).nombre("CAJERO").build();
        rolAdmin  = Rol.builder().id(1L).nombre("ADMIN").build();

        usuarioGuardado = Usuario.builder()
                .id(20L)
                .email("cajero@farmacia.com")
                .contraseña("$2a$hashed")
                .nombre("Cajero Test")
                .activo(true)
                .build();

        when(passwordEncoder.encode(anyString())).thenReturn("$2a$hashed");
        when(usuarioRepository.save(any(Usuario.class))).thenReturn(usuarioGuardado);
        // Por defecto el email no existe — los tests de Case B lo sobreescriben
        when(usuarioRepository.findByEmail(anyString())).thenReturn(Optional.empty());
        when(usuarioTenantRepository.findByUsuarioIdAndTenantId(20L, OTRO_TENANT))
                .thenReturn(Optional.empty());
    }

    @AfterEach
    void tearDown() {
        TenantContext.clear();
    }

    // ──────────────────────────────────────────────────────────────────────────
    // crearUsuario
    // ──────────────────────────────────────────────────────────────────────────

    @Nested
    @DisplayName("crearUsuario — multi-tenant")
    class CrearUsuario {

        // ── Caso A: email nuevo ────────────────────────────────────────────────

        @Test
        @DisplayName("CASO A: crea usuario y usuario_tenant, devuelve esNuevo=true")
        void casoA_usuarioNuevo_creaAmbosRegistros() {
            TenantContext.setCurrentTenant(OTRO_TENANT);
            Usuario nuevo = Usuario.builder()
                    .email("cajero@farmacia.com").contraseña("plain").nombre("Test")
                    .activo(true).build();

            CrearUsuarioResult resultado = usuarioService.crearUsuario(nuevo, null, rolCajero);

            assertThat(resultado.esNuevo()).isTrue();
            assertThat(resultado.usuario().getId()).isEqualTo(20L);
            ArgumentCaptor<UsuarioTenant> captor = ArgumentCaptor.forClass(UsuarioTenant.class);
            verify(usuarioTenantRepository).save(captor.capture());
            assertThat(captor.getValue().getTenantId()).isEqualTo(OTRO_TENANT);
            assertThat(captor.getValue().getRol().getNombre()).isEqualTo("CAJERO");
            assertThat(captor.getValue().getActivo()).isTrue();
        }

        @Test
        @DisplayName("CASO A: codifica la contraseña del usuario nuevo")
        void casoA_codificaPassword() {
            TenantContext.setCurrentTenant(OTRO_TENANT);
            Usuario nuevo = Usuario.builder()
                    .email("nuevo@farmacia.com").contraseña("secreto").nombre("Test")
                    .activo(true).build();

            usuarioService.crearUsuario(nuevo, null, rolCajero);

            verify(passwordEncoder).encode("secreto");
            verify(usuarioRepository).save(argThat(u -> "$2a$hashed".equals(u.getContraseña())));
        }

        @Test
        @DisplayName("CASO A: asigna sucursalId en usuario_tenant, no en usuarios")
        void casoA_sucursalIdEnUsuarioTenant() {
            TenantContext.setCurrentTenant(OTRO_TENANT);
            Usuario nuevo = Usuario.builder()
                    .email("cajero@farmacia.com").contraseña("plain").nombre("Test")
                    .activo(true).build();

            usuarioService.crearUsuario(nuevo, 5L, rolCajero);

            ArgumentCaptor<UsuarioTenant> captor = ArgumentCaptor.forClass(UsuarioTenant.class);
            verify(usuarioTenantRepository).save(captor.capture());
            assertThat(captor.getValue().getSucursalId()).isEqualTo(5L);
        }

        @Test
        @DisplayName("CASO A: no crea usuario_tenant si tenantId es null (sin tenant context)")
        void casoA_sinTenantId_noCreaTenant() {
            Usuario sinTenant = Usuario.builder()
                    .id(30L).email("sa@fluxus.com").contraseña("plain").nombre("SA")
                    .activo(true).build();
            when(usuarioRepository.findByEmail("sa@fluxus.com")).thenReturn(Optional.empty());
            when(usuarioRepository.save(any())).thenReturn(sinTenant);

            CrearUsuarioResult resultado = usuarioService.crearUsuario(sinTenant, null, rolAdmin);

            assertThat(resultado.esNuevo()).isTrue();
            verify(usuarioTenantRepository, never()).save(any());
            verify(usuarioTenantRepository, never()).findByUsuarioIdAndTenantId(any(), any());
        }

        // ── Caso B: email ya existe ────────────────────────────────────────────

        @Test
        @DisplayName("CASO B: reutiliza usuario existente, no crea otro registro en usuarios")
        void casoB_emailExistente_reutilizaUsuario() {
            TenantContext.setCurrentTenant(TENANT_ID);
            when(usuarioRepository.findByEmail("cajero@farmacia.com"))
                    .thenReturn(Optional.of(usuarioGuardado));
            when(usuarioTenantRepository.findByUsuarioIdAndTenantId(20L, TENANT_ID))
                    .thenReturn(Optional.empty());

            Usuario peticion = Usuario.builder()
                    .email("cajero@farmacia.com").contraseña("cualquiera").nombre("Ignorado")
                    .activo(true).build();

            CrearUsuarioResult resultado = usuarioService.crearUsuario(peticion, null, rolAdmin);

            assertThat(resultado.esNuevo()).isFalse();
            assertThat(resultado.usuario().getId()).isEqualTo(20L);
            // No debe insertar nuevo registro en usuarios
            verify(usuarioRepository, never()).save(any());
        }

        @Test
        @DisplayName("CASO B: NO sobrescribe la contraseña del usuario existente")
        void casoB_noSobrescribePassword() {
            TenantContext.setCurrentTenant(TENANT_ID);
            when(usuarioRepository.findByEmail("cajero@farmacia.com"))
                    .thenReturn(Optional.of(usuarioGuardado));
            when(usuarioTenantRepository.findByUsuarioIdAndTenantId(20L, TENANT_ID))
                    .thenReturn(Optional.empty());

            usuarioService.crearUsuario(Usuario.builder()
                    .email("cajero@farmacia.com").contraseña("nuevo-password")
                    .nombre("Test").build(), null, rolAdmin);

            verify(passwordEncoder, never()).encode(anyString());
        }

        @Test
        @DisplayName("CASO B: crea usuario_tenant con el rol del tenant nuevo, devuelve esNuevo=false")
        void casoB_creaRelacionConRolCorrecto() {
            TenantContext.setCurrentTenant(TENANT_ID);
            when(usuarioRepository.findByEmail("cajero@farmacia.com"))
                    .thenReturn(Optional.of(usuarioGuardado));
            when(usuarioTenantRepository.findByUsuarioIdAndTenantId(20L, TENANT_ID))
                    .thenReturn(Optional.empty());

            usuarioService.crearUsuario(Usuario.builder()
                    .email("cajero@farmacia.com").contraseña("x")
                    .nombre("Test").build(), null, rolAdmin);

            ArgumentCaptor<UsuarioTenant> captor = ArgumentCaptor.forClass(UsuarioTenant.class);
            verify(usuarioTenantRepository).save(captor.capture());
            assertThat(captor.getValue().getTenantId()).isEqualTo(TENANT_ID);
            assertThat(captor.getValue().getRol().getNombre()).isEqualTo("ADMIN");
        }

        @Test
        @DisplayName("CASO B: roles distintos por tenant — CAJERO en un tenant, ADMIN en otro")
        void casoB_rolesDiferentesPorTenant() {
            // El usuario es CAJERO en OTRO_TENANT, lo agregan como ADMIN en TENANT_ID
            TenantContext.setCurrentTenant(TENANT_ID);
            when(usuarioRepository.findByEmail("cajero@farmacia.com"))
                    .thenReturn(Optional.of(usuarioGuardado));
            when(usuarioTenantRepository.findByUsuarioIdAndTenantId(20L, TENANT_ID))
                    .thenReturn(Optional.empty());

            usuarioService.crearUsuario(Usuario.builder()
                    .email("cajero@farmacia.com").contraseña("x")
                    .nombre("Test").build(), null, rolAdmin);

            ArgumentCaptor<UsuarioTenant> captor = ArgumentCaptor.forClass(UsuarioTenant.class);
            verify(usuarioTenantRepository).save(captor.capture());
            // La nueva relación debe tener ADMIN, no CAJERO
            assertThat(captor.getValue().getRol().getNombre()).isEqualTo("ADMIN");
            assertThat(captor.getValue().getTenantId()).isEqualTo(TENANT_ID);
        }

        @Test
        @DisplayName("CASO B: sucursales distintas por tenant")
        void casoB_sucursalesDiferentesPorTenant() {
            TenantContext.setCurrentTenant(TENANT_ID);
            when(usuarioRepository.findByEmail("cajero@farmacia.com"))
                    .thenReturn(Optional.of(usuarioGuardado));
            when(usuarioTenantRepository.findByUsuarioIdAndTenantId(20L, TENANT_ID))
                    .thenReturn(Optional.empty());

            usuarioService.crearUsuario(Usuario.builder()
                    .email("cajero@farmacia.com").contraseña("x")
                    .nombre("Test").build(), 99L, rolAdmin);

            ArgumentCaptor<UsuarioTenant> captor = ArgumentCaptor.forClass(UsuarioTenant.class);
            verify(usuarioTenantRepository).save(captor.capture());
            assertThat(captor.getValue().getSucursalId()).isEqualTo(99L);
        }

        @Test
        @DisplayName("CASO B: ya pertenece al tenant activo → ConflictException")
        void casoB_yaPerteneceTenant_lanzaConflict() {
            TenantContext.setCurrentTenant(TENANT_ID);
            UsuarioTenant utActivo = UsuarioTenant.builder()
                    .id(5L).usuario(usuarioGuardado).tenantId(TENANT_ID)
                    .rol(rolCajero).activo(true).build();
            when(usuarioRepository.findByEmail("cajero@farmacia.com"))
                    .thenReturn(Optional.of(usuarioGuardado));
            when(usuarioTenantRepository.findByUsuarioIdAndTenantId(20L, TENANT_ID))
                    .thenReturn(Optional.of(utActivo));

            assertThatThrownBy(() -> usuarioService.crearUsuario(Usuario.builder()
                    .email("cajero@farmacia.com").contraseña("x")
                    .nombre("Test").build(), null, rolAdmin))
                    .isInstanceOf(ConflictException.class)
                    .hasMessageContaining("ya pertenece");

            verify(usuarioTenantRepository, never()).save(any());
        }

        @Test
        @DisplayName("CASO B: relación inactiva → se reactiva con nuevo rol y sucursal")
        void casoB_relacionInactiva_seReactiva() {
            TenantContext.setCurrentTenant(TENANT_ID);
            UsuarioTenant utInactivo = UsuarioTenant.builder()
                    .id(5L).usuario(usuarioGuardado).tenantId(TENANT_ID)
                    .rol(rolCajero).activo(false).build();
            when(usuarioRepository.findByEmail("cajero@farmacia.com"))
                    .thenReturn(Optional.of(usuarioGuardado));
            when(usuarioTenantRepository.findByUsuarioIdAndTenantId(20L, TENANT_ID))
                    .thenReturn(Optional.of(utInactivo));

            usuarioService.crearUsuario(Usuario.builder()
                    .email("cajero@farmacia.com").contraseña("x")
                    .nombre("Test").build(), 7L, rolAdmin);

            ArgumentCaptor<UsuarioTenant> captor = ArgumentCaptor.forClass(UsuarioTenant.class);
            verify(usuarioTenantRepository).save(captor.capture());
            assertThat(captor.getValue().getActivo()).isTrue();
            assertThat(captor.getValue().getRol().getNombre()).isEqualTo("ADMIN");
            assertThat(captor.getValue().getSucursalId()).isEqualTo(7L);
        }

        @Test
        @DisplayName("CASO B: tenant isolation — no mezcla datos entre tenants")
        void casoB_tenantIsolation() {
            // El usuario ya tiene relación activa en OTRO_TENANT
            // Se agrega a TENANT_ID — no debe afectar la relación de OTRO_TENANT
            TenantContext.setCurrentTenant(TENANT_ID);
            when(usuarioRepository.findByEmail("cajero@farmacia.com"))
                    .thenReturn(Optional.of(usuarioGuardado));
            when(usuarioTenantRepository.findByUsuarioIdAndTenantId(20L, TENANT_ID))
                    .thenReturn(Optional.empty());

            usuarioService.crearUsuario(Usuario.builder()
                    .email("cajero@farmacia.com").contraseña("x")
                    .nombre("Test").build(), null, rolAdmin);

            // Solo debe guardarse una nueva relación para TENANT_ID
            ArgumentCaptor<UsuarioTenant> captor = ArgumentCaptor.forClass(UsuarioTenant.class);
            verify(usuarioTenantRepository, times(1)).save(captor.capture());
            assertThat(captor.getValue().getTenantId()).isEqualTo(TENANT_ID);
            // No se toca la relación del OTRO_TENANT
            verify(usuarioTenantRepository, never()).findByUsuarioIdAndTenantId(20L, OTRO_TENANT);
        }
    }

    // ──────────────────────────────────────────────────────────────────────────
    // actualizarUsuario — tenant-scoped
    // ──────────────────────────────────────────────────────────────────────────

    @Nested
    @DisplayName("actualizarUsuario — rol y sucursal van a usuario_tenant, no a usuarios")
    class ActualizarUsuario {

        private UsuarioTenant utVet;

        @BeforeEach
        void setup() {
            utVet = UsuarioTenant.builder()
                    .id(1L).usuario(usuarioGuardado).tenantId(TENANT_ID)
                    .rol(rolCajero).activo(true).sucursalId(10L).build();
            when(usuarioRepository.findById(20L)).thenReturn(Optional.of(usuarioGuardado));
            when(usuarioTenantRepository.findByUsuarioIdAndTenantIdAndActivoTrue(20L, TENANT_ID))
                    .thenReturn(Optional.of(utVet));
            when(usuarioRepository.save(any())).thenReturn(usuarioGuardado);
        }

        @Test
        @DisplayName("A: actualizar en Vet no modifica rol/sucursal de Botica")
        void actualizarEnVet_noModificaBotica() {
            TenantContext.setCurrentTenant(TENANT_ID);

            usuarioService.actualizarUsuario(20L,
                    UsuarioUpdateDTO.builder().nombre("Nuevo").sucursalId(99L).build(),
                    rolAdmin);

            // Solo debe tocarse el usuario_tenant del tenant actual
            verify(usuarioTenantRepository, never())
                    .findByUsuarioIdAndTenantIdAndActivoTrue(20L, OTRO_TENANT);
            // El usuario_tenant de TENANT_ID sí fue actualizado
            ArgumentCaptor<UsuarioTenant> captor = ArgumentCaptor.forClass(UsuarioTenant.class);
            verify(usuarioTenantRepository).save(captor.capture());
            assertThat(captor.getValue().getTenantId()).isEqualTo(TENANT_ID);
            assertThat(captor.getValue().getRol().getNombre()).isEqualTo("ADMIN");
            assertThat(captor.getValue().getSucursalId()).isEqualTo(99L);
        }

        @Test
        @DisplayName("B: cambiar sucursal en Vet no cambia sucursal en Botica")
        void cambiarSucursalEnVet_noAfectaBotica() {
            TenantContext.setCurrentTenant(TENANT_ID);

            usuarioService.actualizarUsuario(20L,
                    UsuarioUpdateDTO.builder().nombre("Test").sucursalId(55L).build(),
                    null);

            ArgumentCaptor<UsuarioTenant> captor = ArgumentCaptor.forClass(UsuarioTenant.class);
            verify(usuarioTenantRepository).save(captor.capture());
            assertThat(captor.getValue().getSucursalId()).isEqualTo(55L);
            assertThat(captor.getValue().getTenantId()).isEqualTo(TENANT_ID);
            verify(usuarioTenantRepository, never())
                    .findByUsuarioIdAndTenantIdAndActivoTrue(20L, OTRO_TENANT);
        }

        @Test
        @DisplayName("rol y sucursal NO se escriben en usuarios — entidad global no se modifica")
        void rolYSucursal_noSeEscribenEnUsuarios() {
            TenantContext.setCurrentTenant(TENANT_ID);

            usuarioService.actualizarUsuario(20L,
                    UsuarioUpdateDTO.builder().nombre("Nuevo").sucursalId(7L).build(),
                    rolAdmin);

            // El save en usuarios debe producirse (campos globales sí se actualizan)
            // rol ya no existe en Usuario — la ausencia del campo es la prueba
            verify(usuarioRepository).save(any(Usuario.class));
        }

        @Test
        @DisplayName("campos globales (nombre, apellido) sí se actualizan en usuarios")
        void camposGlobales_siSeActualizanEnUsuarios() {
            TenantContext.setCurrentTenant(TENANT_ID);
            usuarioGuardado.setNombre("Viejo");
            usuarioGuardado.setApellido("Apellido Viejo");

            usuarioService.actualizarUsuario(20L,
                    UsuarioUpdateDTO.builder().nombre("Nuevo Nombre").apellido("Nuevo Apellido").build(),
                    null);

            verify(usuarioRepository).save(argThat(u ->
                    "Nuevo Nombre".equals(u.getNombre()) && "Nuevo Apellido".equals(u.getApellido())));
        }
    }

    // ──────────────────────────────────────────────────────────────────────────
    // desactivarUsuario — tenant-scoped
    // ──────────────────────────────────────────────────────────────────────────

    @Nested
    @DisplayName("desactivarUsuario — solo desactiva usuario_tenant del tenant actual")
    class DesactivarUsuario {

        @Test
        @DisplayName("C: desactivar en Vet solo desactiva su usuario_tenant, Botica no se toca")
        void desactivarEnVet_noAfectaBotica() {
            UsuarioTenant utVet = UsuarioTenant.builder()
                    .id(1L).usuario(usuarioGuardado).tenantId(TENANT_ID)
                    .rol(rolCajero).activo(true).build();

            TenantContext.setCurrentTenant(TENANT_ID);
            when(usuarioTenantRepository.findByUsuarioIdAndTenantIdAndActivoTrue(20L, TENANT_ID))
                    .thenReturn(Optional.of(utVet));

            usuarioService.desactivarUsuario(20L);

            ArgumentCaptor<UsuarioTenant> captor = ArgumentCaptor.forClass(UsuarioTenant.class);
            verify(usuarioTenantRepository).save(captor.capture());
            assertThat(captor.getValue().getActivo()).isFalse();
            assertThat(captor.getValue().getTenantId()).isEqualTo(TENANT_ID);
            // Botica's usuario_tenant never touched
            verify(usuarioTenantRepository, never())
                    .findByUsuarioIdAndTenantIdAndActivoTrue(20L, OTRO_TENANT);
        }

        @Test
        @DisplayName("usuarios.activo NO se modifica al desactivar en tenant")
        void desactivar_noModificaUsuariosActivo() {
            UsuarioTenant ut = UsuarioTenant.builder()
                    .id(1L).usuario(usuarioGuardado).tenantId(TENANT_ID)
                    .rol(rolCajero).activo(true).build();

            TenantContext.setCurrentTenant(TENANT_ID);
            when(usuarioTenantRepository.findByUsuarioIdAndTenantIdAndActivoTrue(20L, TENANT_ID))
                    .thenReturn(Optional.of(ut));

            usuarioService.desactivarUsuario(20L);

            // nunca debe llamarse usuarioRepository.save() para desactivar
            verify(usuarioRepository, never()).save(any(Usuario.class));
            verify(usuarioRepository, never()).findById(any());
        }
    }

    // ──────────────────────────────────────────────────────────────────────────
    // activarUsuario — tenant-scoped
    // ──────────────────────────────────────────────────────────────────────────

    @Nested
    @DisplayName("activarUsuario — solo activa usuario_tenant del tenant actual")
    class ActivarUsuario {

        @Test
        @DisplayName("D: activar en Vet solo activa su usuario_tenant, Botica no se toca")
        void activarEnVet_noAfectaBotica() {
            UsuarioTenant utVetInactivo = UsuarioTenant.builder()
                    .id(1L).usuario(usuarioGuardado).tenantId(TENANT_ID)
                    .rol(rolCajero).activo(false).build();

            TenantContext.setCurrentTenant(TENANT_ID);
            when(usuarioTenantRepository.findByUsuarioIdAndTenantId(20L, TENANT_ID))
                    .thenReturn(Optional.of(utVetInactivo));

            usuarioService.activarUsuario(20L);

            ArgumentCaptor<UsuarioTenant> captor = ArgumentCaptor.forClass(UsuarioTenant.class);
            verify(usuarioTenantRepository).save(captor.capture());
            assertThat(captor.getValue().getActivo()).isTrue();
            assertThat(captor.getValue().getTenantId()).isEqualTo(TENANT_ID);
            // Botica's usuario_tenant never touched
            verify(usuarioTenantRepository, never()).findByUsuarioIdAndTenantId(20L, OTRO_TENANT);
            // usuarios.activo never touched
            verify(usuarioRepository, never()).save(any(Usuario.class));
            verify(usuarioRepository, never()).findById(any());
        }
    }

    // ──────────────────────────────────────────────────────────────────────────
    // eliminarUsuario — tenant-scoped
    // ──────────────────────────────────────────────────────────────────────────

    @Nested
    @DisplayName("eliminarUsuario — desactiva usuario_tenant del tenant actual")
    class EliminarUsuario {

        @Test
        @DisplayName("E: eliminar de Vet desactiva su usuario_tenant, Botica queda intacta")
        void eliminarDeVet_mantieneBotica() {
            UsuarioTenant utVet = UsuarioTenant.builder()
                    .id(1L).usuario(usuarioGuardado).tenantId(TENANT_ID)
                    .rol(rolCajero).activo(true).build();

            TenantContext.setCurrentTenant(TENANT_ID);
            when(usuarioTenantRepository.findByUsuarioIdAndTenantId(20L, TENANT_ID))
                    .thenReturn(Optional.of(utVet));
            // Usuario todavía tiene relación activa en OTRO_TENANT
            when(usuarioTenantRepository.countByUsuarioIdAndActivoTrue(20L)).thenReturn(1L);

            usuarioService.eliminarUsuario(20L);

            ArgumentCaptor<UsuarioTenant> captor = ArgumentCaptor.forClass(UsuarioTenant.class);
            verify(usuarioTenantRepository).save(captor.capture());
            assertThat(captor.getValue().getActivo()).isFalse();
            // Usuario global NO debe ser eliminado (tiene otro tenant activo)
            verify(usuarioRepository, never()).deleteById(any());
        }

        @Test
        @DisplayName("J: usuario sin tenants restantes → hard-delete del usuario global")
        void sinTenantsRestantes_hardDeleteUsuarioGlobal() {
            UsuarioTenant ut = UsuarioTenant.builder()
                    .id(1L).usuario(usuarioGuardado).tenantId(TENANT_ID)
                    .rol(rolCajero).activo(true).build();

            TenantContext.setCurrentTenant(TENANT_ID);
            when(usuarioTenantRepository.findByUsuarioIdAndTenantId(20L, TENANT_ID))
                    .thenReturn(Optional.of(ut));
            // Sin tenants activos restantes
            when(usuarioTenantRepository.countByUsuarioIdAndActivoTrue(20L)).thenReturn(0L);

            usuarioService.eliminarUsuario(20L);

            verify(usuarioTenantRepository).save(argThat(u -> !u.getActivo()));
            verify(usuarioRepository).deleteById(20L);
        }

        @Test
        @DisplayName("sin tenant en contexto → ForbiddenException")
        void sinTenantContexto_lanzaForbidden() {
            // TenantContext vacío (limpiado por @AfterEach de la clase padre)
            assertThatThrownBy(() -> usuarioService.eliminarUsuario(20L))
                    .isInstanceOf(com.stockflow.exception.ForbiddenException.class);

            verify(usuarioTenantRepository, never()).findByUsuarioIdAndTenantId(any(), any());
            verify(usuarioRepository, never()).deleteById(any());
        }

        @Test
        @DisplayName("usuario no encontrado en tenant → ResourceNotFoundException")
        void usuarioNoEncontradoEnTenant_lanzaResourceNotFound() {
            TenantContext.setCurrentTenant(TENANT_ID);
            when(usuarioTenantRepository.findByUsuarioIdAndTenantId(20L, TENANT_ID))
                    .thenReturn(Optional.empty());

            assertThatThrownBy(() -> usuarioService.eliminarUsuario(20L))
                    .isInstanceOf(com.stockflow.exception.ResourceNotFoundException.class);

            verify(usuarioRepository, never()).deleteById(any());
        }
    }

    // ──────────────────────────────────────────────────────────────────────────
    // obtenerUsuariosPorTenant — fix #1
    // ──────────────────────────────────────────────────────────────────────────

    @Nested
    @DisplayName("obtenerUsuariosPorTenant — usa usuario_tenant, no usuarios.tenant_id")
    class ObtenerUsuariosPorTenant {

        @Test
        @DisplayName("retorna usuarios que tienen relación activa en usuario_tenant, aunque tenant_id primario sea diferente")
        void retorna_usuarios_de_usuario_tenant() {
            // usuario cuyo tenant_id primario es OTRO_TENANT pero pertenece activamente a TENANT_ID
            UsuarioTenant ut1 = UsuarioTenant.builder()
                    .id(1L).usuario(usuarioGuardado).tenantId(TENANT_ID)
                    .rol(rolAdmin).activo(true).build();

            Usuario otroUsuario = Usuario.builder()
                    .id(21L).email("admin@farmacia.com").build();
            UsuarioTenant ut2 = UsuarioTenant.builder()
                    .id(2L).usuario(otroUsuario).tenantId(TENANT_ID)
                    .rol(rolAdmin).activo(true).build();

            when(usuarioTenantRepository.findByTenantIdAndActivoTrue(TENANT_ID))
                    .thenReturn(List.of(ut1, ut2));

            List<Usuario> resultado = usuarioService.obtenerUsuariosPorTenant(TENANT_ID);

            assertThat(resultado).hasSize(2);
            assertThat(resultado).extracting(Usuario::getId).containsExactlyInAnyOrder(20L, 21L);
            // Verifica que se usó usuarioTenantRepository, no usuarioRepository
            verify(usuarioTenantRepository).findByTenantIdAndActivoTrue(TENANT_ID);
        }

        @Test
        @DisplayName("retorna lista vacía si no hay relaciones activas en usuario_tenant")
        void sinRelaciones_retornaListaVacia() {
            when(usuarioTenantRepository.findByTenantIdAndActivoTrue(TENANT_ID))
                    .thenReturn(List.of());

            List<Usuario> resultado = usuarioService.obtenerUsuariosPorTenant(TENANT_ID);

            assertThat(resultado).isEmpty();
            verify(usuarioTenantRepository).findByTenantIdAndActivoTrue(TENANT_ID);
        }
    }

    // ──────────────────────────────────────────────────────────────────────────
    // reenviarActivacion — fix #2
    // ──────────────────────────────────────────────────────────────────────────

    @Nested
    @DisplayName("reenviarActivacion — valida pertenencia via usuario_tenant")
    class ReenviarActivacion {

        @Test
        @DisplayName("permite reenviar si existe relación activa en usuario_tenant aunque tenant_id primario sea diferente")
        void usuarioPerteneceAlTenantViaUsuarioTenant_permite() {
            // usuario cuyo tenant_id primario es OTRO_TENANT, pero pertenece activamente a TENANT_ID
            when(usuarioRepository.findById(20L)).thenReturn(Optional.of(usuarioGuardado));
            when(usuarioTenantRepository.existsByUsuarioIdAndTenantIdAndActivoTrue(20L, TENANT_ID))
                    .thenReturn(true);
            when(usuarioRepository.save(any())).thenReturn(usuarioGuardado);

            usuarioService.reenviarActivacion(20L, TENANT_ID);

            verify(emailService).enviarBienvenidaUsuarioNuevo(
                    eq(usuarioGuardado.getEmail()), anyString(), eq(TENANT_ID), anyString());
            // Verifica que NO se usó usuario.getTenantId() para la validación
            // (si lo usara, lanzaría BadRequestException porque tenant_id = OTRO_TENANT != TENANT_ID)
        }

        @Test
        @DisplayName("rechaza si no existe relación activa en usuario_tenant")
        void usuarioNoPerteneceAlTenant_lanzaBadRequest() {
            when(usuarioRepository.findById(20L)).thenReturn(Optional.of(usuarioGuardado));
            when(usuarioTenantRepository.existsByUsuarioIdAndTenantIdAndActivoTrue(20L, TENANT_ID))
                    .thenReturn(false);

            assertThatThrownBy(() -> usuarioService.reenviarActivacion(20L, TENANT_ID))
                    .isInstanceOf(BadRequestException.class)
                    .hasMessage("No autorizado para gestionar este usuario");

            verify(emailService, never()).enviarBienvenidaUsuarioNuevo(any(), any(), any(), any());
        }

        @Test
        @DisplayName("rechaza si el usuario no existe")
        void usuarioNoExiste_lanzaResourceNotFound() {
            when(usuarioRepository.findById(99L)).thenReturn(Optional.empty());

            assertThatThrownBy(() -> usuarioService.reenviarActivacion(99L, TENANT_ID))
                    .isInstanceOf(com.stockflow.exception.ResourceNotFoundException.class);
        }
    }

    // ──────────────────────────────────────────────────────────────────────────
    // eliminarCuentaCompleta — fix multi-tenant (Caso #7)
    // ──────────────────────────────────────────────────────────────────────────

    @Nested
    @DisplayName("eliminarCuentaCompleta — usa TenantContext, no usuario.getTenantId()")
    class EliminarCuentaCompleta {

        private static final String TENANT_BOTICA = "botica-santa-fe";
        private static final String TENANT_VET    = "veterinaria-vet";

        private Usuario usuarioAdmin;
        private Suscripcion suscripcionBotica;
        private Suscripcion suscripcionVet;

        @BeforeEach
        void setUpAdmin() {
            usuarioAdmin = Usuario.builder()
                    .id(5L).email("admin@botica.com").nombre("Admin")
                    .activo(true).build();

            suscripcionBotica = Suscripcion.builder()
                    .id(10L).tenantId(TENANT_BOTICA).usuarioPrincipal(usuarioAdmin).build();

            suscripcionVet = Suscripcion.builder()
                    .id(15L).tenantId(TENANT_VET).usuarioPrincipal(usuarioAdmin).build();
        }

        @Test
        @DisplayName("owner del tenant activo — elimina el tenant del contexto")
        void ownerDelTenantActivo_eliminaCorrectamente() {
            TenantContext.setCurrentTenant(TENANT_BOTICA);
            when(usuarioRepository.findById(5L)).thenReturn(Optional.of(usuarioAdmin));
            when(suscripcionRepository.findByTenantIdAndUsuarioPrincipalId(TENANT_BOTICA, 5L))
                    .thenReturn(Optional.of(suscripcionBotica));

            usuarioService.eliminarCuentaCompleta(5L);

            ArgumentCaptor<String> tenantCaptor = ArgumentCaptor.forClass(String.class);
            verify(tenantService).eliminarPermanentemente(tenantCaptor.capture());
            assertThat(tenantCaptor.getValue()).isEqualTo(TENANT_BOTICA);
        }

        @Test
        @DisplayName("usuario con 2 tenants, TenantContext = vet → elimina vet, NUNCA botica")
        void multiTenant_contextoApuntaAlSegundo_eliminaElSegundo() {
            TenantContext.setCurrentTenant(TENANT_VET);
            when(usuarioRepository.findById(5L)).thenReturn(Optional.of(usuarioAdmin));
            when(suscripcionRepository.findByTenantIdAndUsuarioPrincipalId(TENANT_VET, 5L))
                    .thenReturn(Optional.of(suscripcionVet));

            usuarioService.eliminarCuentaCompleta(5L);

            ArgumentCaptor<String> tenantCaptor = ArgumentCaptor.forClass(String.class);
            verify(tenantService).eliminarPermanentemente(tenantCaptor.capture());
            // Se eliminó vet, no botica (que es el tenant_id primario del usuario)
            assertThat(tenantCaptor.getValue()).isEqualTo(TENANT_VET);
            assertThat(tenantCaptor.getValue()).isNotEqualTo(TENANT_BOTICA);
        }

        @Test
        @DisplayName("usuario con 2 tenants, TenantContext = botica → elimina botica")
        void multiTenant_contextoApuntaAlPrimario_eliminaElPrimario() {
            TenantContext.setCurrentTenant(TENANT_BOTICA);
            when(usuarioRepository.findById(5L)).thenReturn(Optional.of(usuarioAdmin));
            when(suscripcionRepository.findByTenantIdAndUsuarioPrincipalId(TENANT_BOTICA, 5L))
                    .thenReturn(Optional.of(suscripcionBotica));

            usuarioService.eliminarCuentaCompleta(5L);

            verify(tenantService).eliminarPermanentemente(TENANT_BOTICA);
        }

        @Test
        @DisplayName("usuario no es owner del tenant activo → ForbiddenException, no elimina nada")
        void noEsOwner_lanzaForbidden_noEliminaNada() {
            TenantContext.setCurrentTenant(TENANT_VET);
            when(usuarioRepository.findById(5L)).thenReturn(Optional.of(usuarioAdmin));
            when(suscripcionRepository.findByTenantIdAndUsuarioPrincipalId(TENANT_VET, 5L))
                    .thenReturn(Optional.empty());

            assertThatThrownBy(() -> usuarioService.eliminarCuentaCompleta(5L))
                    .isInstanceOf(ForbiddenException.class)
                    .hasMessageContaining("propietario");

            verify(tenantService, never()).eliminarPermanentemente(any());
        }

        @Test
        @DisplayName("TenantContext vacío → ForbiddenException, no elimina nada")
        void sinTenantEnContexto_lanzaForbidden_noEliminaNada() {
            // TenantContext no establece tenant (está vacío por @AfterEach + no setCurrentTenant)
            when(usuarioRepository.findById(5L)).thenReturn(Optional.of(usuarioAdmin));

            assertThatThrownBy(() -> usuarioService.eliminarCuentaCompleta(5L))
                    .isInstanceOf(ForbiddenException.class)
                    .hasMessageContaining("tenant activo");

            verify(tenantService, never()).eliminarPermanentemente(any());
        }

        @Test
        @DisplayName("regresión: usuario.getTenantId() NO se usa para determinar el tenant a eliminar")
        void regresion_noUsaTenantIdLegacy() {
            // El usuario tiene tenant primario = botica, pero el contexto apunta a vet
            // Antes del fix, se habría eliminado botica. Ahora debe eliminarse vet.
            TenantContext.setCurrentTenant(TENANT_VET);
            when(usuarioRepository.findById(5L)).thenReturn(Optional.of(usuarioAdmin));
            when(suscripcionRepository.findByTenantIdAndUsuarioPrincipalId(TENANT_VET, 5L))
                    .thenReturn(Optional.of(suscripcionVet));

            usuarioService.eliminarCuentaCompleta(5L);

            // eliminarPermanentemente debe llamarse con TENANT_VET, nunca con TENANT_BOTICA
            verify(tenantService).eliminarPermanentemente(TENANT_VET);
            verify(tenantService, never()).eliminarPermanentemente(TENANT_BOTICA);
        }

        @Test
        @DisplayName("usuario no encontrado → ResourceNotFoundException")
        void usuarioNoEncontrado_lanzaResourceNotFound() {
            TenantContext.setCurrentTenant(TENANT_BOTICA);
            when(usuarioRepository.findById(99L)).thenReturn(Optional.empty());

            assertThatThrownBy(() -> usuarioService.eliminarCuentaCompleta(99L))
                    .isInstanceOf(ResourceNotFoundException.class)
                    .hasMessageContaining("no encontrado");

            verify(tenantService, never()).eliminarPermanentemente(any());
        }
    }

    // ──────────────────────────────────────────────────────────────────────────
    // Phase 3 — sucursalId pasa como parámetro explícito (no desde usuarios.sucursal_id)
    // ──────────────────────────────────────────────────────────────────────────

    @Nested
    @DisplayName("Fase3 — sucursalId fluye explícitamente a usuario_tenant, no desde usuarios")
    class SucursalIdEsExplicito {

        @Test
        @DisplayName("crear usuario nuevo: sucursal llega a usuario_tenant, no a usuarios")
        void crearUsuario_sucursalEnUsuarioTenant_noEnUsuarios() {
            TenantContext.setCurrentTenant(OTRO_TENANT);
            Usuario nuevo = Usuario.builder()
                    .email("cajero@farmacia.com").contraseña("plain").nombre("Test")
                    .activo(true).build();

            usuarioService.crearUsuario(nuevo, 42L, rolCajero);

            ArgumentCaptor<UsuarioTenant> captor = ArgumentCaptor.forClass(UsuarioTenant.class);
            verify(usuarioTenantRepository).save(captor.capture());
            assertThat(captor.getValue().getSucursalId()).isEqualTo(42L);
            // La entidad usuarios guardada NO tiene sucursalId (campo eliminado)
            ArgumentCaptor<Usuario> usuarioCaptor = ArgumentCaptor.forClass(Usuario.class);
            verify(usuarioRepository).save(usuarioCaptor.capture());
            // Verificar que no se intenta escribir sucursalId en usuarios
            // (el campo ya no existe en la entidad — si compiló, ya es evidencia suficiente)
        }

        @Test
        @DisplayName("crear usuario en dos tenants: cada tenant tiene su propia sucursal en usuario_tenant")
        void crearUsuario_sucursalesIndependientesPorTenant() {
            // Primera incorporación: tenant A con sucursal 10
            TenantContext.setCurrentTenant(TENANT_ID);
            when(usuarioRepository.findByEmail("cajero@farmacia.com"))
                    .thenReturn(Optional.of(usuarioGuardado));
            when(usuarioTenantRepository.findByUsuarioIdAndTenantId(20L, TENANT_ID))
                    .thenReturn(Optional.empty());

            usuarioService.crearUsuario(Usuario.builder()
                    .email("cajero@farmacia.com").contraseña("x").nombre("Test").build(),
                    10L, rolAdmin);

            ArgumentCaptor<UsuarioTenant> captor = ArgumentCaptor.forClass(UsuarioTenant.class);
            verify(usuarioTenantRepository).save(captor.capture());
            assertThat(captor.getValue().getSucursalId()).isEqualTo(10L);
            assertThat(captor.getValue().getTenantId()).isEqualTo(TENANT_ID);
        }

        @Test
        @DisplayName("actualizar sucursal solo modifica usuario_tenant, no toca la entidad Usuario")
        void actualizarSucursal_soloModificaUsuarioTenant() {
            UsuarioTenant ut = UsuarioTenant.builder()
                    .id(1L).usuario(usuarioGuardado).tenantId(TENANT_ID)
                    .rol(rolCajero).activo(true).sucursalId(10L).build();

            TenantContext.setCurrentTenant(TENANT_ID);
            when(usuarioRepository.findById(20L)).thenReturn(Optional.of(usuarioGuardado));
            when(usuarioTenantRepository.findByUsuarioIdAndTenantIdAndActivoTrue(20L, TENANT_ID))
                    .thenReturn(Optional.of(ut));
            when(usuarioRepository.save(any())).thenReturn(usuarioGuardado);

            usuarioService.actualizarUsuario(20L,
                    UsuarioUpdateDTO.builder().sucursalId(99L).build(), null);

            ArgumentCaptor<UsuarioTenant> captor = ArgumentCaptor.forClass(UsuarioTenant.class);
            verify(usuarioTenantRepository).save(captor.capture());
            assertThat(captor.getValue().getSucursalId()).isEqualTo(99L);
            // El save de usuarioRepository NO recibe un objeto con sucursalId
            // (el campo no existe en Usuario — evidencia de que no se escribe en usuarios)
            verify(usuarioRepository).save(any(Usuario.class));
        }

        @Test
        @DisplayName("usuario sin tenant context y sin sucursalId: solo se guarda identidad global, no usuario_tenant")
        void crear_sinTenantContext_soloGuardaIdentidadGlobal() {
            // Sin TenantContext activo (ej. sistema interno, seed de datos)
            Usuario u = Usuario.builder()
                    .id(99L).email("seed@system.com").contraseña("plain").nombre("Seed")
                    .activo(true).build();
            when(usuarioRepository.findByEmail("seed@system.com")).thenReturn(Optional.empty());
            when(usuarioRepository.save(any())).thenReturn(u);

            CrearUsuarioResult res = usuarioService.crearUsuario(u, null, rolAdmin);

            assertThat(res.esNuevo()).isTrue();
            verify(usuarioTenantRepository, never()).save(any());
        }
    }

    // ──────────────────────────────────────────────────────────────────────────
    // Phase 3 — conteo por tenant via usuario_tenant
    // ──────────────────────────────────────────────────────────────────────────

    @Nested
    @DisplayName("Fase3 — conteo de usuarios por tenant via usuario_tenant")
    class ConteoTenant {

        @Test
        @DisplayName("obtenerUsuariosPorTenant solo cuenta relaciones activas en usuario_tenant")
        void conteo_usaUsuarioTenant_noUsuariosTenantId() {
            // Dos usuarios activos en TENANT_ID
            UsuarioTenant ut1 = UsuarioTenant.builder()
                    .id(1L).usuario(usuarioGuardado).tenantId(TENANT_ID)
                    .rol(rolCajero).activo(true).build();
            Usuario otro = Usuario.builder().id(21L).email("otro@farmacia.com").activo(true).build();
            UsuarioTenant ut2 = UsuarioTenant.builder()
                    .id(2L).usuario(otro).tenantId(TENANT_ID)
                    .rol(rolAdmin).activo(true).build();

            when(usuarioTenantRepository.findByTenantIdAndActivoTrue(TENANT_ID))
                    .thenReturn(List.of(ut1, ut2));

            List<Usuario> lista = usuarioService.obtenerUsuariosPorTenant(TENANT_ID);

            assertThat(lista).hasSize(2);
            // Fuente de verdad es usuario_tenant, no el campo tenant_id de usuarios
            verify(usuarioTenantRepository).findByTenantIdAndActivoTrue(TENANT_ID);
            verify(usuarioRepository, never()).findAll();
        }
    }

    // ──────────────────────────────────────────────────────────────────────────
    // Phase 3 — aislamiento de sucursal entre tenants
    // ──────────────────────────────────────────────────────────────────────────

    @Nested
    @DisplayName("Fase3 — asignarSucursal solo afecta usuario_tenant del tenant correcto")
    class SucursalTenantIsolation {

        @Test
        @DisplayName("actualizarUsuario asigna sucursal en usuario_tenant del tenant activo, no en otros")
        void actualizarSucursal_soloAfectaTenantActivo() {
            UsuarioTenant utVet = UsuarioTenant.builder()
                    .id(1L).usuario(usuarioGuardado).tenantId(TENANT_ID)
                    .rol(rolCajero).activo(true).sucursalId(10L).build();

            TenantContext.setCurrentTenant(TENANT_ID);
            when(usuarioRepository.findById(20L)).thenReturn(Optional.of(usuarioGuardado));
            when(usuarioTenantRepository.findByUsuarioIdAndTenantIdAndActivoTrue(20L, TENANT_ID))
                    .thenReturn(Optional.of(utVet));
            when(usuarioRepository.save(any())).thenReturn(usuarioGuardado);

            usuarioService.actualizarUsuario(20L,
                    UsuarioUpdateDTO.builder().sucursalId(77L).build(), null);

            ArgumentCaptor<UsuarioTenant> captor = ArgumentCaptor.forClass(UsuarioTenant.class);
            verify(usuarioTenantRepository).save(captor.capture());
            // Solo el tenant activo recibe el cambio de sucursal
            assertThat(captor.getValue().getTenantId()).isEqualTo(TENANT_ID);
            assertThat(captor.getValue().getSucursalId()).isEqualTo(77L);
            // El otro tenant nunca se consulta
            verify(usuarioTenantRepository, never())
                    .findByUsuarioIdAndTenantIdAndActivoTrue(20L, OTRO_TENANT);
        }

        @Test
        @DisplayName("sin TenantContext no se actualiza ningún usuario_tenant")
        void sinTenantContext_noActualizaUsuarioTenant() {
            // TenantContext vacío — limpiado por @AfterEach
            when(usuarioRepository.findById(20L)).thenReturn(Optional.of(usuarioGuardado));
            when(usuarioRepository.save(any())).thenReturn(usuarioGuardado);

            usuarioService.actualizarUsuario(20L,
                    UsuarioUpdateDTO.builder().nombre("Test").build(), null);

            // Sin tenantId, el bloque de usuario_tenant se salta completamente
            verify(usuarioTenantRepository, never()).findByUsuarioIdAndTenantIdAndActivoTrue(any(), any());
            verify(usuarioTenantRepository, never()).save(any());
        }
    }

    // ──────────────────────────────────────────────────────────────────────────
    // V103 Prep — sin TenantContext, desactivar/activar lanza ForbiddenException
    // ──────────────────────────────────────────────────────────────────────────

    @Nested
    @DisplayName("V103 Prep — desactivar/activar requieren TenantContext; sin él → ForbiddenException")
    class TenantRequeridoEnOperaciones {

        @Test
        @DisplayName("desactivar sin TenantContext lanza ForbiddenException")
        void desactivar_sinTenantContext_lanzaForbidden() {
            // TenantContext está vacío (limpiado por @AfterEach)
            assertThatThrownBy(() -> usuarioService.desactivarUsuario(20L))
                    .isInstanceOf(com.stockflow.exception.ForbiddenException.class)
                    .hasMessageContaining("No hay tenant activo");

            verify(usuarioRepository, never()).save(any());
            verify(usuarioTenantRepository, never()).save(any());
        }

        @Test
        @DisplayName("activar sin TenantContext lanza ForbiddenException")
        void activar_sinTenantContext_lanzaForbidden() {
            // TenantContext está vacío
            assertThatThrownBy(() -> usuarioService.activarUsuario(20L))
                    .isInstanceOf(com.stockflow.exception.ForbiddenException.class)
                    .hasMessageContaining("No hay tenant activo");

            verify(usuarioRepository, never()).save(any());
            verify(usuarioTenantRepository, never()).save(any());
        }

        @Test
        @DisplayName("crearUsuario sin TenantContext y sin rol no crea usuario_tenant")
        void crear_sinTenantNiRol_noCreaTenant() {
            Usuario u = Usuario.builder()
                    .id(99L).email("nuevo@system.com").contraseña("plain").nombre("Nuevo")
                    .activo(true).build(); // sin rol, sin tenantId
            when(usuarioRepository.findByEmail("nuevo@system.com")).thenReturn(Optional.empty());
            when(usuarioRepository.save(any())).thenReturn(u);

            CrearUsuarioResult resultado = usuarioService.crearUsuario(u, null, null);

            assertThat(resultado.esNuevo()).isTrue();
            verify(usuarioTenantRepository, never()).save(any());
        }
    }

    // ──────────────────────────────────────────────────────────────────────────
    // Phase 3 — semántica usuarios.activo vs usuario_tenant.activo
    // ──────────────────────────────────────────────────────────────────────────

    @Nested
    @DisplayName("Fase3 — usuarios.activo = suspensión global; usuario_tenant.activo = per-tenant")
    class ActivoSemantica {

        @Test
        @DisplayName("desactivar por tenant NO toca usuarios.activo (suspensión global)")
        void desactivarPorTenant_noAfectaUsuariosActivo() {
            UsuarioTenant ut = UsuarioTenant.builder()
                    .id(1L).usuario(usuarioGuardado).tenantId(TENANT_ID)
                    .rol(rolCajero).activo(true).build();

            TenantContext.setCurrentTenant(TENANT_ID);
            when(usuarioTenantRepository.findByUsuarioIdAndTenantIdAndActivoTrue(20L, TENANT_ID))
                    .thenReturn(Optional.of(ut));

            usuarioService.desactivarUsuario(20L);

            // usuarios.activo (suspensión global) no se toca
            verify(usuarioRepository, never()).save(any(Usuario.class));
            // usuario_tenant.activo sí se actualizó
            ArgumentCaptor<UsuarioTenant> captor = ArgumentCaptor.forClass(UsuarioTenant.class);
            verify(usuarioTenantRepository).save(captor.capture());
            assertThat(captor.getValue().getActivo()).isFalse();
        }

        @Test
        @DisplayName("activar por tenant NO toca usuarios.activo (activación global)")
        void activarPorTenant_noAfectaUsuariosActivo() {
            UsuarioTenant ut = UsuarioTenant.builder()
                    .id(1L).usuario(usuarioGuardado).tenantId(TENANT_ID)
                    .rol(rolCajero).activo(false).build();

            TenantContext.setCurrentTenant(TENANT_ID);
            when(usuarioTenantRepository.findByUsuarioIdAndTenantId(20L, TENANT_ID))
                    .thenReturn(Optional.of(ut));

            usuarioService.activarUsuario(20L);

            // usuarios.activo (suspensión global) no se toca
            verify(usuarioRepository, never()).save(any(Usuario.class));
            // usuario_tenant.activo sí se actualizó
            ArgumentCaptor<UsuarioTenant> captor = ArgumentCaptor.forClass(UsuarioTenant.class);
            verify(usuarioTenantRepository).save(captor.capture());
            assertThat(captor.getValue().getActivo()).isTrue();
        }

        @Test
        @DisplayName("actualizarUsuario puede modificar usuarios.activo (suspensión global) via updateDTO.activo")
        void actualizarUsuario_puedeModificarUsuariosActivo() {
            UsuarioTenant ut = UsuarioTenant.builder()
                    .id(1L).usuario(usuarioGuardado).tenantId(TENANT_ID)
                    .rol(rolCajero).activo(true).build();

            TenantContext.setCurrentTenant(TENANT_ID);
            when(usuarioRepository.findById(20L)).thenReturn(Optional.of(usuarioGuardado));
            when(usuarioTenantRepository.findByUsuarioIdAndTenantIdAndActivoTrue(20L, TENANT_ID))
                    .thenReturn(Optional.of(ut));
            when(usuarioRepository.save(any())).thenReturn(usuarioGuardado);

            usuarioService.actualizarUsuario(20L,
                    UsuarioUpdateDTO.builder().activo(false).build(), null);

            // usuarios.activo sí debe actualizarse (suspensión global via DTO)
            verify(usuarioRepository).save(argThat(u -> Boolean.FALSE.equals(u.getActivo())));
        }
    }

    // ──────────────────────────────────────────────────────────────────────────
    // validarEliminacion — fix multi-tenant (Caso #11)
    // ──────────────────────────────────────────────────────────────────────────

    @Nested
    @DisplayName("validarEliminacion — usa TenantContext, no usuario.getTenantId()")
    class ValidarEliminacion {

        private static final String TENANT_BOTICA = "botica-santa-fe";
        private static final String TENANT_VET    = "veterinaria-vet";

        private Usuario usuarioAdmin;
        private Suscripcion suscripcionBotica;
        private Suscripcion suscripcionVet;
        private DatosEliminacionDTO datosBotica;
        private DatosEliminacionDTO datosVet;

        @BeforeEach
        void setUpAdmin() {
            usuarioAdmin = Usuario.builder()
                    .id(5L).email("admin@botica.com").nombre("Admin")
                    .activo(true).build();

            suscripcionBotica = Suscripcion.builder()
                    .id(10L).tenantId(TENANT_BOTICA).usuarioPrincipal(usuarioAdmin).build();

            suscripcionVet = Suscripcion.builder()
                    .id(15L).tenantId(TENANT_VET).usuarioPrincipal(usuarioAdmin).build();

            datosBotica = DatosEliminacionDTO.builder()
                    .tenantId(TENANT_BOTICA).nombreFarmacia("Botica Santa Fe")
                    .usuarios(3).productos(50).ventas(120).build();

            datosVet = DatosEliminacionDTO.builder()
                    .tenantId(TENANT_VET).nombreFarmacia("Veterinaria Vet")
                    .usuarios(2).productos(30).ventas(45).build();
        }

        @Test
        @DisplayName("owner del tenant activo → devuelve datos de ese tenant")
        void ownerDelTenantActivo_devuelveDatosCorrectos() {
            TenantContext.setCurrentTenant(TENANT_BOTICA);
            when(usuarioRepository.findById(5L)).thenReturn(Optional.of(usuarioAdmin));
            when(suscripcionRepository.findByTenantIdAndUsuarioPrincipalId(TENANT_BOTICA, 5L))
                    .thenReturn(Optional.of(suscripcionBotica));
            when(tenantService.obtenerDatosEliminacion(TENANT_BOTICA)).thenReturn(datosBotica);

            DeleteAccountValidationDTO resultado = usuarioService.validarEliminacion(5L);

            assertThat(resultado.getTipo()).isEqualTo("TENANT_OWNER");
            assertThat(resultado.isRequiereConfirmacion()).isTrue();
            assertThat(resultado.getDatosAEliminar().getTenantId()).isEqualTo(TENANT_BOTICA);
            assertThat(resultado.getDatosAEliminar().getNombreFarmacia()).isEqualTo("Botica Santa Fe");
        }

        @Test
        @DisplayName("usuario con 2 tenants + contexto = vet → devuelve datos de vet, NUNCA de botica")
        void multiTenant_contextoApuntaAlSegundo_devuelveDatosDelSegundo() {
            // tenant_id primario = botica, pero el contexto activo apunta a vet
            TenantContext.setCurrentTenant(TENANT_VET);
            when(usuarioRepository.findById(5L)).thenReturn(Optional.of(usuarioAdmin));
            when(suscripcionRepository.findByTenantIdAndUsuarioPrincipalId(TENANT_VET, 5L))
                    .thenReturn(Optional.of(suscripcionVet));
            when(tenantService.obtenerDatosEliminacion(TENANT_VET)).thenReturn(datosVet);

            DeleteAccountValidationDTO resultado = usuarioService.validarEliminacion(5L);

            assertThat(resultado.getTipo()).isEqualTo("TENANT_OWNER");
            assertThat(resultado.getDatosAEliminar().getTenantId()).isEqualTo(TENANT_VET);
            assertThat(resultado.getDatosAEliminar().getNombreFarmacia()).isEqualTo("Veterinaria Vet");
            // Verificar que NO se consultó el tenant primario (botica)
            verify(tenantService, never()).obtenerDatosEliminacion(TENANT_BOTICA);
        }

        @Test
        @DisplayName("usuario con 2 tenants + contexto = botica → devuelve datos de botica")
        void multiTenant_contextoApuntaAlPrimario_devuelveDatosDelPrimario() {
            TenantContext.setCurrentTenant(TENANT_BOTICA);
            when(usuarioRepository.findById(5L)).thenReturn(Optional.of(usuarioAdmin));
            when(suscripcionRepository.findByTenantIdAndUsuarioPrincipalId(TENANT_BOTICA, 5L))
                    .thenReturn(Optional.of(suscripcionBotica));
            when(tenantService.obtenerDatosEliminacion(TENANT_BOTICA)).thenReturn(datosBotica);

            DeleteAccountValidationDTO resultado = usuarioService.validarEliminacion(5L);

            assertThat(resultado.getDatosAEliminar().getTenantId()).isEqualTo(TENANT_BOTICA);
            verify(tenantService, never()).obtenerDatosEliminacion(TENANT_VET);
        }

        @Test
        @DisplayName("usuario no es owner del tenant activo → tipo USUARIO_NORMAL, sin datos de eliminación")
        void noEsOwner_devuelveUsuarioNormal() {
            TenantContext.setCurrentTenant(TENANT_VET);
            when(usuarioRepository.findById(5L)).thenReturn(Optional.of(usuarioAdmin));
            when(suscripcionRepository.findByTenantIdAndUsuarioPrincipalId(TENANT_VET, 5L))
                    .thenReturn(Optional.empty());

            DeleteAccountValidationDTO resultado = usuarioService.validarEliminacion(5L);

            assertThat(resultado.getTipo()).isEqualTo("USUARIO_NORMAL");
            assertThat(resultado.isRequiereConfirmacion()).isFalse();
            assertThat(resultado.getDatosAEliminar()).isNull();
            verify(tenantService, never()).obtenerDatosEliminacion(any());
        }

        @Test
        @DisplayName("TenantContext vacío → ForbiddenException")
        void sinTenantEnContexto_lanzaForbidden() {
            // TenantContext limpiado por @AfterEach en la clase padre — aquí no hay tenant
            when(usuarioRepository.findById(5L)).thenReturn(Optional.of(usuarioAdmin));

            assertThatThrownBy(() -> usuarioService.validarEliminacion(5L))
                    .isInstanceOf(ForbiddenException.class)
                    .hasMessageContaining("tenant activo");

            verify(tenantService, never()).obtenerDatosEliminacion(any());
        }

        @Test
        @DisplayName("regresión: obtenerDatosEliminacion NO se llama con usuario.getTenantId()")
        void regresion_noUsaTenantIdLegacy() {
            // Contexto = vet (distinto del tenant_id primario del usuario = botica)
            TenantContext.setCurrentTenant(TENANT_VET);
            when(usuarioRepository.findById(5L)).thenReturn(Optional.of(usuarioAdmin));
            when(suscripcionRepository.findByTenantIdAndUsuarioPrincipalId(TENANT_VET, 5L))
                    .thenReturn(Optional.of(suscripcionVet));
            when(tenantService.obtenerDatosEliminacion(TENANT_VET)).thenReturn(datosVet);

            usuarioService.validarEliminacion(5L);

            // Debe llamarse con TENANT_VET (el contexto), nunca con TENANT_BOTICA (legacy)
            verify(tenantService).obtenerDatosEliminacion(TENANT_VET);
            verify(tenantService, never()).obtenerDatosEliminacion(TENANT_BOTICA);
        }

        @Test
        @DisplayName("regresión: findByUsuarioPrincipalId NO se usa para determinar ownership")
        void regresion_noUsaFindByUsuarioPrincipalId() {
            TenantContext.setCurrentTenant(TENANT_BOTICA);
            when(usuarioRepository.findById(5L)).thenReturn(Optional.of(usuarioAdmin));
            when(suscripcionRepository.findByTenantIdAndUsuarioPrincipalId(TENANT_BOTICA, 5L))
                    .thenReturn(Optional.of(suscripcionBotica));
            when(tenantService.obtenerDatosEliminacion(TENANT_BOTICA)).thenReturn(datosBotica);

            usuarioService.validarEliminacion(5L);

            // El método legacy NO debe invocarse
            verify(suscripcionRepository, never()).findByUsuarioPrincipalId(any());
            // El método correcto sí debe invocarse
            verify(suscripcionRepository).findByTenantIdAndUsuarioPrincipalId(TENANT_BOTICA, 5L);
        }
    }
}
