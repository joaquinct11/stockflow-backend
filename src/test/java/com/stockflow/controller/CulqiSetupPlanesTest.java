package com.stockflow.controller;

import com.stockflow.config.properties.CulqiProperties;
import com.stockflow.dto.SetupPlanesResultDTO;
import com.stockflow.entity.PlanCatalogo;
import com.stockflow.exception.BadRequestException;
import com.stockflow.repository.PlanCatalogoRepository;
import com.stockflow.service.CulqiService;
import com.stockflow.service.EmailService;
import com.stockflow.service.SucursalService;
import com.stockflow.service.SuscripcionService;
import com.stockflow.service.UsuarioService;
import com.stockflow.repository.SuscripcionRepository;
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
import org.springframework.http.ResponseEntity;

import java.math.BigDecimal;
import java.util.List;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

/**
 * Tests unitarios para POST /api/culqi/admin/setup-planes.
 *
 * No llama a Culqi real — CulqiService está mockeado.
 * Verifica: idempotencia, guard de producción, creación de los 10 grupos,
 * propagación de IDs a filas del mismo precio y manejo de errores parciales.
 */
@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
@DisplayName("POST /admin/setup-planes — Culqi plan setup")
class CulqiSetupPlanesTest {

    @Mock private CulqiService          culqiService;
    @Mock private CulqiProperties       culqiProperties;
    @Mock private PlanCatalogoRepository planCatalogoRepository;
    @Mock private SuscripcionRepository  suscripcionRepository;
    @Mock private SuscripcionService    suscripcionService;
    @Mock private UsuarioService        usuarioService;
    @Mock private SucursalService       sucursalService;
    @Mock private EmailService          emailService;

    @InjectMocks
    private CulqiController controller;

    @BeforeEach
    void setUp() {
        when(culqiProperties.getSecretKey()).thenReturn("sk_test_abc123");
    }

    // ── Guard de producción ──────────────────────────────────────────────────

    @Test
    @DisplayName("sk_live_ sin header X-Confirm-Production → BadRequestException")
    void produccion_sinConfirmacion_lanza() {
        when(culqiProperties.getSecretKey()).thenReturn("sk_live_secretkey");
        when(planCatalogoRepository.findAllByActivoTrue()).thenReturn(List.of());

        assertThatThrownBy(() -> controller.setupPlanes(null))
                .isInstanceOf(BadRequestException.class)
                .hasMessageContaining("PRODUCCIÓN");
    }

    @Test
    @DisplayName("sk_live_ con header X-Confirm-Production: true → procede")
    void produccion_conConfirmacion_procede() {
        when(culqiProperties.getSecretKey()).thenReturn("sk_live_secretkey");
        when(planCatalogoRepository.findAllByActivoTrue()).thenReturn(List.of());

        ResponseEntity<SetupPlanesResultDTO> resp = controller.setupPlanes("true");

        assertThat(resp.getBody()).isNotNull();
        assertThat(resp.getBody().getEntorno()).isEqualTo("PRODUCCION");
    }

    @Test
    @DisplayName("sk_test_ → entorno INTEGRACION sin requerir confirmación")
    void integracion_sinConfirmacion_procede() {
        when(planCatalogoRepository.findAllByActivoTrue()).thenReturn(List.of());

        ResponseEntity<SetupPlanesResultDTO> resp = controller.setupPlanes(null);

        assertThat(resp.getBody()).isNotNull();
        assertThat(resp.getBody().getEntorno()).isEqualTo("INTEGRACION");
    }

    // ── 10 precios únicos → 10 planes Culqi ─────────────────────────────────

    @Test
    @DisplayName("22 filas con 10 precios únicos → 10 llamadas a crearPlan y 22 filas actualizadas")
    void veintidosFilas_diezPreciosUnicos_creanDiezPlanes() {
        List<PlanCatalogo> catalogo = catalogoCompleto();
        when(planCatalogoRepository.findAllByActivoTrue()).thenReturn(catalogo);
        when(culqiService.crearPlan(anyString(), anyLong()))
                .thenAnswer(inv -> "plan_culqi_" + inv.getArgument(1));

        ResponseEntity<SetupPlanesResultDTO> resp = controller.setupPlanes(null);
        SetupPlanesResultDTO r = resp.getBody();

        assertThat(r.getTotalPlanesCreados()).isEqualTo(10);
        assertThat(r.getTotalPlanesOmitidos()).isEqualTo(0);
        assertThat(r.getTotalErrores()).isEqualTo(0);
        assertThat(r.getTotalCodigosAsignados()).isEqualTo(22);

        verify(culqiService, times(10)).crearPlan(anyString(), anyLong());
        // Cada una de las 22 filas debe ser guardada
        verify(planCatalogoRepository, times(22)).save(any(PlanCatalogo.class));
    }

    @Test
    @DisplayName("El monto enviado a Culqi es precio × 100 en enteros (S/89 → 8900)")
    void montoEnviadoACulqi_esPrecioEnCentavos() {
        PlanCatalogo botica = planCatalogo("BOTICA_BASICO", new BigDecimal("89.00"), null);
        when(planCatalogoRepository.findAllByActivoTrue()).thenReturn(List.of(botica));
        when(culqiService.crearPlan(anyString(), anyLong())).thenReturn("plan_test_id");

        controller.setupPlanes(null);

        ArgumentCaptor<Long> montoCaptor = ArgumentCaptor.forClass(Long.class);
        verify(culqiService).crearPlan(anyString(), montoCaptor.capture());
        assertThat(montoCaptor.getValue()).isEqualTo(8900L);
    }

    @Test
    @DisplayName("Veterinaria S/109 → monto 10900 enviado a Culqi")
    void veterinariaBasico_monto10900() {
        PlanCatalogo vet = planCatalogo("VETERINARIA_BASICO", new BigDecimal("109.00"), null);
        when(planCatalogoRepository.findAllByActivoTrue()).thenReturn(List.of(vet));
        when(culqiService.crearPlan(anyString(), anyLong())).thenReturn("plan_vet_id");

        controller.setupPlanes(null);

        ArgumentCaptor<Long> montoCaptor = ArgumentCaptor.forClass(Long.class);
        verify(culqiService).crearPlan(anyString(), montoCaptor.capture());
        assertThat(montoCaptor.getValue()).isEqualTo(10900L);
    }

    // ── Idempotencia ─────────────────────────────────────────────────────────

    @Test
    @DisplayName("Grupo de S/89 ya tiene culqiPlanId en una fila → reutiliza, no llama a Culqi")
    void grupoConIdExistente_reutilizaSinLlamarACulqi() {
        PlanCatalogo boticaConId = planCatalogo("BOTICA_BASICO",    new BigDecimal("89.00"), "plan_89_existente");
        PlanCatalogo farmaciasSinId = planCatalogo("FARMACIA_BASICO", new BigDecimal("89.00"), null);
        when(planCatalogoRepository.findAllByActivoTrue()).thenReturn(List.of(boticaConId, farmaciasSinId));

        ResponseEntity<SetupPlanesResultDTO> resp = controller.setupPlanes(null);
        SetupPlanesResultDTO r = resp.getBody();

        assertThat(r.getTotalPlanesCreados()).isEqualTo(0);
        assertThat(r.getTotalPlanesOmitidos()).isEqualTo(1);
        assertThat(r.getPlanesOmitidos().get(0).getCulqiPlanIdExistente()).isEqualTo("plan_89_existente");

        verifyNoInteractions(culqiService);
        // farmaciasSinId debe recibir el ID propagado
        verify(planCatalogoRepository).save(farmaciasSinId);
        assertThat(farmaciasSinId.getCulqiPlanId()).isEqualTo("plan_89_existente");
    }

    @Test
    @DisplayName("Todas las filas ya tienen culqiPlanId → 0 llamadas a Culqi, todo omitido")
    void todasConfiguradas_ningunaCulqi() {
        PlanCatalogo botica = planCatalogo("BOTICA_BASICO",   new BigDecimal("89.00"), "plan_89");
        PlanCatalogo vet    = planCatalogo("VETERINARIA_BASICO", new BigDecimal("109.00"), "plan_109");
        when(planCatalogoRepository.findAllByActivoTrue()).thenReturn(List.of(botica, vet));

        ResponseEntity<SetupPlanesResultDTO> resp = controller.setupPlanes(null);

        assertThat(resp.getBody().getTotalPlanesCreados()).isEqualTo(0);
        assertThat(resp.getBody().getTotalPlanesOmitidos()).isEqualTo(2);
        verifyNoInteractions(culqiService);
    }

    // ── Errores parciales ────────────────────────────────────────────────────

    @Test
    @DisplayName("Error en un grupo no detiene la creación de los demás")
    void errorParcial_continuaConOtrosGrupos() {
        PlanCatalogo bodega = planCatalogo("TIENDA_BODEGA_BASICO", new BigDecimal("79.00"), null);
        PlanCatalogo botica = planCatalogo("BOTICA_BASICO",        new BigDecimal("89.00"), null);
        when(planCatalogoRepository.findAllByActivoTrue()).thenReturn(List.of(bodega, botica));

        when(culqiService.crearPlan(contains("79"), anyLong()))
                .thenThrow(new BadRequestException("Error Culqi simulado para S/79"));
        when(culqiService.crearPlan(contains("89"), anyLong())).thenReturn("plan_89_ok");

        ResponseEntity<SetupPlanesResultDTO> resp = controller.setupPlanes(null);
        SetupPlanesResultDTO r = resp.getBody();

        assertThat(r.getTotalPlanesCreados()).isEqualTo(1);
        assertThat(r.getTotalErrores()).isEqualTo(1);
        assertThat(r.getErrores().get(0).getPrecioMensual()).isEqualByComparingTo("79.00");
        assertThat(r.getPlanesCreados().get(0).getCulqiPlanId()).isEqualTo("plan_89_ok");
    }

    // ── Helpers ──────────────────────────────────────────────────────────────

    private static PlanCatalogo planCatalogo(String codigo, BigDecimal precio, String culqiPlanId) {
        return PlanCatalogo.builder()
                .codigo(codigo)
                .segmento("TEST")
                .rubroOperativo("TEST")
                .nivel(codigo.endsWith("PRO") ? "PRO" : "BASICO")
                .precioMensual(precio)
                .maxUsuarios(5).maxSucursales(1).maxProductos(2000)
                .activo(true)
                .culqiPlanId(culqiPlanId)
                .build();
    }

    /**
     * Catálogo completo con los 22 planes y 10 precios únicos del seed V110.
     */
    private static List<PlanCatalogo> catalogoCompleto() {
        return List.of(
            planCatalogo("BOTICA_BASICO",            new BigDecimal("89.00"),  null),
            planCatalogo("BOTICA_PRO",               new BigDecimal("169.00"), null),
            planCatalogo("FARMACIA_BASICO",          new BigDecimal("89.00"),  null),
            planCatalogo("FARMACIA_PRO",             new BigDecimal("169.00"), null),
            planCatalogo("VETERINARIA_BASICO",       new BigDecimal("109.00"), null),
            planCatalogo("VETERINARIA_PRO",          new BigDecimal("189.00"), null),
            planCatalogo("TIENDA_ROPA_BASICO",       new BigDecimal("89.00"),  null),
            planCatalogo("TIENDA_ROPA_PRO",          new BigDecimal("169.00"), null),
            planCatalogo("TIENDA_BODEGA_BASICO",     new BigDecimal("79.00"),  null),
            planCatalogo("TIENDA_BODEGA_PRO",        new BigDecimal("149.00"), null),
            planCatalogo("FERRETERIA_BASICO",        new BigDecimal("99.00"),  null),
            planCatalogo("FERRETERIA_PRO",           new BigDecimal("179.00"), null),
            planCatalogo("MINIMARKET_BASICO",        new BigDecimal("99.00"),  null),
            planCatalogo("MINIMARKET_PRO",           new BigDecimal("179.00"), null),
            planCatalogo("ALMACEN_BASICO",           new BigDecimal("89.00"),  null),
            planCatalogo("ALMACEN_PRO",              new BigDecimal("169.00"), null),
            planCatalogo("RESTAURANTE_BASICO",       new BigDecimal("119.00"), null),
            planCatalogo("RESTAURANTE_PRO",          new BigDecimal("199.00"), null),
            planCatalogo("EMPRESA_SERVICIOS_BASICO", new BigDecimal("99.00"),  null),
            planCatalogo("EMPRESA_SERVICIOS_PRO",    new BigDecimal("179.00"), null),
            planCatalogo("OTRO_BASICO",              new BigDecimal("89.00"),  null),
            planCatalogo("OTRO_PRO",                 new BigDecimal("169.00"), null)
        );
    }
}
