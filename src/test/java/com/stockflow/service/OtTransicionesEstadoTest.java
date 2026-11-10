package com.stockflow.service;

import com.stockflow.dto.ActualizarEstadoOtRequestDTO;
import com.stockflow.dto.OrdenTrabajoDTO;
import com.stockflow.entity.OrdenTrabajo;
import com.stockflow.exception.BadRequestException;
import com.stockflow.mapper.OrdenTrabajoMapper;
import com.stockflow.repository.CajaRepository;
import com.stockflow.repository.ClienteRepository;
import com.stockflow.repository.EquipoClienteRepository;
import com.stockflow.repository.OrdenTrabajoRepository;
import com.stockflow.repository.OtConsumoClasificacionRepository;
import com.stockflow.repository.OtConsumoDevolucionRepository;
import com.stockflow.repository.OtConsumoMaterialRepository;
import com.stockflow.repository.OtHistorialEstadoRepository;
import com.stockflow.repository.ProductoRepository;
import com.stockflow.repository.SucursalRepository;
import com.stockflow.repository.UsuarioRepository;
import com.stockflow.repository.UsuarioTenantRepository;
import com.stockflow.service.impl.OrdenTrabajoServiceImpl;
import com.stockflow.util.TenantContext;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.MockedStatic;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;

import java.time.LocalDateTime;
import java.util.Collections;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mockStatic;
import java.util.List;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * Verifica el mapa TRANSICIONES_VALIDAS (A2):
 * transiciones legítimas pasan, ilegítimas lanzan BadRequestException.
 *
 * Decisión de diseño documentada (A1):
 * Cancelar una OT NO revierte automáticamente el stock de consumos. La devolución
 * de materiales es un proceso explícito que el técnico debe ejecutar manualmente
 * antes o después del cambio de estado.
 */
@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class OtTransicionesEstadoTest {

    private static final String TENANT  = "t-test";
    private static final Long   OT_ID   = 1L;
    private static final Long   USER_ID = 99L;

    @Mock private OrdenTrabajoRepository        otRepository;
    @Mock private OtHistorialEstadoRepository   historialRepository;
    @Mock private EquipoClienteRepository       equipoRepository;
    @Mock private ClienteRepository             clienteRepository;
    @Mock private SucursalRepository            sucursalRepository;
    @Mock private UsuarioRepository             usuarioRepository;
    @Mock private OtNumeracionService           otNumeracionService;
    @Mock private UsuarioTenantRepository       usuarioTenantRepository;
    @Mock private OtConsumoMaterialRepository   consumoRepository;
    @Mock private OtConsumoDevolucionRepository devolucionRepository;
    @Mock private OtConsumoClasificacionRepository clasificacionRepository;
    @Mock private ProductoRepository            productoRepository;
    @Mock private OrdenTrabajoMapper            mapper;
    @Mock private TenantService                 tenantService;
    @Mock private VentaService                  ventaService;
    @Mock private CajaRepository                cajaRepository;

    @InjectMocks
    private OrdenTrabajoServiceImpl service;

    private MockedStatic<TenantContext> tenantCtx;

    @BeforeEach
    void setUp() {
        tenantCtx = mockStatic(TenantContext.class);
        tenantCtx.when(TenantContext::getCurrentTenant).thenReturn(TENANT);
        tenantCtx.when(TenantContext::getCurrentUserId).thenReturn(USER_ID);
        SecurityContextHolder.getContext().setAuthentication(
                new UsernamePasswordAuthenticationToken(
                        "admin@test.com", null,
                        List.of(new SimpleGrantedAuthority("PERM_VER_TODAS_OT"))
                )
        );
    }

    @AfterEach
    void tearDown() {
        tenantCtx.close();
        SecurityContextHolder.clearContext();
    }

    // ── Helpers ───────────────────────────────────────────────────────────────

    private OrdenTrabajo otConEstado(String estado) {
        return OrdenTrabajo.builder()
                .id(OT_ID).tenantId(TENANT).estado(estado)
                .numeroOt("OT-2026-00001").createdAt(LocalDateTime.now())
                .build();
    }

    private ActualizarEstadoOtRequestDTO req(String estado) {
        ActualizarEstadoOtRequestDTO r = new ActualizarEstadoOtRequestDTO();
        r.setEstado(estado);
        return r;
    }

    private void stubBase(OrdenTrabajo ot) {
        when(otRepository.findByIdAndTenantId(OT_ID, TENANT)).thenReturn(Optional.of(ot));
        when(otRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));
        when(historialRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));
        when(historialRepository.findByOrdenTrabajo_IdAndTenantIdOrderByCreatedAtAsc(anyLong(), anyString()))
                .thenReturn(Collections.emptyList());
        when(mapper.toDTO(any())).thenReturn(new OrdenTrabajoDTO());
    }

    // ── Transiciones válidas ──────────────────────────────────────────────────

    @ParameterizedTest(name = "TV: {0} → {1}")
    @CsvSource({
            "ABIERTA,EN_PROCESO",
            "ABIERTA,DIAGNOSTICADA",
            "ABIERTA,CANCELADA",
            "EN_PROCESO,ABIERTA",
            "EN_PROCESO,DIAGNOSTICADA",
            "EN_PROCESO,PENDIENTE_REPUESTO",
            "EN_PROCESO,CERRADA_TECN",
            "EN_PROCESO,CANCELADA",
            "DIAGNOSTICADA,EN_PROCESO",
            "DIAGNOSTICADA,PENDIENTE_REPUESTO",
            "DIAGNOSTICADA,CERRADA_TECN",
            "DIAGNOSTICADA,CANCELADA",
            "PENDIENTE_REPUESTO,EN_PROCESO",
            "PENDIENTE_REPUESTO,DIAGNOSTICADA",
            "PENDIENTE_REPUESTO,CANCELADA",
            "CERRADA_TECN,EN_PROCESO",
            "CERRADA_TECN,CANCELADA",
    })
    void transicionValida_noLanzaExcepcion(String desde, String hasta) {
        OrdenTrabajo ot = otConEstado(desde);
        stubBase(ot);

        OrdenTrabajoDTO result = service.actualizarEstado(OT_ID, req(hasta));

        assertThat(result).isNotNull();
        assertThat(result.getHistorial()).isNotNull();
    }

    // ── Transiciones inválidas desde estados no-terminal ─────────────────────
    // Estas pasan el guard pre-OT (destino != FACTURADA/ENTREGADA) y se rechazan
    // por el mapa TRANSICIONES_VALIDAS tras cargar la OT.

    @ParameterizedTest(name = "TI-mapa: {0} → {1}")
    @CsvSource({
            "ABIERTA,CERRADA_TECN",
            "ABIERTA,PENDIENTE_REPUESTO",
            "DIAGNOSTICADA,ABIERTA",
            "PENDIENTE_REPUESTO,ABIERTA",
            "PENDIENTE_REPUESTO,CERRADA_TECN",
            "CERRADA_TECN,ABIERTA",
            "CERRADA_TECN,DIAGNOSTICADA",
            "CERRADA_TECN,PENDIENTE_REPUESTO",
            "FACTURADA,EN_PROCESO",
            "FACTURADA,CANCELADA",
            "ENTREGADA,EN_PROCESO",
            "ENTREGADA,CANCELADA",
            "CANCELADA,EN_PROCESO",
            "CANCELADA,ABIERTA",
    })
    void transicionInvalidaPorMapa_lanzaBadRequest(String desde, String hasta) {
        OrdenTrabajo ot = otConEstado(desde);
        stubBase(ot);

        assertThatThrownBy(() -> service.actualizarEstado(OT_ID, req(hasta)))
                .isInstanceOf(BadRequestException.class);
    }

    // ── Destinos FACTURADA/ENTREGADA están bloqueados siempre ────────────────
    // Guard pre-OT: lanza BadRequestException antes de cargar la OT.

    @ParameterizedTest(name = "TI-guard: {0} → {1}")
    @CsvSource({
            "ABIERTA,FACTURADA",
            "ABIERTA,ENTREGADA",
            "EN_PROCESO,FACTURADA",
            "EN_PROCESO,ENTREGADA",
            "DIAGNOSTICADA,FACTURADA",
            "DIAGNOSTICADA,ENTREGADA",
    })
    void transicionAEstadoReservado_lanzaBadRequest(String desde, String hasta) {
        // No se necesita stub del repositorio: el guard se dispara antes de cargar la OT
        assertThatThrownBy(() -> service.actualizarEstado(OT_ID, req(hasta)))
                .isInstanceOf(BadRequestException.class)
                .hasMessageContaining(hasta);
    }

    // ── Comportamiento A1: cancelar OT NO revierte stock ─────────────────────

    @Test
    void A1_cancelarOT_noInvocaSaveDeProducto() {
        // La devolución de consumos es un proceso explícito separado de cambiar estado.
        // Cancelar una OT no debe alterar el stock de forma automática.
        OrdenTrabajo ot = otConEstado("EN_PROCESO");
        stubBase(ot);

        OrdenTrabajoDTO result = service.actualizarEstado(OT_ID, req("CANCELADA"));

        assertThat(result.getHistorial()).isNotNull();
        // productoRepository.save() nunca debe llamarse desde actualizarEstado
        verify(productoRepository, never()).save(any());
    }
}
