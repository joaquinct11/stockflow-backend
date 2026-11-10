package com.stockflow.service;

import com.stockflow.dto.EntregarOtRequestDTO;
import com.stockflow.dto.OrdenTrabajoDTO;
import com.stockflow.entity.Cliente;
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

import java.math.BigDecimal;
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
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class OtEntregaServiceTest {

    private static final String TENANT  = "tenant-test";
    private static final Long   OT_ID   = 1L;
    private static final Long   USER_ID = 99L;
    private static final Long   VENTA_ID = 500L;

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
    }

    @AfterEach
    void tearDown() {
        tenantContextMock.close();
    }

    // ── Helpers ───────────────────────────────────────────────────────────────

    private OrdenTrabajo otFacturada() {
        return OrdenTrabajo.builder()
                .id(OT_ID)
                .tenantId(TENANT)
                .estado("FACTURADA")
                .ventaId(VENTA_ID)
                .totalFacturado(new BigDecimal("350.00"))
                .numeroOt("OT-2026-00001")
                .createdAt(LocalDateTime.now())
                .build();
    }

    private EntregarOtRequestDTO req(String recibidoPor, String doc, Boolean conformidad, String obs) {
        EntregarOtRequestDTO r = new EntregarOtRequestDTO();
        r.setRecibidoPor(recibidoPor);
        r.setDocumentoReceptor(doc);
        r.setConformidadCliente(conformidad);
        r.setObservaciones(obs);
        return r;
    }

    private void stubEntregaBase(OrdenTrabajo ot) {
        when(otRepository.findByIdAndTenantIdForUpdate(OT_ID, TENANT)).thenReturn(Optional.of(ot));
        when(otRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));
        when(historialRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));
        when(historialRepository
                .findByOrdenTrabajo_IdAndTenantIdOrderByCreatedAtAsc(anyLong(), anyString()))
                .thenReturn(Collections.emptyList());
        when(mapper.toDTO(any())).thenReturn(new OrdenTrabajoDTO());
    }

    // ── Tests ─────────────────────────────────────────────────────────────────

    @Test
    void ET1_entregaCorrectaDesdeFacturada() {
        OrdenTrabajo ot = otFacturada();
        stubEntregaBase(ot);

        service.entregarOt(OT_ID, req("Juan Pérez", "12345678", true, null));

        ArgumentCaptor<OrdenTrabajo> cap = ArgumentCaptor.forClass(OrdenTrabajo.class);
        verify(otRepository).save(cap.capture());
        OrdenTrabajo saved = cap.getValue();
        assertThat(saved.getEstado()).isEqualTo("ENTREGADA");
        assertThat(saved.getRecibidoPor()).isEqualTo("Juan Pérez");
        assertThat(saved.getConformidadCliente()).isTrue();
        assertThat(saved.getFechaEntrega()).isNotNull();
        assertThat(saved.getEntregadoPor()).isEqualTo(USER_ID);
    }

    @Test
    void ET2_rechazarEntregaDesdeCerradaTecn() {
        OrdenTrabajo ot = otFacturada();
        ot.setEstado("CERRADA_TECN");
        when(otRepository.findByIdAndTenantIdForUpdate(OT_ID, TENANT)).thenReturn(Optional.of(ot));

        assertThatThrownBy(() ->
                service.entregarOt(OT_ID, req("Juan", null, true, null)))
                .isInstanceOf(BadRequestException.class)
                .hasMessageContaining("FACTURADA");
    }

    @Test
    void ET3_rechazarEntregaDesdeAbierta() {
        OrdenTrabajo ot = otFacturada();
        ot.setEstado("ABIERTA");
        when(otRepository.findByIdAndTenantIdForUpdate(OT_ID, TENANT)).thenReturn(Optional.of(ot));

        assertThatThrownBy(() ->
                service.entregarOt(OT_ID, req("Juan", null, true, null)))
                .isInstanceOf(BadRequestException.class)
                .hasMessageContaining("FACTURADA");
    }

    @Test
    void ET4_rechazarEntregaDesdeEntregada() {
        OrdenTrabajo ot = otFacturada();
        ot.setEstado("ENTREGADA");
        when(otRepository.findByIdAndTenantIdForUpdate(OT_ID, TENANT)).thenReturn(Optional.of(ot));

        assertThatThrownBy(() ->
                service.entregarOt(OT_ID, req("Juan", null, true, null)))
                .isInstanceOf(BadRequestException.class)
                .hasMessageContaining("FACTURADA");
    }

    @Test
    void ET5_rechazarSegundaEntrega() {
        OrdenTrabajo ot = otFacturada();
        ot.setFechaEntrega(LocalDateTime.now().minusDays(1));
        ot.setRecibidoPor("Receptor anterior");
        when(otRepository.findByIdAndTenantIdForUpdate(OT_ID, TENANT)).thenReturn(Optional.of(ot));

        assertThatThrownBy(() ->
                service.entregarOt(OT_ID, req("Otro receptor", null, true, null)))
                .isInstanceOf(BadRequestException.class)
                .hasMessageContaining("ya fue entregada");
    }

    @Test
    void ET6_rechazarNombreReceptorVacio() {
        // La validación ocurre antes del lock pessimista → no se stubbea el repo
        assertThatThrownBy(() ->
                service.entregarOt(OT_ID, req("   ", null, true, null)))
                .isInstanceOf(BadRequestException.class)
                .hasMessageContaining("obligatorio");
    }

    @Test
    void ET7_documentoOpcionalPermitido() {
        OrdenTrabajo ot = otFacturada();
        stubEntregaBase(ot);

        // Sin documento
        service.entregarOt(OT_ID, req("María García", null, true, null));

        ArgumentCaptor<OrdenTrabajo> cap = ArgumentCaptor.forClass(OrdenTrabajo.class);
        verify(otRepository).save(cap.capture());
        assertThat(cap.getValue().getDocumentoReceptor()).isNull();
    }

    @Test
    void ET8_guardarConformidadDelCliente() {
        OrdenTrabajo ot = otFacturada();
        stubEntregaBase(ot);

        service.entregarOt(OT_ID, req("Carlos López", null, false, null));

        ArgumentCaptor<OrdenTrabajo> cap = ArgumentCaptor.forClass(OrdenTrabajo.class);
        verify(otRepository).save(cap.capture());
        assertThat(cap.getValue().getConformidadCliente()).isFalse();
    }

    @Test
    void ET9_registrarUsuarioAutenticado() {
        OrdenTrabajo ot = otFacturada();
        stubEntregaBase(ot);

        service.entregarOt(OT_ID, req("Ana Torres", null, true, null));

        ArgumentCaptor<OrdenTrabajo> cap = ArgumentCaptor.forClass(OrdenTrabajo.class);
        verify(otRepository).save(cap.capture());
        assertThat(cap.getValue().getEntregadoPor()).isEqualTo(USER_ID);
    }

    @Test
    void ET10_registrarHistorialFacturadaAEntregada() {
        OrdenTrabajo ot = otFacturada();
        stubEntregaBase(ot);

        service.entregarOt(OT_ID, req("Pedro Ruiz", null, true, "Sin observaciones"));

        ArgumentCaptor<OtHistorialEstado> cap = ArgumentCaptor.forClass(OtHistorialEstado.class);
        verify(historialRepository).save(cap.capture());
        OtHistorialEstado h = cap.getValue();
        assertThat(h.getEstadoAnterior()).isEqualTo("FACTURADA");
        assertThat(h.getEstadoNuevo()).isEqualTo("ENTREGADA");
        assertThat(h.getObservacion()).contains("Pedro Ruiz");
    }

    @Test
    void ET11_rechazarOtDeOtroTenant() {
        when(otRepository.findByIdAndTenantIdForUpdate(OT_ID, TENANT)).thenReturn(Optional.empty());

        assertThatThrownBy(() ->
                service.entregarOt(OT_ID, req("Juan", null, true, null)))
                .isInstanceOf(RuntimeException.class);
    }

    @Test
    void ET12_revertirTransaccionSiFallaHistorial() {
        OrdenTrabajo ot = otFacturada();
        when(otRepository.findByIdAndTenantIdForUpdate(OT_ID, TENANT)).thenReturn(Optional.of(ot));
        when(otRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));
        when(historialRepository.save(any())).thenThrow(new RuntimeException("DB error"));

        assertThatThrownBy(() ->
                service.entregarOt(OT_ID, req("Juan", null, true, null)))
                .isInstanceOf(RuntimeException.class)
                .hasMessageContaining("DB error");
        // La anotación @Transactional garantiza el rollback; aquí verificamos que la excepción propaga
    }

    @Test
    void ET13_noLlamarVentaService() {
        OrdenTrabajo ot = otFacturada();
        stubEntregaBase(ot);

        service.entregarOt(OT_ID, req("Juan Pérez", null, true, null));

        verify(ventaService, never()).crearVenta(any());
    }

    @Test
    void ET14_noModificarVentaId() {
        OrdenTrabajo ot = otFacturada();
        stubEntregaBase(ot);

        service.entregarOt(OT_ID, req("Juan", null, true, null));

        ArgumentCaptor<OrdenTrabajo> cap = ArgumentCaptor.forClass(OrdenTrabajo.class);
        verify(otRepository).save(cap.capture());
        assertThat(cap.getValue().getVentaId()).isEqualTo(VENTA_ID);
    }

    @Test
    void ET15_noModificarStock() {
        OrdenTrabajo ot = otFacturada();
        stubEntregaBase(ot);

        service.entregarOt(OT_ID, req("Juan", null, true, null));

        verify(productoRepository, never()).save(any());
        verify(consumoRepository, never()).save(any());
    }
}
