package com.stockflow.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.stockflow.dto.gre.*;
import com.stockflow.entity.Producto;
import com.stockflow.entity.Sucursal;
import com.stockflow.entity.Tenant;
import com.stockflow.entity.gre.*;
import com.stockflow.exception.BadRequestException;
import com.stockflow.repository.*;
import com.stockflow.repository.UbigeoRepository;
import com.stockflow.service.impl.GuiaRemisionServiceImpl;
import com.stockflow.service.impl.LucodeGREService;
import com.stockflow.service.impl.GuiaRemisionPdfService;
import com.stockflow.util.TenantContext;
import org.junit.jupiter.api.*;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.*;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;

import java.time.LocalDate;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
@DisplayName("GuiaRemisionService — reglas de negocio")
class GuiaRemisionServiceTest {

    private static final String TENANT_ID = "negocio-test";

    @Mock private GuiaRemisionRepository       guiaRepo;
    @Mock private GuiaRemisionEventoRepository eventoRepo;
    @Mock private SucursalRepository           sucursalRepo;
    @Mock private ProductoRepository           productoRepo;
    @Mock private TenantRepository             tenantRepo;
    @Mock private MovimientoInventarioService      movimientoService;
    @Mock private MovimientoInventarioRepository   movimientoRepo;
    @Mock private VentaRepository                  ventaRepo;
    @Mock private ProductoPresentacionRepository   presentacionRepo;
    @Mock private PlanLimitService                 planLimitService;
    @Mock private LucodeGREService                 lucodeGREService;
    @Mock private GuiaRemisionPdfService           pdfService;
    @Mock private StockLoteService                 stockLoteService;
    @Mock private UbigeoRepository                 ubigeoRepository;
    @Mock private ProductoStockSucursalRepository  stockSucursalRepo;
    @Mock private ProductoVarianteRepository       varianteRepo;
    @Mock private ProductoVarianteStockSucursalRepository varianteSucursalRepo;
    @Mock private com.stockflow.service.impl.GreSerieContadorService serieContadorService;
    @Spy  private ObjectMapper                     objectMapper = new ObjectMapper();

    @InjectMocks private GuiaRemisionServiceImpl service;

    private static final MockedStatic<TenantContext> tenantCtx = Mockito.mockStatic(TenantContext.class);

    @BeforeAll
    static void setUpStatic() {
        tenantCtx.when(TenantContext::getCurrentTenant).thenReturn(TENANT_ID);
        tenantCtx.when(TenantContext::getCurrentUserId).thenReturn(1L);
    }

    @AfterAll
    static void tearDownStatic() {
        tenantCtx.close();
    }

    @BeforeEach
    void setUp() {
        // GRE abierto para todos — puedeEmitirGRE siempre true
        when(planLimitService.puedeEmitirGRE(TENANT_ID)).thenReturn(true);

        Producto producto = Producto.builder().id(10L).tenantId(TENANT_ID).nombre("Producto Test").build();
        when(productoRepo.findById(10L)).thenReturn(java.util.Optional.of(producto));

        Tenant tenant = Tenant.builder()
                .tenantId(TENANT_ID)
                .nombre("Test Negocio")
                .serieGuia("T001")
                .oseToken("tok_test_abc")
                .build();
        when(tenantRepo.findByTenantId(TENANT_ID)).thenReturn(Optional.of(tenant));

        // Stock siempre suficiente por defecto — tests específicos de stock están en GuiaRemisionStockValidationTest
        when(stockLoteService.getStockVigente(anyString(), anyLong(), anyLong())).thenReturn(100);

        // Por defecto todos los ubigeos son válidos; tests específicos de ubigeo sobreescriben esto
        when(ubigeoRepository.existsById(anyString())).thenReturn(true);

        Sucursal sucursalOrigen = Sucursal.builder().id(10L).tenantId(TENANT_ID).nombre("Tienda Origen").build();
        when(sucursalRepo.findByIdAndTenantId(10L, TENANT_ID)).thenReturn(Optional.of(sucursalOrigen));

        when(guiaRepo.findMaxCorrelativo(TENANT_ID, "T001")).thenReturn(0);
        when(guiaRepo.existsByIdempotencyKey(anyString())).thenReturn(false);
        when(guiaRepo.save(any())).thenAnswer(inv -> {
            GuiaRemision g = inv.getArgument(0);
            if (g.getId() == null) {
                try {
                    var field = GuiaRemision.class.getDeclaredField("id");
                    field.setAccessible(true);
                    field.set(g, 1L);
                } catch (Exception ignored) {}
            }
            return g;
        });
        when(guiaRepo.saveAndFlush(any())).thenAnswer(inv -> inv.getArgument(0));
        when(eventoRepo.save(any())).thenAnswer(inv -> inv.getArgument(0));
    }

    // ── GRE-1: Acceso universal — todos los planes y rubros pueden crear GRE ──

    @Test
    @DisplayName("GRE-1: Todos los planes y rubros pueden crear GRE (acceso universal)")
    void planBasico_todosLosRubros_permitido() {
        // puedeEmitirGRE siempre devuelve true (ya configurado en setUp)
        GuiaRemisionResponse resp = service.crearBorrador(buildRequest());
        assertThat(resp.getEstadoTributario()).isEqualTo(EstadoTributarioGRE.BORRADOR);
    }

    // ── GRE-2: Idempotencia — clave duplicada retorna guía existente ──────────

    @Test
    @DisplayName("GRE-2: Idempotency key duplicada retorna guía existente sin crear nueva")
    void idempotencyKey_duplicada_retornaExistente() {
        String iKey = "idem-abc-123";
        GuiaRemision existente = buildGuia(EstadoTributarioGRE.BORRADOR);

        when(guiaRepo.existsByIdempotencyKey(iKey)).thenReturn(true);
        when(guiaRepo.findByIdempotencyKey(iKey)).thenReturn(Optional.of(existente));

        CrearGuiaRemisionRequest req = buildRequest();
        req.setIdempotencyKey(iKey);

        GuiaRemisionResponse resp = service.crearBorrador(req);
        assertThat(resp.getEstadoTributario()).isEqualTo(EstadoTributarioGRE.BORRADOR);
        verify(guiaRepo, never()).save(any());  // no persiste nueva guía
    }

    // ── GRE-3: Traslado entre sucursales requiere origen != destino ───────────

    @Test
    @DisplayName("GRE-3: Sucursales con mismo origen y destino lanza BadRequestException")
    void sucursales_mismoOrigenDestino_bloqueado() {
        Sucursal s = Sucursal.builder().id(5L).tenantId(TENANT_ID).nombre("Tienda A").build();
        when(sucursalRepo.findByIdAndTenantId(5L, TENANT_ID)).thenReturn(Optional.of(s));
        when(sucursalRepo.countByTenantIdAndActivoTrue(TENANT_ID)).thenReturn(2L);

        CrearGuiaRemisionRequest req = buildRequest();
        req.setTipoTraslado(TipoTrasladoGRE.SUCURSALES);
        req.setSucursalOrigenId(5L);
        req.setSucursalDestinoId(5L);

        assertThatThrownBy(() -> service.crearBorrador(req))
                .isInstanceOf(BadRequestException.class)
                .hasMessageContaining("distintas");
    }

    // ── GRE-4: Emisión idempotente — guía ya emitida retorna sin rellamar Lucode

    @Test
    @DisplayName("GRE-4: Emitir una guía ya EMITIDA es idempotente — no rellamada a Lucode")
    void emitir_yaEmitida_idempotente() {
        GuiaRemision guia = buildGuia(EstadoTributarioGRE.EMITIDA);
        when(guiaRepo.findByIdAndTenantIdForUpdate(1L, TENANT_ID)).thenReturn(Optional.of(guia));

        GuiaRemisionResponse resp = service.emitirGRE(1L);
        assertThat(resp.getEstadoTributario()).isEqualTo(EstadoTributarioGRE.EMITIDA);
        verifyNoInteractions(lucodeGREService);
    }

    // ── GRE-5: EMITIENDO bloquea segunda llamada ──────────────────────────────

    @Test
    @DisplayName("GRE-5: Emitir guía en estado EMITIENDO lanza BadRequestException")
    void emitir_enEstadoEmitiendo_bloqueado() {
        GuiaRemision guia = buildGuia(EstadoTributarioGRE.EMITIENDO);
        when(guiaRepo.findByIdAndTenantIdForUpdate(1L, TENANT_ID)).thenReturn(Optional.of(guia));

        assertThatThrownBy(() -> service.emitirGRE(1L))
                .isInstanceOf(BadRequestException.class)
                .hasMessageContaining("proceso de emisión");
    }

    // ── GRE-6: Lucode rechaza — estado pasa a ERROR, stock NO descontado ────────

    @Test
    @DisplayName("GRE-6: Lucode rechaza con success=false → estado ERROR, sin movimiento de inventario")
    void emitir_lucodeRechaza_estadoError_sinMovimientoInventario() {
        GuiaRemision guia = buildGuiaConSucursal(EstadoTributarioGRE.BORRADOR);
        when(guiaRepo.findByIdAndTenantIdForUpdate(1L, TENANT_ID)).thenReturn(Optional.of(guia));

        var lucodeResp = new com.stockflow.dto.gre.lucode.LucodeGREResponse();
        lucodeResp.setSuccess(false);
        lucodeResp.setMessage("Número de RUC inválido");
        when(lucodeGREService.emitirGRE(any(), anyString())).thenReturn(lucodeResp);

        assertThatThrownBy(() -> service.emitirGRE(1L))
                .isInstanceOf(BadRequestException.class)
                .hasMessageContaining("No se pudo emitir la guía")
                .hasMessageNotContaining("Lucode")
                .hasMessageNotContaining("RUC inválido");

        assertThat(guia.getEstadoTributario()).isEqualTo(EstadoTributarioGRE.ERROR);
        // CRÍTICO: el stock NO debe haberse descontado cuando Lucode rechaza
        verifyNoInteractions(movimientoService);
    }

    // ── GRE-6b: Timeout de Lucode — estado ERROR, stock NO descontado ─────────

    @Test
    @DisplayName("GRE-6b: Timeout de Lucode → estado ERROR, sin movimiento de inventario")
    void emitir_lucodeTimeout_estadoError_sinMovimientoInventario() {
        GuiaRemision guia = buildGuiaConSucursal(EstadoTributarioGRE.BORRADOR);
        when(guiaRepo.findByIdAndTenantIdForUpdate(1L, TENANT_ID)).thenReturn(Optional.of(guia));

        when(lucodeGREService.emitirGRE(any(), anyString()))
                .thenThrow(new RuntimeException("Connection timed out"));

        assertThatThrownBy(() -> service.emitirGRE(1L))
                .isInstanceOf(RuntimeException.class)
                .hasMessageContaining("Error emitiendo GRE");

        assertThat(guia.getEstadoTributario()).isEqualTo(EstadoTributarioGRE.ERROR);
        assertThat(guia.getLucodeErrorRaw()).contains("TIMEOUT");
        // Stock NO descontado — resultado de Lucode desconocido
        verifyNoInteractions(movimientoService);
    }

    // ── GRE-6c: Lucode acepta — SALIDA de inventario creada ──────────────────

    @Test
    @DisplayName("GRE-6c: Lucode acepta → estado EMITIDA + movimiento SALIDA creado")
    void emitir_lucodeAcepta_movimientoSalidaCreado() {
        GuiaRemision guia = buildGuiaConSucursal(EstadoTributarioGRE.BORRADOR);
        when(guiaRepo.findByIdAndTenantIdForUpdate(1L, TENANT_ID)).thenReturn(Optional.of(guia));

        var payload = new com.stockflow.dto.gre.lucode.LucodeGREPayload();
        payload.setHash("abc123");
        var lucodeResp = new com.stockflow.dto.gre.lucode.LucodeGREResponse();
        lucodeResp.setSuccess(true);
        lucodeResp.setPayload(payload);
        when(lucodeGREService.emitirGRE(any(), anyString())).thenReturn(lucodeResp);

        GuiaRemisionResponse resp = service.emitirGRE(1L);

        assertThat(resp.getEstadoTributario()).isEqualTo(EstadoTributarioGRE.EMITIDA);
        // Stock SÍ debe haberse descontado después de confirmación
        verify(movimientoService, atLeastOnce()).crearMovimiento(any());
    }

    // ── GRE-7: Cancelar BORRADOR OK, cancelar EMITIDA bloqueado ──────────────

    @Test
    @DisplayName("GRE-7a: Cancelar guía BORRADOR cambia a CANCELADA")
    void cancelar_borrador_ok() {
        GuiaRemision guia = buildGuia(EstadoTributarioGRE.BORRADOR);
        when(guiaRepo.findByIdAndTenantIdWithSucursales(1L, TENANT_ID)).thenReturn(Optional.of(guia));

        GuiaRemisionResponse resp = service.cancelarBorrador(1L);
        assertThat(resp.getEstadoTributario()).isEqualTo(EstadoTributarioGRE.CANCELADA);
    }

    @Test
    @DisplayName("GRE-7b: Cancelar guía EMITIDA lanza BadRequestException")
    void cancelar_emitida_bloqueado() {
        GuiaRemision guia = buildGuia(EstadoTributarioGRE.EMITIDA);
        when(guiaRepo.findByIdAndTenantIdWithSucursales(1L, TENANT_ID)).thenReturn(Optional.of(guia));

        assertThatThrownBy(() -> service.cancelarBorrador(1L))
                .isInstanceOf(BadRequestException.class)
                .hasMessageContaining("BORRADOR");
    }

    // ── GRE-8: Aislamiento de tenant ──────────────────────────────────────────

    @Test
    @DisplayName("GRE-8: Acceder a guía de otro tenant lanza excepción")
    void obtener_otroTenant_lanzaExcepcion() {
        when(guiaRepo.findByIdAndTenantId(99L, TENANT_ID)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> service.obtener(99L))
                .isInstanceOf(com.stockflow.exception.ResourceNotFoundException.class);
    }

    // ── Helpers ───────────────────────────────────────────────────────────────

    private CrearGuiaRemisionRequest buildRequest() {
        CrearGuiaRemisionRequest req = new CrearGuiaRemisionRequest();
        req.setTipoTraslado(TipoTrasladoGRE.DELIVERY);
        req.setSucursalOrigenId(10L);  // F-09: DELIVERY independiente requiere sucursal origen
        req.setMotivoTrasladoCodigo("01");
        req.setModalidadTransporte(ModalidadTransporteGRE.PRIVADO);
        req.setFechaTraslado(LocalDate.now().plusDays(1));
        req.setDestinatarioTipoDoc("6");
        req.setDestinatarioNumDoc("20100000001");
        req.setDestinatarioRazonSocial("Cliente Test");
        req.setDestinatarioDireccion("Av. Test 123");

        GuiaRemisionItemDTO item = new GuiaRemisionItemDTO();
        item.setProductoId(10L);
        item.setDescripcion("Producto Test");
        item.setCantidad(java.math.BigDecimal.ONE);
        req.setItems(List.of(item));

        TransporteDTO td = new TransporteDTO();
        td.setDireccionPartida("Partida 1");
        td.setDireccionLlegada("Llegada 2");
        req.setTransporte(td);

        return req;
    }

    private GuiaRemision buildGuia(EstadoTributarioGRE estado) {
        Sucursal origen = Sucursal.builder().id(10L).tenantId(TENANT_ID).nombre("Tienda Origen").build();
        return GuiaRemision.builder()
                .id(1L)
                .tenantId(TENANT_ID)
                .serie("T001")
                .correlativo(1)
                .tipoTraslado(TipoTrasladoGRE.DELIVERY)
                .motivoTrasladoCodigo("01")
                .modalidadTransporte(ModalidadTransporteGRE.PRIVADO)
                .fechaTraslado(LocalDate.now())
                .estadoTributario(estado)
                .estadoLogistico(estado == EstadoTributarioGRE.EMITIDA
                        ? EstadoLogisticoGRE.EN_TRANSITO : EstadoLogisticoGRE.PENDIENTE)
                .sucursalOrigen(origen)  // F-09: DELIVERY sin venta requiere sucursal origen
                .items(new java.util.ArrayList<>())
                .build();
    }

    // ── GRE-ADMIN-1: Editar BORRADOR — campos escalares actualizados ──────────

    @Test
    @DisplayName("GRE-ADMIN-1: Editar borrador actualiza campos escalares correctamente")
    void editarBorrador_borrador_actualizaCampos() {
        GuiaRemision guia = buildGuia(EstadoTributarioGRE.BORRADOR);
        when(guiaRepo.findByIdAndTenantIdWithSucursales(1L, TENANT_ID)).thenReturn(Optional.of(guia));

        EditarBorradorRequest req = buildEditRequest();
        req.setObservaciones("Observacion editada");
        req.setNumeroBultos(5);

        GuiaRemisionResponse resp = service.editarBorrador(1L, req);
        assertThat(guia.getObservaciones()).isEqualTo("Observacion editada");
        assertThat(guia.getNumeroBultos()).isEqualTo(5);
        assertThat(resp.getEstadoTributario()).isEqualTo(EstadoTributarioGRE.BORRADOR);
    }

    // ── GRE-ADMIN-2: Editar ERROR — permitido (para corregir y reintentar) ───

    @Test
    @DisplayName("GRE-ADMIN-2: Editar guía en estado ERROR está permitido")
    void editarBorrador_error_permitido() {
        GuiaRemision guia = buildGuia(EstadoTributarioGRE.ERROR);
        when(guiaRepo.findByIdAndTenantIdWithSucursales(1L, TENANT_ID)).thenReturn(Optional.of(guia));

        GuiaRemisionResponse resp = service.editarBorrador(1L, buildEditRequest());
        assertThat(resp).isNotNull();
    }

    // ── GRE-ADMIN-3: Editar estado no editable — bloqueado ───────────────────

    @Test
    @DisplayName("GRE-ADMIN-3: Editar guía EMITIDA lanza BadRequestException")
    void editarBorrador_emitida_bloqueado() {
        GuiaRemision guia = buildGuia(EstadoTributarioGRE.EMITIDA);
        when(guiaRepo.findByIdAndTenantIdWithSucursales(1L, TENANT_ID)).thenReturn(Optional.of(guia));

        assertThatThrownBy(() -> service.editarBorrador(1L, buildEditRequest()))
                .isInstanceOf(BadRequestException.class)
                .hasMessageContaining("BORRADOR");
    }

    @Test
    @DisplayName("GRE-ADMIN-3b: Editar guía EMITIENDO lanza BadRequestException")
    void editarBorrador_emitiendo_bloqueado() {
        GuiaRemision guia = buildGuia(EstadoTributarioGRE.EMITIENDO);
        when(guiaRepo.findByIdAndTenantIdWithSucursales(1L, TENANT_ID)).thenReturn(Optional.of(guia));

        assertThatThrownBy(() -> service.editarBorrador(1L, buildEditRequest()))
                .isInstanceOf(BadRequestException.class)
                .hasMessageContaining("BORRADOR");
    }

    // ── GRE-ADMIN-4: Eliminar BORRADOR — eliminación exitosa ─────────────────

    @Test
    @DisplayName("GRE-ADMIN-4: Eliminar guía BORRADOR llama a delete sin excepción")
    void eliminarBorrador_borrador_ok() {
        GuiaRemision guia = buildGuia(EstadoTributarioGRE.BORRADOR);
        when(guiaRepo.findByIdAndTenantIdWithSucursales(1L, TENANT_ID)).thenReturn(Optional.of(guia));

        assertThatCode(() -> service.eliminarBorrador(1L)).doesNotThrowAnyException();
        verify(guiaRepo).delete(guia);
    }

    // ── GRE-ADMIN-5: Eliminar ERROR — permitido (nunca se emitió a SUNAT) ────

    @Test
    @DisplayName("GRE-ADMIN-5: Eliminar guía ERROR está permitido (nunca fue emitida)")
    void eliminarBorrador_error_permitido() {
        GuiaRemision guia = buildGuia(EstadoTributarioGRE.ERROR);
        when(guiaRepo.findByIdAndTenantIdWithSucursales(1L, TENANT_ID)).thenReturn(Optional.of(guia));

        assertThatCode(() -> service.eliminarBorrador(1L)).doesNotThrowAnyException();
        verify(guiaRepo).delete(guia);
    }

    // ── GRE-ADMIN-6: Eliminar EMITIDA — bloqueado ─────────────────────────────

    @Test
    @DisplayName("GRE-ADMIN-6: Eliminar guía EMITIDA lanza BadRequestException")
    void eliminarBorrador_emitida_bloqueado() {
        GuiaRemision guia = buildGuia(EstadoTributarioGRE.EMITIDA);
        when(guiaRepo.findByIdAndTenantIdWithSucursales(1L, TENANT_ID)).thenReturn(Optional.of(guia));

        assertThatThrownBy(() -> service.eliminarBorrador(1L))
                .isInstanceOf(BadRequestException.class)
                .hasMessageContaining("BORRADOR");
        verify(guiaRepo, never()).delete(any());
    }

    // ── GRE-ADMIN-7: Aislamiento de tenant en edición ─────────────────────────

    @Test
    @DisplayName("GRE-ADMIN-7: Editar guía de otro tenant lanza ResourceNotFoundException")
    void editarBorrador_otroTenant_lanzaExcepcion() {
        when(guiaRepo.findByIdAndTenantId(99L, TENANT_ID)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> service.editarBorrador(99L, buildEditRequest()))
                .isInstanceOf(com.stockflow.exception.ResourceNotFoundException.class);
    }

    // ── GRE-ADMIN-8: Aislamiento de tenant en eliminación ────────────────────

    @Test
    @DisplayName("GRE-ADMIN-8: Eliminar guía de otro tenant lanza ResourceNotFoundException")
    void eliminarBorrador_otroTenant_lanzaExcepcion() {
        when(guiaRepo.findByIdAndTenantId(99L, TENANT_ID)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> service.eliminarBorrador(99L))
                .isInstanceOf(com.stockflow.exception.ResourceNotFoundException.class);
        verify(guiaRepo, never()).delete(any());
    }

    // ── GRE-TRANSPORTE-1: Editar con transporte existente — UPDATE, sin INSERT duplicado ─

    @Test
    @DisplayName("GRE-TRANSPORTE-1: Editar con transporte existente actualiza in-place, sin INSERT duplicado")
    void editarBorrador_conTransporteExistente_actualizaSinInsertDuplicado() {
        GuiaRemisionTransporte transporteExistente = GuiaRemisionTransporte.builder()
                .id(5L)
                .tenantId(TENANT_ID)
                .direccionPartida("Partida original")
                .direccionLlegada("Llegada original")
                .build();
        GuiaRemision guia = buildGuia(EstadoTributarioGRE.BORRADOR);
        guia.setTransporte(transporteExistente);
        transporteExistente.setGuiaRemision(guia);

        when(guiaRepo.findByIdAndTenantIdWithSucursales(1L, TENANT_ID)).thenReturn(Optional.of(guia));

        EditarBorradorRequest req = buildEditRequest();
        req.getTransporte().setDireccionPartida("Partida nueva");
        req.getTransporte().setUbigeoPartida("150101");

        service.editarBorrador(1L, req);

        // La instancia no debe cambiar (mismo id), solo se actualizan sus campos
        assertThat(guia.getTransporte()).isSameAs(transporteExistente);
        assertThat(transporteExistente.getId()).isEqualTo(5L);
        assertThat(transporteExistente.getDireccionPartida()).isEqualTo("Partida nueva");
        assertThat(transporteExistente.getUbigeoPartida()).isEqualTo("150101");
    }

    // ── GRE-TRANSPORTE-2: Editar sin transporte previo — INSERT de único registro ─

    @Test
    @DisplayName("GRE-TRANSPORTE-2: Editar guía sin transporte previo inserta exactamente un registro")
    void editarBorrador_sinTransportePrevio_insertaUnicoRegistro() {
        GuiaRemision guia = buildGuia(EstadoTributarioGRE.BORRADOR);
        // sin transporte previo
        assertThat(guia.getTransporte()).isNull();

        when(guiaRepo.findByIdAndTenantIdWithSucursales(1L, TENANT_ID)).thenReturn(Optional.of(guia));

        service.editarBorrador(1L, buildEditRequest());

        // Se asignó un transporte nuevo con la asociación correcta
        assertThat(guia.getTransporte()).isNotNull();
        assertThat(guia.getTransporte().getId()).isNull(); // nuevo — sin id aún
        assertThat(guia.getTransporte().getGuiaRemision()).isSameAs(guia);
        assertThat(guia.getTransporte().getTenantId()).isEqualTo(TENANT_ID);
    }

    // ── GRE-TRANSPORTE-3: Editar varias veces — siempre actualiza el mismo registro ─

    @Test
    @DisplayName("GRE-TRANSPORTE-3: Editar varias veces conserva el mismo registro de transporte")
    void editarBorrador_variasVeces_actualizaMismoRegistro() {
        GuiaRemisionTransporte transporteExistente = GuiaRemisionTransporte.builder()
                .id(7L)
                .tenantId(TENANT_ID)
                .direccionPartida("Partida A")
                .direccionLlegada("Llegada A")
                .build();
        GuiaRemision guia = buildGuia(EstadoTributarioGRE.BORRADOR);
        guia.setTransporte(transporteExistente);
        transporteExistente.setGuiaRemision(guia);

        when(guiaRepo.findByIdAndTenantIdWithSucursales(1L, TENANT_ID)).thenReturn(Optional.of(guia));

        EditarBorradorRequest req1 = buildEditRequest();
        req1.getTransporte().setDireccionPartida("Partida B");
        service.editarBorrador(1L, req1);
        assertThat(guia.getTransporte()).isSameAs(transporteExistente);
        assertThat(transporteExistente.getId()).isEqualTo(7L);

        EditarBorradorRequest req2 = buildEditRequest();
        req2.getTransporte().setDireccionPartida("Partida C");
        service.editarBorrador(1L, req2);
        assertThat(guia.getTransporte()).isSameAs(transporteExistente);
        assertThat(transporteExistente.getId()).isEqualTo(7L);
        assertThat(transporteExistente.getDireccionPartida()).isEqualTo("Partida C");
    }

    // ── GRE-TRANSPORTE-4: Ubigeos se persisten correctamente ─────────────────────

    @Test
    @DisplayName("GRE-TRANSPORTE-4: Ubigeo de partida y llegada se persisten en la entidad de transporte")
    void editarBorrador_persisteUbigeos() {
        GuiaRemisionTransporte transporteExistente = GuiaRemisionTransporte.builder()
                .id(9L)
                .tenantId(TENANT_ID)
                .direccionPartida("Partida original")
                .direccionLlegada("Llegada original")
                .build();
        GuiaRemision guia = buildGuia(EstadoTributarioGRE.BORRADOR);
        guia.setTransporte(transporteExistente);
        transporteExistente.setGuiaRemision(guia);

        when(guiaRepo.findByIdAndTenantIdWithSucursales(1L, TENANT_ID)).thenReturn(Optional.of(guia));

        EditarBorradorRequest req = buildEditRequest();
        req.getTransporte().setUbigeoPartida("150101");
        req.getTransporte().setUbigeoLlegada("130101");
        req.getTransporte().setDireccionPartida("Av. Lima 100");
        req.getTransporte().setDireccionLlegada("Av. Trujillo 200");

        service.editarBorrador(1L, req);

        assertThat(transporteExistente.getUbigeoPartida()).isEqualTo("150101");
        assertThat(transporteExistente.getUbigeoLlegada()).isEqualTo("130101");
        assertThat(transporteExistente.getDireccionPartida()).isEqualTo("Av. Lima 100");
        assertThat(transporteExistente.getDireccionLlegada()).isEqualTo("Av. Trujillo 200");
    }

    // ── GRE-TRANSPORTE-5: Tenant isolation en transporte ─────────────────────────

    @Test
    @DisplayName("GRE-TRANSPORTE-5: El nuevo transporte creado lleva el tenantId correcto")
    void editarBorrador_nuevoTransporte_tieneTenantCorrecto() {
        GuiaRemision guia = buildGuia(EstadoTributarioGRE.BORRADOR);
        when(guiaRepo.findByIdAndTenantIdWithSucursales(1L, TENANT_ID)).thenReturn(Optional.of(guia));

        service.editarBorrador(1L, buildEditRequest());

        assertThat(guia.getTransporte()).isNotNull();
        assertThat(guia.getTransporte().getTenantId()).isEqualTo(TENANT_ID);
    }

    private EditarBorradorRequest buildEditRequest() {
        EditarBorradorRequest req = new EditarBorradorRequest();
        req.setModalidadTransporte(ModalidadTransporteGRE.PRIVADO);
        req.setFechaTraslado(LocalDate.now().plusDays(1));

        GuiaRemisionItemDTO item = new GuiaRemisionItemDTO();
        item.setProductoId(10L);
        item.setDescripcion("Producto editado");
        item.setCantidad(java.math.BigDecimal.ONE);
        req.setItems(List.of(item));

        TransporteDTO td = new TransporteDTO();
        td.setDireccionPartida("Partida nueva");
        td.setDireccionLlegada("Llegada nueva");
        req.setTransporte(td);

        return req;
    }

    // ── GRE-RECUP-1: Recuperación exitosa ────────────────────────────────────────

    @Test
    @DisplayName("GRE-RECUP-1: recuperarEmitiendo transiciona EMITIENDO → ERROR y registra evento")
    void recuperarEmitiendo_estadoEmitiendo_transicionaAError() {
        GuiaRemision guia = buildGuia(EstadoTributarioGRE.EMITIENDO);
        when(guiaRepo.findByIdAndTenantIdWithSucursales(1L, TENANT_ID)).thenReturn(Optional.of(guia));

        service.recuperarEmitiendo(1L);

        assertThat(guia.getEstadoTributario()).isEqualTo(EstadoTributarioGRE.ERROR);
        assertThat(guia.getLucodeErrorRaw()).contains("Recuperación manual");
        verify(guiaRepo).save(guia);
        verify(eventoRepo).save(argThat(e ->
                e.getTipoEvento() == TipoEventoGRE.RECUPERACION_INTERRUPCION));
    }

    // ── GRE-RECUP-2: Rechaza estado no-EMITIENDO ─────────────────────────────────

    @Test
    @DisplayName("GRE-RECUP-2: recuperarEmitiendo lanza BadRequest si el estado no es EMITIENDO")
    void recuperarEmitiendo_estadoBorrador_lanzaError() {
        GuiaRemision guia = buildGuia(EstadoTributarioGRE.BORRADOR);
        when(guiaRepo.findByIdAndTenantIdWithSucursales(1L, TENANT_ID)).thenReturn(Optional.of(guia));

        assertThatThrownBy(() -> service.recuperarEmitiendo(1L))
                .isInstanceOf(BadRequestException.class)
                .hasMessageContaining("EMITIENDO");
        verify(guiaRepo, never()).save(any());
    }

    // ── GRE-RECUP-3: Rechaza si lucodeHash presente ──────────────────────────────

    @Test
    @DisplayName("GRE-RECUP-3: recuperarEmitiendo lanza BadRequest si lucodeHash no es null")
    void recuperarEmitiendo_conLucodeHash_lanzaError() {
        GuiaRemision guia = buildGuia(EstadoTributarioGRE.EMITIENDO);
        guia.setLucodeHash("abc123hash");
        when(guiaRepo.findByIdAndTenantIdWithSucursales(1L, TENANT_ID)).thenReturn(Optional.of(guia));

        assertThatThrownBy(() -> service.recuperarEmitiendo(1L))
                .isInstanceOf(BadRequestException.class)
                .hasMessageContaining("datos de emisión");
        verify(guiaRepo, never()).save(any());
    }

    // ── GRE-RECUP-4: Rechaza si xmlUrl presente ──────────────────────────────────

    @Test
    @DisplayName("GRE-RECUP-4: recuperarEmitiendo lanza BadRequest si xmlUrl no es null")
    void recuperarEmitiendo_conXmlUrl_lanzaError() {
        GuiaRemision guia = buildGuia(EstadoTributarioGRE.EMITIENDO);
        guia.setXmlUrl("https://cdn.example.com/gre.xml");
        when(guiaRepo.findByIdAndTenantIdWithSucursales(1L, TENANT_ID)).thenReturn(Optional.of(guia));

        assertThatThrownBy(() -> service.recuperarEmitiendo(1L))
                .isInstanceOf(BadRequestException.class)
                .hasMessageContaining("datos de emisión");
        verify(guiaRepo, never()).save(any());
    }

    // ── GRE-RECUP-5: Tenant isolation ────────────────────────────────────────────

    @Test
    @DisplayName("GRE-RECUP-5: recuperarEmitiendo lanza ResourceNotFoundException si id no pertenece al tenant")
    void recuperarEmitiendo_idOtroTenant_lanzaNotFound() {
        when(guiaRepo.findByIdAndTenantId(99L, TENANT_ID)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> service.recuperarEmitiendo(99L))
                .isInstanceOf(com.stockflow.exception.ResourceNotFoundException.class);
    }

    // ── GRE-UBIGEO-1: Código Huacho (150801) válido ──────────────────────────────

    @Test
    @DisplayName("GRE-UBIGEO-1: Código 150801 (Huacho, Lima) es aceptado y persiste")
    void crearBorrador_ubigeoHuacho_aceptado() {
        when(ubigeoRepository.existsById("150801")).thenReturn(true);

        CrearGuiaRemisionRequest req = buildRequest();
        req.getTransporte().setUbigeoPartida("150801");
        req.getTransporte().setUbigeoLlegada("150801");

        GuiaRemisionResponse resp = service.crearBorrador(req);
        assertThat(resp.getEstadoTributario()).isEqualTo(EstadoTributarioGRE.BORRADOR);
    }

    // ── GRE-UBIGEO-2: Código inexistente en catálogo rechazado ───────────────────

    @Test
    @DisplayName("GRE-UBIGEO-2: Código 999999 no existe en catálogo → BadRequestException, sin Lucode")
    void crearBorrador_ubigeoInexistente_rechazado() {
        when(ubigeoRepository.existsById("999999")).thenReturn(false);

        CrearGuiaRemisionRequest req = buildRequest();
        req.getTransporte().setUbigeoPartida("999999");

        assertThatThrownBy(() -> service.crearBorrador(req))
                .isInstanceOf(BadRequestException.class)
                .hasMessageContaining("999999")
                .hasMessageContaining("catálogo");
        verifyNoInteractions(lucodeGREService);
        verifyNoInteractions(movimientoService);
    }

    // ── GRE-UBIGEO-3: Código con longitud incorrecta rechazado ───────────────────

    @Test
    @DisplayName("GRE-UBIGEO-3: Código de 5 dígitos (15080) rechazado antes de consultar catálogo")
    void crearBorrador_ubigeoLongitudCorta_rechazado() {
        CrearGuiaRemisionRequest req = buildRequest();
        req.getTransporte().setUbigeoPartida("15080");

        assertThatThrownBy(() -> service.crearBorrador(req))
                .isInstanceOf(BadRequestException.class)
                .hasMessageContaining("6 dígitos");
        verify(ubigeoRepository, never()).existsById(any());
        verifyNoInteractions(lucodeGREService);
    }

    @Test
    @DisplayName("GRE-UBIGEO-3b: Código de 7 dígitos (1508011) rechazado antes de consultar catálogo")
    void crearBorrador_ubigeoLongitudLarga_rechazado() {
        CrearGuiaRemisionRequest req = buildRequest();
        req.getTransporte().setUbigeoPartida("1508011");

        assertThatThrownBy(() -> service.crearBorrador(req))
                .isInstanceOf(BadRequestException.class)
                .hasMessageContaining("6 dígitos");
        verify(ubigeoRepository, never()).existsById(any());
    }

    // ── GRE-UBIGEO-4: Ambos ubigeos válidos aceptados ────────────────────────────

    @Test
    @DisplayName("GRE-UBIGEO-4: Partida Lima (150101) y llegada La Libertad (130101) ambos válidos")
    void crearBorrador_ambosUbigeosValidos_aceptado() {
        when(ubigeoRepository.existsById("150101")).thenReturn(true);
        when(ubigeoRepository.existsById("130101")).thenReturn(true);

        CrearGuiaRemisionRequest req = buildRequest();
        req.getTransporte().setUbigeoPartida("150101");
        req.getTransporte().setUbigeoLlegada("130101");

        GuiaRemisionResponse resp = service.crearBorrador(req);
        assertThat(resp.getEstadoTributario()).isEqualTo(EstadoTributarioGRE.BORRADOR);
        verify(ubigeoRepository).existsById("150101");
        verify(ubigeoRepository).existsById("130101");
    }

    // ── GRE-UBIGEO-5: Rechazo antes de llamar a Lucode ───────────────────────────

    @Test
    @DisplayName("GRE-UBIGEO-5: Ubigeo inválido en crearBorrador rechazado antes de llamar a Lucode")
    void crearBorrador_ubigeoInvalido_rechazadoAntesDeEmision() {
        when(ubigeoRepository.existsById("000000")).thenReturn(false);

        CrearGuiaRemisionRequest req = buildRequest();
        req.getTransporte().setUbigeoPartida("000000");

        assertThatThrownBy(() -> service.crearBorrador(req))
                .isInstanceOf(BadRequestException.class);
        verifyNoInteractions(lucodeGREService);
        verifyNoInteractions(movimientoService);
    }

    // ── GRE-UBIGEO-6: Ubigeo inválido en editarBorrador rechazado ────────────────

    @Test
    @DisplayName("GRE-UBIGEO-6: Ubigeo inexistente en editarBorrador rechaza sin modificar inventario")
    void editarBorrador_ubigeoInexistente_rechazado() {
        GuiaRemision guia = buildGuia(EstadoTributarioGRE.BORRADOR);
        when(guiaRepo.findByIdAndTenantIdWithSucursales(1L, TENANT_ID)).thenReturn(Optional.of(guia));
        when(ubigeoRepository.existsById("888888")).thenReturn(false);

        EditarBorradorRequest req = buildEditRequest();
        req.getTransporte().setUbigeoPartida("888888");

        assertThatThrownBy(() -> service.editarBorrador(1L, req))
                .isInstanceOf(BadRequestException.class)
                .hasMessageContaining("888888");
        verifyNoInteractions(movimientoService);
    }

    /** Helper: guía con sucursal origen — para tests de inventario. */
    private GuiaRemision buildGuiaConSucursal(EstadoTributarioGRE estado) {
        Sucursal origen = Sucursal.builder().id(10L).tenantId(TENANT_ID).nombre("Tienda Origen").build();
        Producto prod = Producto.builder().id(10L).tenantId(TENANT_ID).nombre("Producto Test").stockActual(50).build();
        GuiaRemisionItem item = GuiaRemisionItem.builder()
                .producto(prod)
                .descripcion("Producto Test")
                .cantidad(java.math.BigDecimal.TEN)
                .unidadMedida("NIU")
                .tenantId(TENANT_ID)
                .build();
        GuiaRemision g = buildGuia(estado);
        g.setSucursalOrigen(origen);
        g.getItems().add(item);
        return g;
    }
}
