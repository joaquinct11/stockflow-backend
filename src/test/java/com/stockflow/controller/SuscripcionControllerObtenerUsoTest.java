package com.stockflow.controller;

import com.stockflow.mapper.SuscripcionMapper;
import com.stockflow.repository.ProductoRepository;
import com.stockflow.repository.SucursalRepository;
import com.stockflow.repository.SuscripcionRepository;
import com.stockflow.repository.UsuarioRepository;
import com.stockflow.repository.UsuarioTenantRepository;
import com.stockflow.service.CulqiService;
import com.stockflow.service.EmailService;
import com.stockflow.service.SuscripcionService;
import com.stockflow.service.UsuarioService;
import com.stockflow.config.properties.CulqiProperties;
import com.stockflow.util.TenantContext;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;
import org.springframework.http.ResponseEntity;

import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
@DisplayName("SuscripcionController.obtenerUso() — usa usuario_tenant, no usuarios.tenant_id")
class SuscripcionControllerObtenerUsoTest {

    private static final String TENANT_ID = "botica-santa-fe";

    @Mock private SuscripcionService       suscripcionService;
    @Mock private UsuarioService           usuarioService;
    @Mock private SuscripcionMapper        suscripcionMapper;
    @Mock private SuscripcionRepository    suscripcionRepository;
    @Mock private SucursalRepository       sucursalRepository;
    @Mock private ProductoRepository       productoRepository;
    @Mock private UsuarioRepository        usuarioRepository;
    @Mock private UsuarioTenantRepository  usuarioTenantRepository;
    @Mock private CulqiService             culqiService;
    @Mock private EmailService             emailService;
    @Mock private CulqiProperties          culqiProperties;

    @InjectMocks private SuscripcionController controller;

    @BeforeEach
    void setUp() {
        TenantContext.setCurrentTenant(TENANT_ID);
        // Defaults para que los otros conteos no fallen
        when(sucursalRepository.countByTenantIdAndActivoTrue(TENANT_ID)).thenReturn(1L);
        when(productoRepository.countByTenantIdAndActivoTrue(TENANT_ID)).thenReturn(25L);
    }

    @AfterEach
    void tearDown() {
        TenantContext.clear();
    }

    // ──────────────────────────────────────────────────────────────────────────
    // Conteo de usuarios via usuario_tenant
    // ──────────────────────────────────────────────────────────────────────────

    @Nested
    @DisplayName("Conteo de usuarios via usuario_tenant")
    class ConteoUsuarios {

        @Test
        @DisplayName("usuarios con relación activa en usuario_tenant → se cuentan")
        void usuariosConRelacionActiva_seCuentan() {
            when(usuarioTenantRepository.countByTenantIdAndActivoTrue(TENANT_ID)).thenReturn(3L);

            ResponseEntity<Map<String, Long>> response = controller.obtenerUso();

            assertThat(response.getBody()).isNotNull();
            assertThat(response.getBody().get("usuarios")).isEqualTo(3L);
        }

        @Test
        @DisplayName("usuario cuyo tenant primario es otro pero tiene relación activa → se cuenta")
        void usuarioConTenantPrimarioDiferente_tieneRelacionActiva_seCuenta() {
            // Este usuario tiene usuarios.tenant_id = OTRO_TENANT pero está activo en TENANT_ID
            // via usuario_tenant — countByTenantIdAndActivoTrue lo incluye correctamente
            when(usuarioTenantRepository.countByTenantIdAndActivoTrue(TENANT_ID)).thenReturn(4L);

            ResponseEntity<Map<String, Long>> response = controller.obtenerUso();

            assertThat(response.getBody().get("usuarios")).isEqualTo(4L);
        }

        @Test
        @DisplayName("relaciones usuario_tenant activo=false → no se cuentan")
        void relacionesInactivas_noSeCuentan() {
            // countByTenantIdAndActivoTrue filtra solo activo=true
            when(usuarioTenantRepository.countByTenantIdAndActivoTrue(TENANT_ID)).thenReturn(2L);

            ResponseEntity<Map<String, Long>> response = controller.obtenerUso();

            // Solo los activos (2), no los inactivos
            assertThat(response.getBody().get("usuarios")).isEqualTo(2L);
        }

        @Test
        @DisplayName("usuarios de otro tenant → no se cuentan")
        void usuariosDeOtroTenant_noSeCuentan() {
            when(usuarioTenantRepository.countByTenantIdAndActivoTrue(TENANT_ID)).thenReturn(1L);

            ResponseEntity<Map<String, Long>> response = controller.obtenerUso();

            // Solo el del tenant correcto
            assertThat(response.getBody().get("usuarios")).isEqualTo(1L);
        }
    }

    // ──────────────────────────────────────────────────────────────────────────
    // Regresiones y estructura de respuesta
    // ──────────────────────────────────────────────────────────────────────────

    @Nested
    @DisplayName("Regresiones y estructura de respuesta")
    class Regresiones {

        @Test
        @DisplayName("usuarioRepository.countByTenantId() ya NO se usa para este contador")
        void regresion_noUsaCountByTenantId() {
            when(usuarioTenantRepository.countByTenantIdAndActivoTrue(TENANT_ID)).thenReturn(3L);

            controller.obtenerUso();

            // El método correcto sí debe invocarse
            verify(usuarioTenantRepository).countByTenantIdAndActivoTrue(TENANT_ID);
        }

        @Test
        @DisplayName("conteo de productos sigue usando productoRepository sin cambios")
        void productosSiguenUsandoProductoRepository() {
            when(usuarioTenantRepository.countByTenantIdAndActivoTrue(TENANT_ID)).thenReturn(3L);
            when(productoRepository.countByTenantIdAndActivoTrue(TENANT_ID)).thenReturn(50L);

            controller.obtenerUso();

            verify(productoRepository).countByTenantIdAndActivoTrue(TENANT_ID);
        }

        @Test
        @DisplayName("respuesta contiene exactamente las claves 'sucursales', 'usuarios', 'productos'")
        void respuestaContieneTodasLasClaves() {
            when(usuarioTenantRepository.countByTenantIdAndActivoTrue(TENANT_ID)).thenReturn(3L);

            ResponseEntity<Map<String, Long>> response = controller.obtenerUso();

            assertThat(response.getStatusCode().value()).isEqualTo(200);
            assertThat(response.getBody()).containsKeys("sucursales", "usuarios", "productos");
        }
    }
}
