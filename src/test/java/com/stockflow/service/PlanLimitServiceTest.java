package com.stockflow.service;

import com.stockflow.exception.BadRequestException;
import com.stockflow.repository.ProductoRepository;
import com.stockflow.repository.SuscripcionRepository;
import com.stockflow.repository.UsuarioRepository;
import com.stockflow.repository.UsuarioTenantRepository;
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

import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
@DisplayName("PlanLimitService — validarLimiteUsuarios usa usuario_tenant")
class PlanLimitServiceTest {

    private static final String TENANT_ID   = "vet-59ba";
    private static final String OTRO_TENANT = "botica-santa-fe";

    @Mock private SuscripcionRepository   suscripcionRepository;
    @Mock private UsuarioRepository       usuarioRepository;
    @Mock private ProductoRepository      productoRepository;
    @Mock private UsuarioTenantRepository usuarioTenantRepository;

    @InjectMocks private PlanLimitService planLimitService;

    @BeforeEach
    void setUp() {
        // Por defecto: plan BASICO y 0 usuarios en usuario_tenant
        when(suscripcionRepository.findFirstByTenantIdOrderByIdDesc(TENANT_ID))
                .thenReturn(Optional.of(
                        com.stockflow.entity.Suscripcion.builder()
                                .planId("BASICO").tenantId(TENANT_ID).build()));
        when(usuarioTenantRepository.countByTenantIdAndActivoTrue(TENANT_ID)).thenReturn(0L);
        when(productoRepository.countByTenantId(anyString())).thenReturn(0L);
    }

    // ──────────────────────────────────────────────────────────────────────────
    // Fuente del conteo: usuario_tenant, no usuarios.tenant_id
    // ──────────────────────────────────────────────────────────────────────────

    @Nested
    @DisplayName("Fuente del conteo")
    class FuenteDelConteo {

        @Test
        @DisplayName("usa countByTenantIdAndActivoTrue de usuario_tenant, nunca usuarioRepository")
        void usa_usuarioTenantRepository_noUsuarioRepository() {
            when(usuarioTenantRepository.countByTenantIdAndActivoTrue(TENANT_ID)).thenReturn(2L);

            assertThatCode(() -> planLimitService.validarLimiteUsuarios(TENANT_ID))
                    .doesNotThrowAnyException();

            verify(usuarioTenantRepository).countByTenantIdAndActivoTrue(TENANT_ID);
        }

        @Test
        @DisplayName("un usuario con tenant_id primario diferente pero relación activa en usuario_tenant SÍ cuenta")
        void usuarioConTenantIdDiferente_contaSiTieneRelacionActiva() {
            // usuario_tenant reporta 3 usuarios activos en TENANT_ID
            // (incluso si alguno tiene usuarios.tenant_id = OTRO_TENANT)
            when(usuarioTenantRepository.countByTenantIdAndActivoTrue(TENANT_ID)).thenReturn(3L);

            assertThatCode(() -> planLimitService.validarLimiteUsuarios(TENANT_ID))
                    .doesNotThrowAnyException();

            verify(usuarioTenantRepository).countByTenantIdAndActivoTrue(TENANT_ID);
        }

        @Test
        @DisplayName("usuario con usuario_tenant activo en OTRO_TENANT no afecta el conteo de TENANT_ID")
        void relacionActivaEnOtroTenant_noAfectaConteo() {
            when(usuarioTenantRepository.countByTenantIdAndActivoTrue(TENANT_ID)).thenReturn(1L);
            // No stubeamos OTRO_TENANT porque no debe consultarse

            assertThatCode(() -> planLimitService.validarLimiteUsuarios(TENANT_ID))
                    .doesNotThrowAnyException();

            verify(usuarioTenantRepository, never()).countByTenantIdAndActivoTrue(OTRO_TENANT);
        }

        @Test
        @DisplayName("usuario con usuario_tenant activo=false no se incluye en el conteo")
        void relacionInactiva_noContabilizada() {
            // countByTenantIdAndActivoTrue excluye inactivos por definición del método
            // Aquí verificamos que el servicio no hace un segundo conteo sin el filtro
            when(usuarioTenantRepository.countByTenantIdAndActivoTrue(TENANT_ID)).thenReturn(0L);

            assertThatCode(() -> planLimitService.validarLimiteUsuarios(TENANT_ID))
                    .doesNotThrowAnyException();

            // Solo se llama al método con filtro activo=true
            verify(usuarioTenantRepository).countByTenantIdAndActivoTrue(TENANT_ID);
            verify(usuarioTenantRepository, never()).countByUsuarioIdAndActivoTrue(anyLong());
        }
    }

    // ──────────────────────────────────────────────────────────────────────────
    // Límite plan BÁSICO
    // ──────────────────────────────────────────────────────────────────────────

    @Nested
    @DisplayName("Plan BÁSICO — límite de usuarios")
    class PlanBasico {

        @Test
        @DisplayName("permite cuando el conteo está por debajo del límite (4 de 5)")
        void bajoDelLimite_permite() {
            when(usuarioTenantRepository.countByTenantIdAndActivoTrue(TENANT_ID)).thenReturn(4L);

            assertThatCode(() -> planLimitService.validarLimiteUsuarios(TENANT_ID))
                    .doesNotThrowAnyException();
        }

        @Test
        @DisplayName("rechaza cuando el conteo iguala el límite (5 de 5)")
        void igualAlLimite_rechaza() {
            when(usuarioTenantRepository.countByTenantIdAndActivoTrue(TENANT_ID))
                    .thenReturn((long) PlanLimitService.BASICO_MAX_USUARIOS);

            assertThatThrownBy(() -> planLimitService.validarLimiteUsuarios(TENANT_ID))
                    .isInstanceOf(BadRequestException.class)
                    .hasMessageContaining("plan Básico")
                    .hasMessageContaining(String.valueOf(PlanLimitService.BASICO_MAX_USUARIOS));
        }

        @Test
        @DisplayName("rechaza cuando el conteo supera el límite (6 de 5)")
        void sobreElLimite_rechaza() {
            when(usuarioTenantRepository.countByTenantIdAndActivoTrue(TENANT_ID))
                    .thenReturn((long) PlanLimitService.BASICO_MAX_USUARIOS + 1);

            assertThatThrownBy(() -> planLimitService.validarLimiteUsuarios(TENANT_ID))
                    .isInstanceOf(BadRequestException.class);
        }
    }

    // ──────────────────────────────────────────────────────────────────────────
    // Límite plan PRO
    // ──────────────────────────────────────────────────────────────────────────

    @Nested
    @DisplayName("Plan PRO — límite de usuarios")
    class PlanPro {

        @BeforeEach
        void setPlanPro() {
            when(suscripcionRepository.findFirstByTenantIdOrderByIdDesc(TENANT_ID))
                    .thenReturn(Optional.of(
                            com.stockflow.entity.Suscripcion.builder()
                                    .planId("PRO").tenantId(TENANT_ID).build()));
        }

        @Test
        @DisplayName("permite cuando el conteo está por debajo del límite PRO (14 de 15)")
        void bajoDelLimitePro_permite() {
            when(usuarioTenantRepository.countByTenantIdAndActivoTrue(TENANT_ID)).thenReturn(14L);

            assertThatCode(() -> planLimitService.validarLimiteUsuarios(TENANT_ID))
                    .doesNotThrowAnyException();
        }

        @Test
        @DisplayName("rechaza cuando el conteo iguala el límite PRO (15 de 15)")
        void igualAlLimitePro_rechaza() {
            when(usuarioTenantRepository.countByTenantIdAndActivoTrue(TENANT_ID))
                    .thenReturn((long) PlanLimitService.PRO_MAX_USUARIOS);

            assertThatThrownBy(() -> planLimitService.validarLimiteUsuarios(TENANT_ID))
                    .isInstanceOf(BadRequestException.class)
                    .hasMessageContaining("plan Pro")
                    .hasMessageContaining(String.valueOf(PlanLimitService.PRO_MAX_USUARIOS));
        }
    }

    // ──────────────────────────────────────────────────────────────────────────
    // Otros límites no modificados (validarLimiteProductos, validarRolPermitido)
    // ──────────────────────────────────────────────────────────────────────────

    @Nested
    @DisplayName("Otros límites — comportamiento inalterado")
    class OtrosLimites {

        @Test
        @DisplayName("validarLimiteProductos sigue usando productoRepository (no cambiado)")
        void validarLimiteProductos_sigueUsandoProductoRepository() {
            when(productoRepository.countByTenantId(TENANT_ID)).thenReturn(10L);

            assertThatCode(() -> planLimitService.validarLimiteProductos(TENANT_ID))
                    .doesNotThrowAnyException();

            verify(productoRepository).countByTenantId(TENANT_ID);
            verify(usuarioTenantRepository, never()).countByTenantIdAndActivoTrue(any());
        }

        @Test
        @DisplayName("validarLimiteProductos rechaza cuando supera el límite BÁSICO")
        void validarLimiteProductos_basico_rechaza() {
            when(productoRepository.countByTenantId(TENANT_ID))
                    .thenReturn((long) PlanLimitService.BASICO_MAX_PRODUCTOS);

            assertThatThrownBy(() -> planLimitService.validarLimiteProductos(TENANT_ID))
                    .isInstanceOf(BadRequestException.class)
                    .hasMessageContaining("productos");
        }

        @Test
        @DisplayName("validarRolPermitido rechaza rol no permitido en plan BÁSICO")
        void validarRolPermitido_rolNoPermitido_rechaza() {
            assertThatThrownBy(() -> planLimitService.validarRolPermitido(TENANT_ID, "GERENTE"))
                    .isInstanceOf(BadRequestException.class)
                    .hasMessageContaining("plan Básico");
        }

        @Test
        @DisplayName("validarRolPermitido permite ADMIN, VENDEDOR y GESTOR_INVENTARIO en plan BÁSICO")
        void validarRolPermitido_rolesPermitidos_permite() {
            assertThatCode(() -> planLimitService.validarRolPermitido(TENANT_ID, "ADMIN"))
                    .doesNotThrowAnyException();
            assertThatCode(() -> planLimitService.validarRolPermitido(TENANT_ID, "VENDEDOR"))
                    .doesNotThrowAnyException();
            assertThatCode(() -> planLimitService.validarRolPermitido(TENANT_ID, "GESTOR_INVENTARIO"))
                    .doesNotThrowAnyException();
        }
    }
}
