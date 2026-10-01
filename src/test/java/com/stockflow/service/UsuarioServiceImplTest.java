package com.stockflow.service;

import com.stockflow.entity.Rol;
import com.stockflow.entity.Usuario;
import com.stockflow.entity.UsuarioTenant;
import com.stockflow.repository.SuscripcionRepository;
import com.stockflow.repository.UsuarioRepository;
import com.stockflow.repository.UsuarioTenantRepository;
import com.stockflow.service.impl.UsuarioServiceImpl;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;
import org.springframework.security.crypto.password.PasswordEncoder;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
@DisplayName("UsuarioServiceImpl — creación de usuario con usuario_tenant")
class UsuarioServiceImplTest {

    @Mock private UsuarioRepository usuarioRepository;
    @Mock private UsuarioTenantRepository usuarioTenantRepository;
    @Mock private SuscripcionRepository suscripcionRepository;
    @Mock private PasswordEncoder passwordEncoder;
    @Mock private TenantService tenantService;
    @Mock private EmailService emailService;

    @InjectMocks private UsuarioServiceImpl usuarioService;

    private static final String TENANT_ID = "farmacia-abc";
    private Rol rolCajero;
    private Usuario nuevoUsuario;

    @BeforeEach
    void setUp() {
        rolCajero = Rol.builder().id(2L).nombre("CAJERO").build();

        nuevoUsuario = Usuario.builder()
                .id(null)
                .email("cajero@farmacia.com")
                .contraseña("plain-pass")
                .nombre("Cajero Test")
                .activo(true)
                .tenantId(TENANT_ID)
                .rol(rolCajero)
                .build();

        when(passwordEncoder.encode("plain-pass")).thenReturn("$2a$hashed");

        // El save devuelve el mismo usuario con ID asignado
        Usuario usuarioGuardado = Usuario.builder()
                .id(20L)
                .email("cajero@farmacia.com")
                .contraseña("$2a$hashed")
                .nombre("Cajero Test")
                .activo(true)
                .tenantId(TENANT_ID)
                .rol(rolCajero)
                .build();
        when(usuarioRepository.save(any(Usuario.class))).thenReturn(usuarioGuardado);
        when(usuarioTenantRepository.existsByUsuarioIdAndTenantIdAndActivoTrue(20L, TENANT_ID))
                .thenReturn(false);
    }

    @Test
    @DisplayName("crearUsuario: crea usuario_tenant automáticamente cuando tiene tenantId y rol")
    void crearUsuario_withTenantAndRol_createsUsuarioTenantRow() {
        usuarioService.crearUsuario(nuevoUsuario);

        ArgumentCaptor<UsuarioTenant> captor = ArgumentCaptor.forClass(UsuarioTenant.class);
        verify(usuarioTenantRepository).save(captor.capture());

        UsuarioTenant ut = captor.getValue();
        assertThat(ut.getTenantId()).isEqualTo(TENANT_ID);
        assertThat(ut.getRol().getNombre()).isEqualTo("CAJERO");
        assertThat(ut.getActivo()).isTrue();
    }

    @Test
    @DisplayName("crearUsuario: no crea usuario_tenant si ya existe uno activo (idempotencia)")
    void crearUsuario_alreadyExists_doesNotDuplicate() {
        when(usuarioTenantRepository.existsByUsuarioIdAndTenantIdAndActivoTrue(20L, TENANT_ID))
                .thenReturn(true);

        usuarioService.crearUsuario(nuevoUsuario);

        verify(usuarioTenantRepository, never()).save(any(UsuarioTenant.class));
    }

    @Test
    @DisplayName("crearUsuario: no crea usuario_tenant si el usuario no tiene tenantId (SUPER_ADMIN u otro)")
    void crearUsuario_noTenantId_skipsUsuarioTenant() {
        nuevoUsuario.setTenantId(null);

        // El save devuelve usuario sin tenantId
        Usuario sinTenant = Usuario.builder()
                .id(30L).email("cajero@farmacia.com").contraseña("$2a$hashed")
                .nombre("Cajero Test").activo(true).tenantId(null).rol(rolCajero).build();
        when(usuarioRepository.save(any(Usuario.class))).thenReturn(sinTenant);

        usuarioService.crearUsuario(nuevoUsuario);

        verify(usuarioTenantRepository, never()).save(any(UsuarioTenant.class));
        verify(usuarioTenantRepository, never()).existsByUsuarioIdAndTenantIdAndActivoTrue(any(), any());
    }
}
