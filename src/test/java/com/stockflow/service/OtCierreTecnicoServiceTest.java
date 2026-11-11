package com.stockflow.service;

import com.stockflow.dto.CierreTecnicoOtRequestDTO;
import com.stockflow.dto.OrdenTrabajoDTO;
import com.stockflow.entity.OrdenTrabajo;
import com.stockflow.entity.OtConsumoMaterial;
import com.stockflow.entity.OtHistorialEstado;
import com.stockflow.entity.Producto;
import com.stockflow.entity.Usuario;
import com.stockflow.exception.BadRequestException;
import com.stockflow.exception.ResourceNotFoundException;
import com.stockflow.mapper.OrdenTrabajoMapper;
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
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class OtCierreTecnicoServiceTest {

    private static final String TENANT   = "tenant-test";
    private static final Long   OT_ID    = 1L;
    private static final Long   USER_ID  = 99L;
    private static final Long   PROD_ID  = 10L;
    private static final Long   CONSUMO_ID = 20L;

    @Mock private OrdenTrabajoRepository          otRepository;
    @Mock private OtHistorialEstadoRepository     historialRepository;
    @Mock private EquipoClienteRepository         equipoRepository;
    @Mock private ClienteRepository               clienteRepository;
    @Mock private SucursalRepository              sucursalRepository;
    @Mock private UsuarioRepository               usuarioRepository;
    @Mock private OtConsumoMaterialRepository     consumoRepository;
    @Mock private OtConsumoDevolucionRepository   devolucionRepository;
    @Mock private OtConsumoClasificacionRepository clasificacionRepository;
    @Mock private ProductoRepository              productoRepository;
    @Mock private UsuarioTenantRepository         usuarioTenantRepository;
    @Mock private OtNumeracionService             otNumeracionService;
    @Mock private OrdenTrabajoMapper              mapper;
    @Mock private TenantService                   tenantService;

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

    // ── Helpers ──────────────────────────────────────────────────────────────

    private OrdenTrabajo otEnProceso() {
        return OrdenTrabajo.builder()
                .id(OT_ID).tenantId(TENANT).estado("EN_PROCESO")
                .numeroOt("OT-2025-0001")
                .createdAt(LocalDateTime.now())
                .build();
    }

    private OtConsumoMaterial consumoFisico(long id, int cantBase) {
        return OtConsumoMaterial.builder()
                .id(id).tenantId(TENANT).productoId(PROD_ID)
                .cantidadBase(cantBase).factorPresentacion(1).cantidadPresentacion(cantBase)
                .createdAt(LocalDateTime.now())
                .build();
    }

    private Producto productoFisico() {
        return Producto.builder().id(PROD_ID).tipo("FISICO").build();
    }

    private Producto productoServicio(long prodId) {
        return Producto.builder().id(prodId).tipo("SERVICIO").build();
    }

    private CierreTecnicoOtRequestDTO reqValido() {
        return CierreTecnicoOtRequestDTO.builder()
                .diagnosticoFinal("Falla en el disco duro")
                .solucionAplicada("Reemplazo del disco duro")
                .build();
    }

    private void stubOtForUpdate(OrdenTrabajo ot) {
        when(otRepository.findByIdAndTenantIdForUpdate(OT_ID, TENANT)).thenReturn(Optional.of(ot));
    }

    private void stubSave(OrdenTrabajo saved) {
        when(otRepository.save(any(OrdenTrabajo.class))).thenReturn(saved);
    }

    private void stubHistorialEmpty() {
        when(historialRepository.findByOrdenTrabajo_IdAndTenantIdOrderByCreatedAtAsc(anyLong(), anyString()))
                .thenReturn(List.of());
    }

    private void stubMapperToDTO(OrdenTrabajo ot) {
        OrdenTrabajoDTO dto = OrdenTrabajoDTO.builder()
                .id(ot.getId()).estado(ot.getEstado()).build();
        when(mapper.toDTO(any(OrdenTrabajo.class))).thenReturn(dto);
    }

    // ── CT-1: Cierre exitoso con todos los consumos regularizados ─────────────

    @Test
    void CT1_cierreExitoso_todosConsumosRegularizados() {
        OrdenTrabajo ot = otEnProceso();
        stubOtForUpdate(ot);

        OtConsumoMaterial consumo = consumoFisico(CONSUMO_ID, 5);
        when(consumoRepository.findByOrdenTrabajo_IdAndTenantIdOrderByCreatedAtAsc(OT_ID, TENANT))
                .thenReturn(List.of(consumo));
        when(productoRepository.findAllById(any())).thenReturn(List.of(productoFisico()));
        // devuelta=3, clasificada=2 → pendiente=0
        when(devolucionRepository.sumCantidadDevueltaGroupByConsumoId(any()))
                .thenReturn(List.<Object[]>of(new Object[]{CONSUMO_ID, 3L}));
        when(clasificacionRepository.sumCantidadClasificadaGroupByConsumoId(any()))
                .thenReturn(List.<Object[]>of(new Object[]{CONSUMO_ID, 2L}));

        OrdenTrabajo saved = OrdenTrabajo.builder().id(OT_ID).tenantId(TENANT)
                .estado("CERRADA_TECN").createdAt(LocalDateTime.now()).build();
        stubSave(saved);
        stubHistorialEmpty();
        stubMapperToDTO(saved);
        when(usuarioRepository.findById(USER_ID)).thenReturn(Optional.empty());

        OrdenTrabajoDTO result = service.cierreTecnico(OT_ID, reqValido());

        assertThat(result.getEstado()).isEqualTo("CERRADA_TECN");
        verify(otRepository).save(any(OrdenTrabajo.class));
        verify(historialRepository).save(any(OtHistorialEstado.class));
    }

    // ── CT-2: Rechazo si consumo físico tiene unidades pendientes ─────────────

    @Test
    void CT2_rechazoCierrePorPendiente() {
        OrdenTrabajo ot = otEnProceso();
        stubOtForUpdate(ot);

        OtConsumoMaterial consumo = consumoFisico(CONSUMO_ID, 5);
        when(consumoRepository.findByOrdenTrabajo_IdAndTenantIdOrderByCreatedAtAsc(OT_ID, TENANT))
                .thenReturn(List.of(consumo));
        when(productoRepository.findAllById(any())).thenReturn(List.of(productoFisico()));
        // devuelta=2, clasificada=1 → pendiente=2
        when(devolucionRepository.sumCantidadDevueltaGroupByConsumoId(any()))
                .thenReturn(List.<Object[]>of(new Object[]{CONSUMO_ID, 2L}));
        when(clasificacionRepository.sumCantidadClasificadaGroupByConsumoId(any()))
                .thenReturn(List.<Object[]>of(new Object[]{CONSUMO_ID, 1L}));

        assertThatThrownBy(() -> service.cierreTecnico(OT_ID, reqValido()))
                .isInstanceOf(BadRequestException.class)
                .hasMessageContaining("materiales sin regularizar");

        verify(otRepository, never()).save(any());
    }

    // ── CT-3: Servicios no requieren clasificación ni devolución ─────────────

    @Test
    void CT3_servicioNoRequiereRegularizacion() {
        OrdenTrabajo ot = otEnProceso();
        stubOtForUpdate(ot);

        Long prodServicioId = 50L;
        OtConsumoMaterial consumoServicio = OtConsumoMaterial.builder()
                .id(CONSUMO_ID).tenantId(TENANT).productoId(prodServicioId)
                .cantidadBase(1).factorPresentacion(1).cantidadPresentacion(1)
                .createdAt(LocalDateTime.now()).build();

        when(consumoRepository.findByOrdenTrabajo_IdAndTenantIdOrderByCreatedAtAsc(OT_ID, TENANT))
                .thenReturn(List.of(consumoServicio));
        when(productoRepository.findAllById(any())).thenReturn(List.of(productoServicio(prodServicioId)));
        when(devolucionRepository.sumCantidadDevueltaGroupByConsumoId(any())).thenReturn(List.of());
        when(clasificacionRepository.sumCantidadClasificadaGroupByConsumoId(any())).thenReturn(List.of());

        OrdenTrabajo saved = OrdenTrabajo.builder().id(OT_ID).tenantId(TENANT)
                .estado("CERRADA_TECN").createdAt(LocalDateTime.now()).build();
        stubSave(saved);
        stubHistorialEmpty();
        stubMapperToDTO(saved);
        when(usuarioRepository.findById(USER_ID)).thenReturn(Optional.empty());

        // Debe pasar sin excepción — el servicio se ignora en la validación
        OrdenTrabajoDTO result = service.cierreTecnico(OT_ID, reqValido());
        assertThat(result.getEstado()).isEqualTo("CERRADA_TECN");
    }

    // ── CT-4: OT inexistente → ResourceNotFoundException ─────────────────────

    @Test
    void CT4_otInexistenteLanzaNotFoundException() {
        when(otRepository.findByIdAndTenantIdForUpdate(OT_ID, TENANT)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> service.cierreTecnico(OT_ID, reqValido()))
                .isInstanceOf(ResourceNotFoundException.class)
                .hasMessageContaining("Orden de trabajo no encontrada");
    }

    // ── CT-5: OT de otro tenant → ResourceNotFoundException ──────────────────

    @Test
    void CT5_otOtroTenantNoEncontrada() {
        // TenantContext retorna TENANT, el repository busca por tenantId=TENANT.
        // Si la OT pertenece a otro tenant, findByIdAndTenantIdForUpdate no la devuelve.
        when(otRepository.findByIdAndTenantIdForUpdate(OT_ID, TENANT)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> service.cierreTecnico(OT_ID, reqValido()))
                .isInstanceOf(ResourceNotFoundException.class);
    }

    // ── CT-6: Estado no permitido para cierre técnico ─────────────────────────

    @Test
    void CT6_estadoNoPermitido_ABIERTA() {
        OrdenTrabajo ot = OrdenTrabajo.builder()
                .id(OT_ID).tenantId(TENANT).estado("ABIERTA")
                .createdAt(LocalDateTime.now()).build();
        stubOtForUpdate(ot);

        assertThatThrownBy(() -> service.cierreTecnico(OT_ID, reqValido()))
                .isInstanceOf(BadRequestException.class)
                .hasMessageContaining("No se puede cerrar técnicamente");

        verify(otRepository, never()).save(any());
    }

    @Test
    void CT6b_estadoNoPermitido_CERRADA_TECN() {
        OrdenTrabajo ot = OrdenTrabajo.builder()
                .id(OT_ID).tenantId(TENANT).estado("CERRADA_TECN")
                .createdAt(LocalDateTime.now()).build();
        stubOtForUpdate(ot);

        assertThatThrownBy(() -> service.cierreTecnico(OT_ID, reqValido()))
                .isInstanceOf(BadRequestException.class)
                .hasMessageContaining("EN_PROCESO, DIAGNOSTICADA, PENDIENTE_REPUESTO");

        verify(otRepository, never()).save(any());
    }

    // ── CT-7: Persistencia de diagnóstico y solución ──────────────────────────

    @Test
    void CT7_persisteDiagnosticoYSolucion() {
        OrdenTrabajo ot = otEnProceso();
        stubOtForUpdate(ot);

        when(consumoRepository.findByOrdenTrabajo_IdAndTenantIdOrderByCreatedAtAsc(OT_ID, TENANT))
                .thenReturn(List.of());

        ArgumentCaptor<OrdenTrabajo> captor = ArgumentCaptor.forClass(OrdenTrabajo.class);
        OrdenTrabajo saved = OrdenTrabajo.builder().id(OT_ID).tenantId(TENANT)
                .estado("CERRADA_TECN").createdAt(LocalDateTime.now()).build();
        when(otRepository.save(captor.capture())).thenReturn(saved);
        stubHistorialEmpty();
        stubMapperToDTO(saved);
        when(usuarioRepository.findById(USER_ID)).thenReturn(Optional.empty());

        CierreTecnicoOtRequestDTO req = CierreTecnicoOtRequestDTO.builder()
                .diagnosticoFinal("Falla RAM")
                .solucionAplicada("Reemplazo módulo RAM")
                .observaciones("Revisado el 2025-11-06")
                .build();
        service.cierreTecnico(OT_ID, req);

        OrdenTrabajo captured = captor.getValue();
        assertThat(captured.getDiagnostico()).isEqualTo("Falla RAM");
        assertThat(captured.getTrabajoRealizado()).isEqualTo("Reemplazo módulo RAM");
        assertThat(captured.getObservaciones()).isEqualTo("Revisado el 2025-11-06");
        assertThat(captured.getEstado()).isEqualTo("CERRADA_TECN");
        assertThat(captured.getFechaCierreTecnico()).isNotNull();
    }

    // ── CT-8: Registro en historial ───────────────────────────────────────────

    @Test
    void CT8_registraHistorialConEstadoAnteriorYNuevo() {
        OrdenTrabajo ot = OrdenTrabajo.builder()
                .id(OT_ID).tenantId(TENANT).estado("DIAGNOSTICADA")
                .createdAt(LocalDateTime.now()).build();
        stubOtForUpdate(ot);

        when(consumoRepository.findByOrdenTrabajo_IdAndTenantIdOrderByCreatedAtAsc(OT_ID, TENANT))
                .thenReturn(List.of());

        OrdenTrabajo saved = OrdenTrabajo.builder().id(OT_ID).tenantId(TENANT)
                .estado("CERRADA_TECN").createdAt(LocalDateTime.now()).build();
        stubSave(saved);
        stubHistorialEmpty();
        stubMapperToDTO(saved);
        when(usuarioRepository.findById(USER_ID)).thenReturn(Optional.empty());

        ArgumentCaptor<OtHistorialEstado> histCaptor = ArgumentCaptor.forClass(OtHistorialEstado.class);

        service.cierreTecnico(OT_ID, reqValido());

        verify(historialRepository).save(histCaptor.capture());
        OtHistorialEstado h = histCaptor.getValue();
        assertThat(h.getEstadoAnterior()).isEqualTo("DIAGNOSTICADA");
        assertThat(h.getEstadoNuevo()).isEqualTo("CERRADA_TECN");
        assertThat(h.getUsuarioId()).isEqualTo(USER_ID);
        assertThat(h.getObservacion()).contains("Cierre técnico");
    }

    // ── CT-9: Rechazo cuando faltan datos obligatorios ────────────────────────

    @Test
    void CT9_rechazaSinDiagnosticoFinal() {
        CierreTecnicoOtRequestDTO req = CierreTecnicoOtRequestDTO.builder()
                .diagnosticoFinal("  ")   // blank
                .solucionAplicada("OK")
                .build();

        assertThatThrownBy(() -> service.cierreTecnico(OT_ID, req))
                .isInstanceOf(BadRequestException.class)
                .hasMessageContaining("diagnóstico final es obligatorio");
    }

    @Test
    void CT9b_rechazaSinSolucionAplicada() {
        CierreTecnicoOtRequestDTO req = CierreTecnicoOtRequestDTO.builder()
                .diagnosticoFinal("Falla de hardware")
                .solucionAplicada("")   // blank
                .build();

        assertThatThrownBy(() -> service.cierreTecnico(OT_ID, req))
                .isInstanceOf(BadRequestException.class)
                .hasMessageContaining("solución aplicada es obligatoria");
    }

    // ── CT-10: Sin consumos — cierre directo ──────────────────────────────────

    @Test
    void CT10_otSinConsumos_cierraCorrectamente() {
        OrdenTrabajo ot = OrdenTrabajo.builder()
                .id(OT_ID).tenantId(TENANT).estado("PENDIENTE_REPUESTO")
                .createdAt(LocalDateTime.now()).build();
        stubOtForUpdate(ot);

        when(consumoRepository.findByOrdenTrabajo_IdAndTenantIdOrderByCreatedAtAsc(OT_ID, TENANT))
                .thenReturn(List.of());

        OrdenTrabajo saved = OrdenTrabajo.builder().id(OT_ID).tenantId(TENANT)
                .estado("CERRADA_TECN").createdAt(LocalDateTime.now()).build();
        stubSave(saved);
        stubHistorialEmpty();
        stubMapperToDTO(saved);
        when(usuarioRepository.findById(USER_ID)).thenReturn(Optional.empty());

        OrdenTrabajoDTO result = service.cierreTecnico(OT_ID, reqValido());
        assertThat(result.getEstado()).isEqualTo("CERRADA_TECN");
    }
}
