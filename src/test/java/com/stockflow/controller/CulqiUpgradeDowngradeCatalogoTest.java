package com.stockflow.controller;

import com.stockflow.config.properties.CulqiProperties;
import com.stockflow.dto.CulqiSuscribirRequestDTO;
import com.stockflow.entity.PlanCatalogo;
import com.stockflow.entity.Suscripcion;
import com.stockflow.entity.Usuario;
import com.stockflow.exception.BadRequestException;
import com.stockflow.repository.PlanCatalogoRepository;
import com.stockflow.repository.SuscripcionRepository;
import com.stockflow.service.CulqiService;
import com.stockflow.service.EmailService;
import com.stockflow.service.SucursalService;
import com.stockflow.service.SuscripcionService;
import com.stockflow.service.UsuarioService;
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
import org.mockito.MockedStatic;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;

import java.math.BigDecimal;
import java.util.Map;
import java.util.Optional;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

/**
 * Tests para upgrade y downgrade con routing por segmento del catálogo.
 *
 * Cada test verifica que:
 *  - El culqi_plan_id correcto (del catálogo) llega a crearSuscripcion.
 *  - El precio correcto se guarda en la suscripción local.
 *  - El plan_codigo en suscripciones se actualiza al código destino.
 *  - El segmento se conserva — no hay mezcla entre rubros.
 */
@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
@DisplayName("Upgrade / downgrade — routing por segmento del catálogo")
class CulqiUpgradeDowngradeCatalogoTest {

    @Mock private CulqiService           culqiService;
    @Mock private CulqiProperties        culqiProperties;
    @Mock private PlanCatalogoRepository planCatalogoRepository;
    @Mock private SuscripcionRepository  suscripcionRepository;
    @Mock private SuscripcionService     suscripcionService;
    @Mock private UsuarioService         usuarioService;
    @Mock private SucursalService        sucursalService;
    @Mock private EmailService           emailService;

    @InjectMocks
    private CulqiController controller;

    private MockedStatic<TenantContext> tenantContextMock;

    private static final String TENANT_A = "tenant-a-001";
    private static final String TENANT_B = "tenant-b-002";
    private static final long   USR_ID   = 10L;

    @BeforeEach
    void setUp() {
        tenantContextMock = mockStatic(TenantContext.class);
        tenantContextMock.when(TenantContext::getCurrentTenant).thenReturn(TENANT_A);
        tenantContextMock.when(TenantContext::getCurrentUserId).thenReturn(USR_ID);

        Usuario usuario = new Usuario();
        usuario.setId(USR_ID);
        usuario.setEmail("owner@example.com");
        usuario.setNombre("Owner");
        when(usuarioService.obtenerUsuarioPorId(USR_ID)).thenReturn(Optional.of(usuario));

        when(culqiProperties.getPrecioBasico()).thenReturn(new BigDecimal("89.00"));
        when(culqiProperties.getPrecioPro()).thenReturn(new BigDecimal("169.00"));
        when(culqiProperties.getPlanIdBasico()).thenReturn("plan_legacy_bas");
        when(culqiProperties.getPlanIdPro()).thenReturn("plan_legacy_pro");

        when(culqiService.crearCliente(anyString(), any(), any(), any())).thenReturn("cus_x");
        when(culqiService.crearTarjeta(anyString(), anyString())).thenReturn(Map.of("id", "crd_x"));
        when(culqiService.crearSuscripcion(anyString(), anyString(), any())).thenReturn("sub_nuevo");
        when(suscripcionRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));
    }

    @AfterEach
    void tearDown() {
        tenantContextMock.close();
    }

    // ── VETERINARIA ──────────────────────────────────────────────────────────

    @Nested
    @DisplayName("Veterinaria")
    class Veterinaria {

        @Test
        @DisplayName("VETERINARIA_BASICO → upgrade → VETERINARIA_PRO (S/189)")
        void veterinariaBasico_upgrade_veterinariaPro() {
            Suscripcion basico = activaCon("VETERINARIA_BASICO", "BASICO");
            mockCatalogo("VETERINARIA_PRO",   "189.00", "plan_culqi_18900");
            mockCatalogo("VETERINARIA_BASICO", "109.00", "plan_culqi_10900");
            when(suscripcionRepository.findFirstByTenantIdOrderByIdDesc(TENANT_A))
                    .thenReturn(Optional.of(basico));

            var resp = controller.upgradePro(req("tok_up"));

            verify(culqiService).crearSuscripcion(any(), eq("plan_culqi_18900"), any());
            assertThat(resp.getBody().getPrecioMensual()).isEqualByComparingTo("189.00");
            assertThat(basico.getPlanCodigo()).isEqualTo("VETERINARIA_PRO");
        }

        @Test
        @DisplayName("VETERINARIA_PRO → downgrade → VETERINARIA_BASICO (S/109)")
        void veterinariaPro_downgrade_veterinariaBasico() {
            Suscripcion pro = activaCon("VETERINARIA_PRO", "PRO");
            mockCatalogo("VETERINARIA_BASICO", "109.00", "plan_culqi_10900");
            when(suscripcionRepository.findFirstByTenantIdOrderByIdDesc(TENANT_A))
                    .thenReturn(Optional.of(pro));

            var resp = controller.downgradeBasico(req("tok_down"));

            verify(culqiService).crearSuscripcion(any(), eq("plan_culqi_10900"), any());
            assertThat(resp.getBody().getPrecioMensual()).isEqualByComparingTo("109.00");
            assertThat(pro.getPlanCodigo()).isEqualTo("VETERINARIA_BASICO");
        }
    }

    // ── BOTICA ───────────────────────────────────────────────────────────────

    @Nested
    @DisplayName("Botica — diferente precio a Veterinaria, mismo nivel")
    class Botica {

        @Test
        @DisplayName("BOTICA_BASICO → upgrade → BOTICA_PRO (S/169)")
        void boticaBasico_upgrade_boticaPro() {
            Suscripcion basico = activaCon("BOTICA_BASICO", "BASICO");
            mockCatalogo("BOTICA_PRO",   "169.00", "plan_culqi_16900");
            mockCatalogo("BOTICA_BASICO", "89.00",  "plan_culqi_8900");
            when(suscripcionRepository.findFirstByTenantIdOrderByIdDesc(TENANT_A))
                    .thenReturn(Optional.of(basico));

            var resp = controller.upgradePro(req("tok_up"));

            verify(culqiService).crearSuscripcion(any(), eq("plan_culqi_16900"), any());
            assertThat(resp.getBody().getPrecioMensual()).isEqualByComparingTo("169.00");
            assertThat(basico.getPlanCodigo()).isEqualTo("BOTICA_PRO");
        }
    }

    // ── FARMACIA ─────────────────────────────────────────────────────────────

    @Nested
    @DisplayName("Farmacia — mismo precio que Botica pero código distinto")
    class Farmacia {

        @Test
        @DisplayName("FARMACIA_BASICO → upgrade → FARMACIA_PRO (S/169, código correcto)")
        void farmaciaBasico_upgrade_farmaciaPro() {
            Suscripcion basico = activaCon("FARMACIA_BASICO", "BASICO");
            mockCatalogo("FARMACIA_PRO",   "169.00", "plan_culqi_16900");
            mockCatalogo("FARMACIA_BASICO", "89.00",  "plan_culqi_8900");
            when(suscripcionRepository.findFirstByTenantIdOrderByIdDesc(TENANT_A))
                    .thenReturn(Optional.of(basico));

            var resp = controller.upgradePro(req("tok_up"));

            verify(culqiService).crearSuscripcion(any(), eq("plan_culqi_16900"), any());
            // El plan_codigo debe ser FARMACIA_PRO, no BOTICA_PRO ni OTRO_PRO
            assertThat(basico.getPlanCodigo()).isEqualTo("FARMACIA_PRO");
        }
    }

    // ── TIENDA ROPA ──────────────────────────────────────────────────────────

    @Nested
    @DisplayName("Tienda de ropa")
    class TiendaRopa {

        @Test
        @DisplayName("TIENDA_ROPA_BASICO → upgrade → TIENDA_ROPA_PRO")
        void tiendaRopaBasico_upgrade_tiendaRopaPro() {
            Suscripcion basico = activaCon("TIENDA_ROPA_BASICO", "BASICO");
            mockCatalogo("TIENDA_ROPA_PRO",   "169.00", "plan_culqi_16900");
            mockCatalogo("TIENDA_ROPA_BASICO", "89.00",  "plan_culqi_8900");
            when(suscripcionRepository.findFirstByTenantIdOrderByIdDesc(TENANT_A))
                    .thenReturn(Optional.of(basico));

            controller.upgradePro(req("tok_up"));

            assertThat(basico.getPlanCodigo()).isEqualTo("TIENDA_ROPA_PRO");
        }
    }

    // ── TIENDA BODEGA ────────────────────────────────────────────────────────

    @Nested
    @DisplayName("Tienda bodega — precio diferente a Tienda ropa")
    class TiendaBodega {

        @Test
        @DisplayName("TIENDA_BODEGA_BASICO (S/79) → upgrade → TIENDA_BODEGA_PRO (S/149)")
        void tiendaBodegaBasico_upgrade_tiendaBodegaPro() {
            Suscripcion basico = activaCon("TIENDA_BODEGA_BASICO", "BASICO");
            mockCatalogo("TIENDA_BODEGA_PRO",   "149.00", "plan_culqi_14900");
            mockCatalogo("TIENDA_BODEGA_BASICO", "79.00",  "plan_culqi_7900");
            when(suscripcionRepository.findFirstByTenantIdOrderByIdDesc(TENANT_A))
                    .thenReturn(Optional.of(basico));

            var resp = controller.upgradePro(req("tok_up"));

            verify(culqiService).crearSuscripcion(any(), eq("plan_culqi_14900"), any());
            assertThat(resp.getBody().getPrecioMensual()).isEqualByComparingTo("149.00");
            assertThat(basico.getPlanCodigo()).isEqualTo("TIENDA_BODEGA_PRO");
        }

        @Test
        @DisplayName("TIENDA_BODEGA_PRO → downgrade → TIENDA_BODEGA_BASICO (S/79)")
        void tiendaBodegaPro_downgrade_tiendaBodegaBasico() {
            Suscripcion pro = activaCon("TIENDA_BODEGA_PRO", "PRO");
            mockCatalogo("TIENDA_BODEGA_BASICO", "79.00", "plan_culqi_7900");
            when(suscripcionRepository.findFirstByTenantIdOrderByIdDesc(TENANT_A))
                    .thenReturn(Optional.of(pro));

            var resp = controller.downgradeBasico(req("tok_down"));

            verify(culqiService).crearSuscripcion(any(), eq("plan_culqi_7900"), any());
            assertThat(resp.getBody().getPrecioMensual()).isEqualByComparingTo("79.00");
        }
    }

    // ── ALMACENES ────────────────────────────────────────────────────────────

    @Nested
    @DisplayName("Almacenes — usa ALMACEN_ (sin S), no ALMACENES_")
    class Almacenes {

        @Test
        @DisplayName("ALMACEN_BASICO → upgrade → ALMACEN_PRO (no ALMACENES_PRO ni OTRO_PRO)")
        void almacenBasico_upgrade_almacenPro() {
            Suscripcion basico = activaCon("ALMACEN_BASICO", "BASICO");
            mockCatalogo("ALMACEN_PRO",   "169.00", "plan_culqi_almacen_pro");
            mockCatalogo("ALMACEN_BASICO", "89.00",  "plan_culqi_almacen_bas");
            when(suscripcionRepository.findFirstByTenantIdOrderByIdDesc(TENANT_A))
                    .thenReturn(Optional.of(basico));

            controller.upgradePro(req("tok_up"));

            // Verifica que se consulta ALMACEN_PRO, no ALMACENES_PRO ni ningún otro código
            verify(planCatalogoRepository, atLeastOnce()).findByCodigoAndActivoTrue("ALMACEN_PRO");
            assertThat(basico.getPlanCodigo()).isEqualTo("ALMACEN_PRO");
        }

        @Test
        @DisplayName("ALMACEN_PRO → downgrade → ALMACEN_BASICO (no confunde con ALMACENES_)")
        void almacenPro_downgrade_almacenBasico() {
            Suscripcion pro = activaCon("ALMACEN_PRO", "PRO");
            mockCatalogo("ALMACEN_BASICO", "89.00", "plan_culqi_almacen_bas2");
            when(suscripcionRepository.findFirstByTenantIdOrderByIdDesc(TENANT_A))
                    .thenReturn(Optional.of(pro));

            controller.downgradeBasico(req("tok_down"));

            verify(planCatalogoRepository, atLeastOnce()).findByCodigoAndActivoTrue("ALMACEN_BASICO");
            assertThat(pro.getPlanCodigo()).isEqualTo("ALMACEN_BASICO");
        }
    }

    // ── Todos los rubros conservan su segmento ───────────────────────────────

    @Nested
    @DisplayName("Todos los rubros conservan su segmento exacto")
    class TodosLosRubros {

        @Test
        @DisplayName("RESTAURANTE, MINIMARKET, FERRETERIA, EMPRESA_SERVICIOS, OTRO — upgrade preserva prefijo")
        void otrosRubros_upgrade_preservaSegmento() {
            String[][] rubros = {
                {"RESTAURANTE_BASICO",       "RESTAURANTE_PRO",       "119.00", "199.00"},
                {"MINIMARKET_BASICO",         "MINIMARKET_PRO",         "99.00",  "179.00"},
                {"FERRETERIA_BASICO",         "FERRETERIA_PRO",         "99.00",  "179.00"},
                {"EMPRESA_SERVICIOS_BASICO",  "EMPRESA_SERVICIOS_PRO",  "99.00",  "179.00"},
                {"OTRO_BASICO",               "OTRO_PRO",               "89.00",  "169.00"},
            };

            for (String[] r : rubros) {
                String baseCodigo = r[0], proCodigo = r[1];
                String planIdPro = "plan_" + proCodigo.toLowerCase();

                mockCatalogo(proCodigo,  r[3], planIdPro);
                mockCatalogo(baseCodigo, r[2], "plan_" + baseCodigo.toLowerCase());

                Suscripcion basico = activaCon(baseCodigo, "BASICO");
                when(suscripcionRepository.findFirstByTenantIdOrderByIdDesc(TENANT_A))
                        .thenReturn(Optional.of(basico));

                controller.upgradePro(req("tok"));

                assertThat(basico.getPlanCodigo())
                        .as("Segmento incorrecto para %s", baseCodigo)
                        .isEqualTo(proCodigo);

                // Reset para el siguiente rubro
                reset(suscripcionRepository, culqiService);
                when(culqiService.crearCliente(anyString(), any(), any(), any())).thenReturn("cus_x");
                when(culqiService.crearTarjeta(anyString(), anyString())).thenReturn(Map.of("id", "crd_x"));
                when(culqiService.crearSuscripcion(anyString(), anyString(), any())).thenReturn("sub_nuevo");
                when(suscripcionRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));
            }
        }
    }

    // ── Ruta legada (plan_codigo NULL) ───────────────────────────────────────

    @Nested
    @DisplayName("Ruta legada — plan_codigo NULL")
    class Legado {

        @Test
        @DisplayName("upgrade con plan_codigo NULL usa CULQI_PLAN_ID_PRO (env var)")
        void upgrade_planCodigoNull_usaEnvVar() {
            Suscripcion legada = activaCon(null, "BASICO");
            legada.setPreapprovalId("sub_old");
            when(suscripcionRepository.findFirstByTenantIdOrderByIdDesc(TENANT_A))
                    .thenReturn(Optional.of(legada));

            controller.upgradePro(req("tok_up"));

            verify(culqiService).crearSuscripcion(any(), eq("plan_legacy_pro"), any());
            verify(planCatalogoRepository, never()).findByCodigoAndActivoTrue(any());
        }

        @Test
        @DisplayName("downgrade con plan_codigo NULL usa CULQI_PLAN_ID_BASICO (env var)")
        void downgrade_planCodigoNull_usaEnvVar() {
            Suscripcion legada = activaCon(null, "PRO");
            legada.setPreapprovalId("sub_old_pro");
            when(suscripcionRepository.findFirstByTenantIdOrderByIdDesc(TENANT_A))
                    .thenReturn(Optional.of(legada));

            controller.downgradeBasico(req("tok_down"));

            verify(culqiService).crearSuscripcion(any(), eq("plan_legacy_bas"), any());
            verify(planCatalogoRepository, never()).findByCodigoAndActivoTrue(any());
        }
    }

    // ── Sin culqi_plan_id configurado ────────────────────────────────────────

    @Nested
    @DisplayName("Plan destino sin culqi_plan_id — error, sin cobro")
    class PlanSinCulqiId {

        @Test
        @DisplayName("upgrade a plan sin culqi_plan_id → BadRequestException, sin llamadas a Culqi")
        void upgrade_destinoSinCulqiPlanId_lanza() {
            Suscripcion basico = activaCon("VETERINARIA_BASICO", "BASICO");
            mockCatalogo("VETERINARIA_PRO",   "189.00", null);  // sin culqi_plan_id
            mockCatalogo("VETERINARIA_BASICO", "109.00", "plan_ok_bas");
            when(suscripcionRepository.findFirstByTenantIdOrderByIdDesc(TENANT_A))
                    .thenReturn(Optional.of(basico));

            assertThatThrownBy(() -> controller.upgradePro(req("tok_up")))
                    .isInstanceOf(BadRequestException.class)
                    .hasMessageContaining("culqi_plan_id");

            // Ninguna llamada de Culqi — ni customer, ni tarjeta, ni suscripción
            verify(culqiService, never()).crearCliente(any(), any(), any(), any());
            verify(culqiService, never()).crearSuscripcion(any(), any(), any());
        }

        @Test
        @DisplayName("downgrade a plan sin culqi_plan_id → BadRequestException, sin llamadas a Culqi")
        void downgrade_destinoSinCulqiPlanId_lanza() {
            Suscripcion pro = activaCon("VETERINARIA_PRO", "PRO");
            mockCatalogo("VETERINARIA_BASICO", "109.00", null);  // sin culqi_plan_id
            when(suscripcionRepository.findFirstByTenantIdOrderByIdDesc(TENANT_A))
                    .thenReturn(Optional.of(pro));

            assertThatThrownBy(() -> controller.downgradeBasico(req("tok_down")))
                    .isInstanceOf(BadRequestException.class)
                    .hasMessageContaining("culqi_plan_id");

            verify(culqiService, never()).crearCliente(any(), any(), any(), any());
        }
    }

    // ── Aislamiento multi-tenant ──────────────────────────────────────────────

    @Nested
    @DisplayName("Aislamiento multi-tenant")
    class AislamientoMultiTenant {

        @Test
        @DisplayName("upgrade usa TenantContext.getCurrentTenant() para filtrar la suscripción")
        void upgrade_usaTenantContextParaFiltrar() {
            // TenantContext devuelve TENANT_A; el repo no devuelve suscripción para TENANT_A
            when(suscripcionRepository.findFirstByTenantIdOrderByIdDesc(TENANT_A))
                    .thenReturn(Optional.empty());

            // No hay plan_codigo (null), ruta legada → falla por falta de env var
            when(culqiProperties.getPlanIdPro()).thenReturn("plan_legacy_pro");
            when(suscripcionRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));

            // Debe ejecutarse sin errores y usar plan_legacy_pro
            assertThatNoException().isThrownBy(() -> controller.upgradePro(req("tok")));

            // NUNCA debe consultar el repositorio con TENANT_B
            verify(suscripcionRepository, never())
                    .findFirstByTenantIdOrderByIdDesc(TENANT_B);
        }

        @Test
        @DisplayName("downgrade solo ve la suscripción del tenant autenticado, no de otro")
        void downgrade_noAccedeASuscripcionDeOtroTenant() {
            // Para TENANT_A → no hay suscripción
            when(suscripcionRepository.findFirstByTenantIdOrderByIdDesc(TENANT_A))
                    .thenReturn(Optional.empty());

            // Debe lanzar "No tienes una suscripción activa" (no encontró la del tenant A)
            assertThatThrownBy(() -> controller.downgradeBasico(req("tok")))
                    .isInstanceOf(BadRequestException.class)
                    .hasMessageContaining("No tienes una suscripción activa");

            verify(suscripcionRepository, never())
                    .findFirstByTenantIdOrderByIdDesc(TENANT_B);
        }
    }

    // ── Helpers ──────────────────────────────────────────────────────────────

    private Suscripcion activaCon(String planCodigo, String planId) {
        Suscripcion s = new Suscripcion();
        s.setId(1L);
        s.setTenantId(TENANT_A);
        s.setEstado("ACTIVA");
        s.setPlanCodigo(planCodigo);
        s.setPlanId(planId);
        s.setPreapprovalId("sub_existente");
        return s;
    }

    private void mockCatalogo(String codigo, String precio, String culqiPlanId) {
        PlanCatalogo p = PlanCatalogo.builder()
                .codigo(codigo)
                .segmento(codigo.split("_")[0])
                .rubroOperativo(codigo.split("_")[0])
                .nivel(codigo.endsWith("PRO") ? "PRO" : "BASICO")
                .precioMensual(new BigDecimal(precio))
                .maxUsuarios(5).maxSucursales(2).maxProductos(1000)
                .activo(true)
                .culqiPlanId(culqiPlanId)
                .build();
        when(planCatalogoRepository.findByCodigoAndActivoTrue(codigo)).thenReturn(Optional.of(p));
    }

    private static CulqiSuscribirRequestDTO req(String tokenId) {
        CulqiSuscribirRequestDTO r = new CulqiSuscribirRequestDTO();
        r.setPlanId("PRO");
        r.setTokenId(tokenId);
        return r;
    }
}
