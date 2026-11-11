package com.stockflow.service;

import com.stockflow.dto.SucursalDTO;
import com.stockflow.entity.Sucursal;
import com.stockflow.exception.BadRequestException;
import com.stockflow.repository.*;
import com.stockflow.service.impl.SucursalServiceImpl;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;

import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;

/**
 * Regresión: SucursalesPage bug — botón "Nueva sucursal" visible en plan BÁSICO.
 * Verifica que el backend rechaza la creación cuando el límite del plan se alcanza,
 * para todos los escenarios relevantes.
 */
@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
@DisplayName("SucursalService.crear — validación de límite según plan")
class SucursalCrearLimitePlanTest {

    private static final String TENANT = "vet-test";

    @Mock private SucursalRepository                      sucursalRepository;
    @Mock private ProductoStockSucursalRepository         stockSucursalRepository;
    @Mock private ProductoVarianteStockSucursalRepository varianteStockSucursalRepository;
    @Mock private PlanLimitService                        planLimitService;
    @Mock private VentaRepository                         ventaRepository;
    @Mock private MovimientoInventarioRepository          movimientoRepository;
    @Mock private GastoRepository                         gastoRepository;
    @Mock private CajaRepository                          cajaRepository;
    @Mock private DevolucionRepository                    devolucionRepository;
    @Mock private NotaCreditoRepository                   notaCreditoRepository;
    @Mock private OrdenCompraRepository                   ordenCompraRepository;
    @Mock private RecepcionRepository                     recepcionRepository;
    @Mock private ComprobanteRepository                   comprobanteRepository;
    @Mock private UsuarioTenantRepository                 usuarioTenantRepository;
    @Mock private CertificadoRepository                   certificadoRepository;

    @InjectMocks
    private SucursalServiceImpl sucursalService;

    private SucursalDTO dtoNueva() {
        return SucursalDTO.builder()
                .nombre("Sucursal Nueva")
                .direccion("Av. Test 123")
                .build();
    }

    private Sucursal sucursalGuardada() {
        return Sucursal.builder()
                .id(99L)
                .tenantId(TENANT)
                .nombre("Sucursal Nueva")
                .activo(true)
                .build();
    }

    /** Mocks mínimos para que el flujo de creación PRO llegue hasta el final sin NPE. */
    private void mockearSaveExitoso() {
        when(sucursalRepository.save(any(Sucursal.class))).thenReturn(sucursalGuardada());
    }

    // ── 1. BÁSICO: cualquier intento de crear lanza BadRequestException ──────

    @Nested
    @DisplayName("Plan BÁSICO — crear siempre rechazado")
    class PlanBasico {

        @Test
        @DisplayName("BÁSICO con 0 sucursales → rechazado por validarPlanPro")
        void basico_conCeroSucursales_lanzaBadRequest() {
            when(planLimitService.isBasico(TENANT)).thenReturn(true);

            assertThatThrownBy(() -> sucursalService.crear(dtoNueva(), TENANT))
                    .isInstanceOf(BadRequestException.class)
                    .hasMessageContaining("plan Pro");
        }

        @Test
        @DisplayName("BÁSICO con 1 sucursal activa → rechazado por validarPlanPro")
        void basico_conUnaSucursalActiva_lanzaBadRequest() {
            when(planLimitService.isBasico(TENANT)).thenReturn(true);
            when(sucursalRepository.countByTenantIdAndActivoTrue(TENANT)).thenReturn(1L);

            assertThatThrownBy(() -> sucursalService.crear(dtoNueva(), TENANT))
                    .isInstanceOf(BadRequestException.class)
                    .hasMessageContaining("plan Pro");
        }
    }

    // ── 2. PRO dentro del límite: crear debe ser permitido ──────────────────

    @Nested
    @DisplayName("Plan PRO — crear permitido dentro del límite")
    class PlanProDentroDelLimite {

        @Test
        @DisplayName("PRO con 0 de 5 activas → no lanza excepción en validaciones")
        void pro_conCeroActivas_noLanzaExcepcion() {
            when(planLimitService.isBasico(TENANT)).thenReturn(false);
            when(planLimitService.getMaxSucursales(TENANT)).thenReturn(5);
            when(sucursalRepository.countByTenantIdAndActivoTrue(TENANT)).thenReturn(0L);
            mockearSaveExitoso();

            assertThatCode(() -> sucursalService.crear(dtoNueva(), TENANT))
                    .doesNotThrowAnyException();
        }

        @Test
        @DisplayName("PRO con 4 de 5 activas → no lanza excepción en validaciones")
        void pro_conCuatroActivas_noLanzaExcepcion() {
            when(planLimitService.isBasico(TENANT)).thenReturn(false);
            when(planLimitService.getMaxSucursales(TENANT)).thenReturn(5);
            when(sucursalRepository.countByTenantIdAndActivoTrue(TENANT)).thenReturn(4L);
            mockearSaveExitoso();

            assertThatCode(() -> sucursalService.crear(dtoNueva(), TENANT))
                    .doesNotThrowAnyException();
        }
    }

    // ── 3. PRO en el límite exacto: crear debe ser rechazado ────────────────

    @Nested
    @DisplayName("Plan PRO — crear rechazado al alcanzar el límite")
    class PlanProEnLimite {

        @Test
        @DisplayName("PRO con 5 de 5 activas → rechazado por validarLimiteSucursales")
        void pro_conCincoActivas_lanzaBadRequest() {
            when(planLimitService.isBasico(TENANT)).thenReturn(false);
            when(planLimitService.getMaxSucursales(TENANT)).thenReturn(5);
            when(sucursalRepository.countByTenantIdAndActivoTrue(TENANT)).thenReturn(5L);

            assertThatThrownBy(() -> sucursalService.crear(dtoNueva(), TENANT))
                    .isInstanceOf(BadRequestException.class)
                    .hasMessageContaining("máximo");
        }
    }

    // ── 4. Downgrade PRO → BÁSICO: creación rechazada post-downgrade ────────

    @Nested
    @DisplayName("Post-downgrade PRO → BÁSICO — crear siempre rechazado")
    class PostDowngrade {

        @Test
        @DisplayName("Post-downgrade con 1 sucursal activa (bloqueada excluida) → rechazado")
        void postDowngrade_conUnaSucursalActiva_lanzaBadRequest() {
            // Después del downgrade isBasico=true, la sucursal bloqueada no cuenta
            when(planLimitService.isBasico(TENANT)).thenReturn(true);
            when(sucursalRepository.countByTenantIdAndActivoTrue(TENANT)).thenReturn(1L);

            assertThatThrownBy(() -> sucursalService.crear(dtoNueva(), TENANT))
                    .isInstanceOf(BadRequestException.class)
                    .hasMessageContaining("plan Pro");
        }
    }

    // ── 5. Upgrade BÁSICO → PRO: crear permitido dentro del nuevo límite ────

    @Nested
    @DisplayName("Post-upgrade BÁSICO → PRO — crear permitido")
    class PostUpgrade {

        @Test
        @DisplayName("Post-upgrade con 1 activa de 5 → no lanza excepción")
        void postUpgrade_conUnaActiva_noLanzaExcepcion() {
            when(planLimitService.isBasico(TENANT)).thenReturn(false);
            when(planLimitService.getMaxSucursales(TENANT)).thenReturn(5);
            when(sucursalRepository.countByTenantIdAndActivoTrue(TENANT)).thenReturn(1L);
            mockearSaveExitoso();

            assertThatCode(() -> sucursalService.crear(dtoNueva(), TENANT))
                    .doesNotThrowAnyException();
        }
    }

    // ── 6. Mensaje de error descriptivo ─────────────────────────────────────

    @Nested
    @DisplayName("Mensajes de error — deben ser claros y accionables")
    class MensajesDeError {

        @Test
        @DisplayName("BÁSICO → mensaje menciona 'plan Pro'")
        void basico_mensajeMencionaPlanPro() {
            when(planLimitService.isBasico(TENANT)).thenReturn(true);

            assertThatThrownBy(() -> sucursalService.crear(dtoNueva(), TENANT))
                    .isInstanceOf(BadRequestException.class)
                    .hasMessageContaining("plan Pro");
        }

        @Test
        @DisplayName("PRO en límite → mensaje menciona 'máximo' y el número de sucursales")
        void pro_enLimite_mensajeMencionaMaximo() {
            when(planLimitService.isBasico(TENANT)).thenReturn(false);
            when(planLimitService.getMaxSucursales(TENANT)).thenReturn(5);
            when(sucursalRepository.countByTenantIdAndActivoTrue(TENANT)).thenReturn(5L);

            assertThatThrownBy(() -> sucursalService.crear(dtoNueva(), TENANT))
                    .isInstanceOf(BadRequestException.class)
                    .hasMessageContaining("máximo")
                    .hasMessageContaining("5");
        }
    }
}
