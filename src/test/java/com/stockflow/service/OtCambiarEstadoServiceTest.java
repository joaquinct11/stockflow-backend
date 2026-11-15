package com.stockflow.service;

import com.stockflow.dto.ActualizarEstadoOtRequestDTO;
import com.stockflow.dto.OrdenTrabajoDTO;
import com.stockflow.entity.OrdenTrabajo;
import com.stockflow.entity.OtHistorialEstado;
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
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.MockedStatic;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.LocalDateTime;
import java.util.Collections;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mockStatic;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * Verifica que actualizarEstado siempre devuelve el historial populado.
 * Bug: retornaba mapper.toDTO(ot) sin historial → pantalla en blanco en frontend.
 */
@ExtendWith(MockitoExtension.class)
class OtCambiarEstadoServiceTest {

    private static final String TENANT  = "tenant-test";
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

    private MockedStatic<TenantContext> tenantContextMock;

    @BeforeEach
    void setUp() {
        tenantContextMock = mockStatic(TenantContext.class);
        tenantContextMock.when(TenantContext::getCurrentTenant).thenReturn(TENANT);
        tenantContextMock.when(TenantContext::getCurrentUserId).thenReturn(USER_ID);
        SecurityContextHolder.getContext().setAuthentication(
                new UsernamePasswordAuthenticationToken(
                        "admin@test.com", null,
                        List.of(new SimpleGrantedAuthority("PERM_VER_TODAS_OT"))
                )
        );
    }

    @AfterEach
    void tearDown() {
        tenantContextMock.close();
        SecurityContextHolder.clearContext();
    }

    // ── Helpers ───────────────────────────────────────────────────────────────

    private OrdenTrabajo otAbierta() {
        return OrdenTrabajo.builder()
                .id(OT_ID)
                .tenantId(TENANT)
                .estado("ABIERTA")
                .numeroOt("OT-2026-00001")
                .createdAt(LocalDateTime.now())
                .build();
    }

    private ActualizarEstadoOtRequestDTO req(String estado, String obs) {
        ActualizarEstadoOtRequestDTO r = new ActualizarEstadoOtRequestDTO();
        r.setEstado(estado);
        r.setObservacion(obs);
        return r;
    }

    private void stubBase(OrdenTrabajo ot) {
        when(otRepository.findByIdAndTenantId(OT_ID, TENANT)).thenReturn(Optional.of(ot));
        when(otRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));
        when(historialRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));
        when(historialRepository
                .findByOrdenTrabajo_IdAndTenantIdOrderByCreatedAtAsc(anyLong(), anyString()))
                .thenReturn(Collections.emptyList());
        when(mapper.toDTO(any())).thenReturn(new OrdenTrabajoDTO());
    }

    // ── Tests ─────────────────────────────────────────────────────────────────

    @Test
    void CE1_cambioAbertaAEnProceso_devuelveHistorialNoNull() {
        OrdenTrabajo ot = otAbierta();
        stubBase(ot);

        OrdenTrabajoDTO result = service.actualizarEstado(OT_ID, req("EN_PROCESO", null));

        // El historial debe estar inicializado (no null) para evitar NPE en el frontend
        assertThat(result.getHistorial()).isNotNull();
    }

    @Test
    void CE2_cambioEnProceso_registraHistorial() {
        OrdenTrabajo ot = otAbierta();
        stubBase(ot);

        service.actualizarEstado(OT_ID, req("EN_PROCESO", "Iniciando trabajo"));

        ArgumentCaptor<OtHistorialEstado> cap = ArgumentCaptor.forClass(OtHistorialEstado.class);
        verify(historialRepository).save(cap.capture());
        assertThat(cap.getValue().getEstadoAnterior()).isEqualTo("ABIERTA");
        assertThat(cap.getValue().getEstadoNuevo()).isEqualTo("EN_PROCESO");
    }

    @Test
    void CE3_cambioEnProcesoADiagnosticada() {
        OrdenTrabajo ot = otAbierta();
        ot.setEstado("EN_PROCESO");
        stubBase(ot);

        OrdenTrabajoDTO result = service.actualizarEstado(OT_ID, req("DIAGNOSTICADA", null));

        assertThat(result.getHistorial()).isNotNull();
    }

    @Test
    void CE4_cambioEnProcesoAPendienteRepuesto() {
        OrdenTrabajo ot = otAbierta();
        ot.setEstado("EN_PROCESO");
        stubBase(ot);

        OrdenTrabajoDTO result = service.actualizarEstado(OT_ID, req("PENDIENTE_REPUESTO", null));

        assertThat(result.getHistorial()).isNotNull();
    }

    @Test
    void CE5_mismoEstadoDevuelveHistorialNoNull() {
        // Early-return path: estadoAnterior == nuevoEstado → también debe tener historial
        OrdenTrabajo ot = otAbierta();
        when(otRepository.findByIdAndTenantId(OT_ID, TENANT)).thenReturn(Optional.of(ot));
        when(historialRepository
                .findByOrdenTrabajo_IdAndTenantIdOrderByCreatedAtAsc(anyLong(), anyString()))
                .thenReturn(Collections.emptyList());
        when(mapper.toDTO(any())).thenReturn(new OrdenTrabajoDTO());

        OrdenTrabajoDTO result = service.actualizarEstado(OT_ID, req("ABIERTA", null));

        assertThat(result.getHistorial()).isNotNull();
    }

    @Test
    void CE6_rechazarTransicionAFacturada() {
        assertThatThrownBy(() ->
                service.actualizarEstado(OT_ID, req("FACTURADA", null)))
                .isInstanceOf(BadRequestException.class)
                .hasMessageContaining("FACTURADA");
    }

    @Test
    void CE7_rechazarTransicionAEntregada() {
        assertThatThrownBy(() ->
                service.actualizarEstado(OT_ID, req("ENTREGADA", null)))
                .isInstanceOf(BadRequestException.class)
                .hasMessageContaining("ENTREGADA");
    }

    @Test
    void CE8_rechazarEstadoInvalido() {
        assertThatThrownBy(() ->
                service.actualizarEstado(OT_ID, req("ESTADO_INEXISTENTE", null)))
                .isInstanceOf(BadRequestException.class);
    }

    @Test
    void CE9_historialContieneEntradaDespuesdeCambio() {
        OrdenTrabajo ot = otAbierta();
        when(otRepository.findByIdAndTenantId(OT_ID, TENANT)).thenReturn(Optional.of(ot));
        when(otRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));
        when(historialRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));

        OrdenTrabajoDTO.HistorialEstadoDTO entrada = new OrdenTrabajoDTO.HistorialEstadoDTO();
        List<OrdenTrabajoDTO.HistorialEstadoDTO> historialSimulado = List.of(entrada);
        when(historialRepository
                .findByOrdenTrabajo_IdAndTenantIdOrderByCreatedAtAsc(anyLong(), anyString()))
                .thenReturn(Collections.emptyList());
        when(mapper.toDTO(any())).thenReturn(new OrdenTrabajoDTO());
        when(mapper.toHistorialDTO(any())).thenReturn(entrada);

        // Reemplazamos el stub del repositorio para devolver una entrada
        when(historialRepository
                .findByOrdenTrabajo_IdAndTenantIdOrderByCreatedAtAsc(OT_ID, TENANT))
                .thenReturn(List.of(new com.stockflow.entity.OtHistorialEstado()));

        OrdenTrabajoDTO result = service.actualizarEstado(OT_ID, req("EN_PROCESO", null));

        assertThat(result.getHistorial()).isNotNull();
        assertThat(result.getHistorial()).hasSize(1);
    }

    @Test
    void CE10_cambioConCancelacion() {
        OrdenTrabajo ot = otAbierta();
        stubBase(ot);

        OrdenTrabajoDTO result = service.actualizarEstado(OT_ID, req("CANCELADA", "Cliente canceló"));

        assertThat(result.getHistorial()).isNotNull();
        ArgumentCaptor<OtHistorialEstado> cap = ArgumentCaptor.forClass(OtHistorialEstado.class);
        verify(historialRepository).save(cap.capture());
        assertThat(cap.getValue().getObservacion()).isEqualTo("Cliente canceló");
    }
}
