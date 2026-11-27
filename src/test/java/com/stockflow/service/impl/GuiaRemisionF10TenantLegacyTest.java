package com.stockflow.service.impl;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.stockflow.dto.gre.*;
import com.stockflow.dto.gre.lucode.LucodeGREResponse;
import com.stockflow.entity.*;
import com.stockflow.entity.gre.*;
import com.stockflow.exception.BadRequestException;
import com.stockflow.repository.*;
import com.stockflow.service.MovimientoInventarioService;
import com.stockflow.service.StockLoteService;
import com.stockflow.util.TenantContext;
import org.junit.jupiter.api.*;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

/**
 * Tests de integración unitaria para F-10: compatibilidad GRE con tenants legacy sin sucursales.
 *
 * Casos cubiertos:
 *  T-F10-1:  Caso A — tenant con sucursales, origen seleccionado → permitido
 *  T-F10-2:  Caso A-fallido — tenant con sucursales, sin origen → BadRequestException "sucursal de origen"
 *  T-F10-3:  Caso B — tenant sin sucursales, con ubigeo+dirección despacho → permitido
 *  T-F10-4:  Caso C — tenant sin sucursales ni punto de despacho → BadRequestException "punto de despacho"
 *  T-F10-5:  Caso D — DELIVERY con venta → permitido sin importar sucursales/despacho
 *  T-F10-6:  Caso E — TRASLADO SUCURSALES → no afectado por F-10
 *  T-F10-7:  Caso B — emitirGRE registra SALIDA con sucursalId=null
 *  T-F10-8:  Caso B — emitirGRE valida stock con sucursalId=null
 *  T-F10-9:  Caso C — editarBorrador rechazado "punto de despacho"
 *  T-F10-10: Caso B — editarBorrador permitido
 *  T-F10-11: Caso B — stock insuficiente → BadRequestException antes de Lucode
 *  T-F10-12: Caso B — reconciliarGREAceptada registra salida con sucursalId=null
 *  T-F10-13: Caso A — reconciliarGREAceptada registra salida con sucursalId concreto
 *  T-F10-14: Caso B — MovimientoInventario.sucursalId es null
 */
@ExtendWith(MockitoExtension.class)
@DisplayName("GuiaRemisionService — F-10: Compatibilidad tenant legacy sin sucursales")
class GuiaRemisionF10TenantLegacyTest {

    private static final String TENANT  = "tenant-legacy";
    private static final Long   USER_ID = 1L;

    @Mock GuiaRemisionRepository           guiaRepo;
    @Mock GuiaRemisionEventoRepository     eventoRepo;
    @Mock SucursalRepository               sucursalRepo;
    @Mock ProductoRepository               productoRepo;
    @Mock TenantRepository                 tenantRepo;
    @Mock VentaRepository                  ventaRepo;
    @Mock ProductoPresentacionRepository   presentacionRepo;
    @Mock MovimientoInventarioService      movimientoService;
    @Mock MovimientoInventarioRepository   movimientoRepo;
    @Mock LucodeGREService                 lucodeGREService;
    @Mock GuiaRemisionPdfService           pdfService;
    @Mock StockLoteService                 stockLoteService;
    @Mock UbigeoRepository                 ubigeoRepository;
    @Mock com.stockflow.repository.ProductoStockSucursalRepository stockSucursalRepo;
    @Mock com.stockflow.repository.ProductoVarianteRepository varianteRepo;
    @Mock com.stockflow.repository.ProductoVarianteStockSucursalRepository varianteSucursalRepo;
    @Mock GreSerieContadorService serieContadorService;

    private GuiaRemisionServiceImpl service;

    @BeforeEach
    void setUp() {
        TenantContext.setCurrentTenant(TENANT);
        TenantContext.setCurrentUserId(USER_ID);
        service = new GuiaRemisionServiceImpl(
                guiaRepo, eventoRepo, sucursalRepo, productoRepo, tenantRepo,
                ventaRepo, presentacionRepo, movimientoService, movimientoRepo,
                lucodeGREService, pdfService, new ObjectMapper(), stockLoteService,
                ubigeoRepository, stockSucursalRepo, varianteRepo, varianteSucursalRepo,
                serieContadorService);

        // F-22: reconciliarGREAceptada consulta tenantRepo; sin oseToken omite verificación Lucode.
        // Los tests individuales que llaman emitirGRE sobreescriben este mock con su builder propio.
        Tenant tenantBase = new Tenant();
        tenantBase.setTenantId(TENANT);
        tenantBase.setSerieGuia("T001");
        tenantBase.setOseToken(null);
        lenient().when(tenantRepo.findByTenantId(TENANT)).thenReturn(Optional.of(tenantBase));
    }

    @AfterEach
    void tearDown() {
        TenantContext.clear();
    }

    // ── Factories ──────────────────────────────────────────────────────────────

    private Tenant buildTenantConSucursales() {
        Tenant t = new Tenant();
        t.setTenantId(TENANT);
        t.setSerieGuia("T001");
        t.setNombre("Empresa Con Sucursales");
        t.setOseToken("tok-test");
        return t;
    }

    private Tenant buildTenantLegacyConDespacho() {
        Tenant t = new Tenant();
        t.setTenantId(TENANT);
        t.setSerieGuia("T001");
        t.setNombre("Botica Santa Fe");
        t.setUbigeoDespacho("150101");
        t.setDireccionDespacho("Jr. Amazonas 456, Lima");
        t.setOseToken("tok-test");
        return t;
    }

    private Tenant buildTenantLegacySinDespacho() {
        Tenant t = new Tenant();
        t.setTenantId(TENANT);
        t.setSerieGuia("T001");
        t.setNombre("Empresa Sin Configurar");
        t.setOseToken("tok-test");
        return t;
    }

    private Sucursal buildSucursal(Long id) {
        Sucursal s = new Sucursal();
        s.setId(id);
        s.setTenantId(TENANT);
        s.setNombre("Almacén Principal");
        return s;
    }

    private Producto buildProducto() {
        Producto p = new Producto();
        p.setId(5L);
        p.setNombre("Paracetamol 500mg");
        p.setTenantId(TENANT);
        p.setStockActual(50);
        return p;
    }

    private CrearGuiaRemisionRequest buildCrearRequest(Long sucursalOrigenId, Long ventaId) {
        GuiaRemisionItemDTO item = new GuiaRemisionItemDTO();
        item.setProductoId(5L);
        item.setCantidad(new BigDecimal("2"));
        item.setUnidadMedida("NIU");

        TransporteDTO td = new TransporteDTO();
        td.setDireccionPartida("Jr. Amazonas 456");
        td.setDireccionLlegada("Av. Grau 789");

        CrearGuiaRemisionRequest req = new CrearGuiaRemisionRequest();
        req.setTipoTraslado(TipoTrasladoGRE.DELIVERY);
        req.setMotivoTrasladoCodigo("01");
        req.setModalidadTransporte(ModalidadTransporteGRE.PRIVADO);
        req.setFechaTraslado(LocalDate.now().plusDays(1));
        req.setSucursalOrigenId(sucursalOrigenId);
        req.setVentaId(ventaId);
        req.setDestinatarioRazonSocial("Cliente F-10");
        req.setDestinatarioTipoDoc("6");
        req.setDestinatarioNumDoc("20100000001");
        req.setItems(List.of(item));
        req.setTransporte(td);
        return req;
    }

    private GuiaRemision buildBorradorDelivery(Long id, Sucursal origen, Venta venta) {
        Producto producto = buildProducto();
        GuiaRemisionItem item = GuiaRemisionItem.builder()
                .id(1L)
                .producto(producto)
                .descripcion("Paracetamol 500mg")
                .cantidad(new BigDecimal("3"))
                .unidadMedida("NIU")
                .tenantId(TENANT)
                .build();

        GuiaRemision guia = GuiaRemision.builder()
                .id(id)
                .tenantId(TENANT)
                .serie("T001")
                .correlativo(10)
                .tipoTraslado(TipoTrasladoGRE.DELIVERY)
                .motivoTrasladoCodigo("01")
                .modalidadTransporte(ModalidadTransporteGRE.PRIVADO)
                .fechaTraslado(LocalDate.now().plusDays(1))
                .sucursalOrigen(origen)
                .venta(venta)
                .estadoTributario(EstadoTributarioGRE.BORRADOR)
                .estadoLogistico(EstadoLogisticoGRE.PENDIENTE)
                .items(new java.util.ArrayList<>(List.of(item)))
                .build();

        item.setGuiaRemision(guia);
        return guia;
    }

    private EditarBorradorRequest buildEditarRequest(Long sucursalOrigenId) {
        GuiaRemisionItemDTO item = new GuiaRemisionItemDTO();
        item.setProductoId(5L);
        item.setCantidad(new BigDecimal("2"));
        item.setUnidadMedida("NIU");

        TransporteDTO td = new TransporteDTO();
        td.setDireccionPartida("Jr. Amazonas 456");
        td.setDireccionLlegada("Av. Grau 789");

        EditarBorradorRequest req = new EditarBorradorRequest();
        req.setModalidadTransporte(ModalidadTransporteGRE.PRIVADO);
        req.setFechaTraslado(LocalDate.now().plusDays(1));
        req.setSucursalOrigenId(sucursalOrigenId);
        req.setItems(List.of(item));
        req.setTransporte(td);
        return req;
    }

    private void stubSaveGuia() {
        when(guiaRepo.save(any())).thenAnswer(inv -> {
            GuiaRemision g = inv.getArgument(0);
            if (g.getId() == null) g.setId(99L);
            return g;
        });
    }

    private LucodeGREResponse buildLucodeExito() {
        var payload = new com.stockflow.dto.gre.lucode.LucodeGREPayload();
        payload.setHash("hashF10Test");
        LucodeGREResponse resp = new LucodeGREResponse();
        resp.setSuccess(true);
        resp.setPayload(payload);
        return resp;
    }

    // ── T-F10-1: Caso A — tenant con sucursales, origen seleccionado → permitido ──

    @Test
    @DisplayName("T-F10-1: Caso A — tenant con sucursales con origen seleccionado es permitido")
    void casoA_conOrigen_permitido() {
        Sucursal origen = buildSucursal(10L);

        when(guiaRepo.existsByIdempotencyKey(any())).thenReturn(false);
        when(tenantRepo.findByTenantId(TENANT)).thenReturn(Optional.of(buildTenantConSucursales()));
        when(guiaRepo.findMaxCorrelativo(TENANT, "T001")).thenReturn(0);
        when(sucursalRepo.findByIdAndTenantId(10L, TENANT)).thenReturn(Optional.of(origen));
        when(productoRepo.findById(5L)).thenReturn(Optional.of(buildProducto()));
        stubSaveGuia();

        assertThatNoException().isThrownBy(() -> service.crearBorrador(buildCrearRequest(10L, null)));
    }

    // ── T-F10-2: Caso A-fallido — tenant con sucursales, sin origen → rechazado ─

    @Test
    @DisplayName("T-F10-2: Caso A-fallido — tenant con sucursales sin origen seleccionado es rechazado")
    void casoAFallido_sinOrigen_rechazado() {
        when(guiaRepo.existsByIdempotencyKey(any())).thenReturn(false);
        when(tenantRepo.findByTenantId(TENANT)).thenReturn(Optional.of(buildTenantConSucursales()));
        when(guiaRepo.findMaxCorrelativo(TENANT, "T001")).thenReturn(0);
        when(sucursalRepo.countByTenantIdAndActivoTrue(TENANT)).thenReturn(2L);

        assertThatThrownBy(() -> service.crearBorrador(buildCrearRequest(null, null)))
                .isInstanceOf(BadRequestException.class)
                .hasMessageContaining("sucursal de origen");

        verify(guiaRepo, never()).save(any());
    }

    // ── T-F10-3: Caso B — tenant legacy con despacho → permitido ─────────────

    @Test
    @DisplayName("T-F10-3: Caso B — tenant sin sucursales con ubigeo+dirección despacho es permitido")
    void casoB_legacyConDespacho_permitido() {
        when(guiaRepo.existsByIdempotencyKey(any())).thenReturn(false);
        when(tenantRepo.findByTenantId(TENANT)).thenReturn(Optional.of(buildTenantLegacyConDespacho()));
        when(guiaRepo.findMaxCorrelativo(TENANT, "T001")).thenReturn(0);
        when(sucursalRepo.countByTenantIdAndActivoTrue(TENANT)).thenReturn(0L);
        when(productoRepo.findById(5L)).thenReturn(Optional.of(buildProducto()));
        stubSaveGuia();

        // sucursalOrigenId=null, sin venta, tenant legacy con despacho
        assertThatNoException().isThrownBy(() -> service.crearBorrador(buildCrearRequest(null, null)));

        verify(guiaRepo, atLeast(1)).save(any());
    }

    // ── T-F10-4: Caso C — tenant sin sucursales ni despacho → rechazado ───────

    @Test
    @DisplayName("T-F10-4: Caso C — tenant sin sucursales ni punto de despacho es rechazado")
    void casoC_sinSucursalesSinDespacho_rechazado() {
        when(guiaRepo.existsByIdempotencyKey(any())).thenReturn(false);
        when(tenantRepo.findByTenantId(TENANT)).thenReturn(Optional.of(buildTenantLegacySinDespacho()));
        when(guiaRepo.findMaxCorrelativo(TENANT, "T001")).thenReturn(0);
        when(sucursalRepo.countByTenantIdAndActivoTrue(TENANT)).thenReturn(0L);

        assertThatThrownBy(() -> service.crearBorrador(buildCrearRequest(null, null)))
                .isInstanceOf(BadRequestException.class)
                .hasMessageContaining("punto de despacho")
                .hasMessageContaining("Configuración");

        verify(guiaRepo, never()).save(any());
    }

    // ── T-F10-5: Caso D — DELIVERY con venta → siempre permitido ─────────────

    @Test
    @DisplayName("T-F10-5: Caso D — DELIVERY con venta no requiere sucursal ni punto de despacho")
    void casoD_conVenta_siemprePermitido() {
        Venta venta = new Venta();
        venta.setId(50L);
        venta.setTenantId(TENANT);

        when(guiaRepo.existsByIdempotencyKey(any())).thenReturn(false);
        when(tenantRepo.findByTenantId(TENANT)).thenReturn(Optional.of(buildTenantLegacySinDespacho()));
        when(guiaRepo.findMaxCorrelativo(TENANT, "T001")).thenReturn(0);
        when(ventaRepo.findById(50L)).thenReturn(Optional.of(venta));
        when(productoRepo.findById(5L)).thenReturn(Optional.of(buildProducto()));
        stubSaveGuia();

        // ventaId=50 → validarOrigenDelivery retorna inmediatamente (Caso D)
        assertThatNoException().isThrownBy(() -> service.crearBorrador(buildCrearRequest(null, 50L)));
        verify(sucursalRepo, never()).countByTenantIdAndActivoTrue(any());
    }

    // ── T-F10-6: Caso E — TRASLADO SUCURSALES no afectado ────────────────────

    @Test
    @DisplayName("T-F10-6: Caso E — TRASLADO SUCURSALES conserva validaciones existentes, no afectado por F-10")
    void casoE_traslado_noAfectado() {
        Sucursal origen  = buildSucursal(10L);
        Sucursal destino = buildSucursal(20L);
        destino.setId(20L);

        when(guiaRepo.existsByIdempotencyKey(any())).thenReturn(false);
        when(tenantRepo.findByTenantId(TENANT)).thenReturn(Optional.of(buildTenantConSucursales()));
        when(guiaRepo.findMaxCorrelativo(TENANT, "T001")).thenReturn(0);
        when(sucursalRepo.countByTenantIdAndActivoTrue(TENANT)).thenReturn(2L);
        when(sucursalRepo.findByIdAndTenantId(10L, TENANT)).thenReturn(Optional.of(origen));
        when(sucursalRepo.findByIdAndTenantId(20L, TENANT)).thenReturn(Optional.of(destino));
        when(productoRepo.findById(5L)).thenReturn(Optional.of(buildProducto()));
        stubSaveGuia();

        GuiaRemisionItemDTO item = new GuiaRemisionItemDTO();
        item.setProductoId(5L);
        item.setCantidad(BigDecimal.ONE);
        item.setUnidadMedida("NIU");
        TransporteDTO td = new TransporteDTO();
        td.setDireccionPartida("Origen");
        td.setDireccionLlegada("Destino");
        CrearGuiaRemisionRequest req = new CrearGuiaRemisionRequest();
        req.setTipoTraslado(TipoTrasladoGRE.SUCURSALES);
        req.setMotivoTrasladoCodigo("01");
        req.setModalidadTransporte(ModalidadTransporteGRE.PRIVADO);
        req.setFechaTraslado(LocalDate.now().plusDays(1));
        req.setSucursalOrigenId(10L);
        req.setSucursalDestinoId(20L);
        req.setDestinatarioRazonSocial("Cliente Destino");
        req.setItems(List.of(item));
        req.setTransporte(td);

        assertThatNoException().isThrownBy(() -> service.crearBorrador(req));
        // F-28 sí consulta el count de sucursales para traslado SUCURSALES
        verify(sucursalRepo, times(1)).countByTenantIdAndActivoTrue(TENANT);
    }

    // ── T-F10-7: Caso B — emitirGRE registra SALIDA con sucursalId=null ───────

    @Test
    @DisplayName("T-F10-7: Caso B — emitirGRE registra movimiento SALIDA con sucursalId null")
    void casoB_emitir_registraSalidaConSucursalIdNull() {
        GuiaRemision guia = buildBorradorDelivery(77L, null, null);

        when(guiaRepo.findByIdAndTenantIdForUpdate(77L, TENANT)).thenReturn(Optional.of(guia));
        when(tenantRepo.findByTenantId(TENANT)).thenReturn(Optional.of(buildTenantLegacyConDespacho()));
        when(sucursalRepo.countByTenantIdAndActivoTrue(TENANT)).thenReturn(0L);
        when(stockLoteService.getStockVigente(TENANT, 5L, null)).thenReturn(50);
        when(guiaRepo.saveAndFlush(any())).thenAnswer(inv -> inv.getArgument(0));
        when(lucodeGREService.emitirGRE(any(), any())).thenReturn(buildLucodeExito());
        when(guiaRepo.save(any())).thenAnswer(inv -> inv.getArgument(0));

        service.emitirGRE(77L);

        ArgumentCaptor<MovimientoInventario> captor = ArgumentCaptor.forClass(MovimientoInventario.class);
        verify(movimientoService, times(1)).crearMovimiento(captor.capture());
        MovimientoInventario mov = captor.getValue();
        assertThat(mov.getSucursalId()).isNull();
        assertThat(mov.getTipo()).isEqualTo("SALIDA");
        assertThat(mov.getCantidad()).isEqualTo(3);
    }

    // ── T-F10-8: Caso B — validación stock usa sucursalId=null ───────────────

    @Test
    @DisplayName("T-F10-8: Caso B — emitirGRE consulta stock general del tenant (sucursalId=null)")
    void casoB_emitir_validaStockConSucursalIdNull() {
        GuiaRemision guia = buildBorradorDelivery(78L, null, null);

        when(guiaRepo.findByIdAndTenantIdForUpdate(78L, TENANT)).thenReturn(Optional.of(guia));
        when(tenantRepo.findByTenantId(TENANT)).thenReturn(Optional.of(buildTenantLegacyConDespacho()));
        when(sucursalRepo.countByTenantIdAndActivoTrue(TENANT)).thenReturn(0L);
        when(stockLoteService.getStockVigente(TENANT, 5L, null)).thenReturn(100);
        when(guiaRepo.saveAndFlush(any())).thenAnswer(inv -> inv.getArgument(0));
        when(lucodeGREService.emitirGRE(any(), any())).thenReturn(buildLucodeExito());
        when(guiaRepo.save(any())).thenAnswer(inv -> inv.getArgument(0));

        service.emitirGRE(78L);

        // Verificar que se consultó stock con sucursalId=null (no con una sucursal específica)
        verify(stockLoteService).getStockVigente(TENANT, 5L, null);
        verify(stockLoteService, never()).getStockVigente(eq(TENANT), eq(5L), argThat(id -> id != null));
    }

    // ── T-F10-9: Caso C en editarBorrador → rechazado ────────────────────────

    @Test
    @DisplayName("T-F10-9: Caso C — editarBorrador sin punto de despacho es rechazado")
    void casoC_editarBorrador_rechazado() {
        GuiaRemision guia = buildBorradorDelivery(55L, null, null);
        when(guiaRepo.findByIdAndTenantIdWithSucursales(55L, TENANT)).thenReturn(Optional.of(guia));
        when(tenantRepo.findByTenantId(TENANT)).thenReturn(Optional.of(buildTenantLegacySinDespacho()));
        when(sucursalRepo.countByTenantIdAndActivoTrue(TENANT)).thenReturn(0L);

        assertThatThrownBy(() -> service.editarBorrador(55L, buildEditarRequest(null)))
                .isInstanceOf(BadRequestException.class)
                .hasMessageContaining("punto de despacho");

        verify(guiaRepo, never()).save(any());
    }

    // ── T-F10-10: Caso B en editarBorrador → permitido ───────────────────────

    @Test
    @DisplayName("T-F10-10: Caso B — editarBorrador con tenant legacy con despacho es permitido")
    void casoB_editarBorrador_permitido() {
        Producto producto = buildProducto();
        GuiaRemision guia = buildBorradorDelivery(55L, null, null);
        when(guiaRepo.findByIdAndTenantIdWithSucursales(55L, TENANT)).thenReturn(Optional.of(guia));
        when(tenantRepo.findByTenantId(TENANT)).thenReturn(Optional.of(buildTenantLegacyConDespacho()));
        when(sucursalRepo.countByTenantIdAndActivoTrue(TENANT)).thenReturn(0L);
        when(productoRepo.findById(5L)).thenReturn(Optional.of(producto));
        when(guiaRepo.save(any())).thenAnswer(inv -> inv.getArgument(0));

        assertThatNoException().isThrownBy(() -> service.editarBorrador(55L, buildEditarRequest(null)));
        verify(guiaRepo, times(1)).save(any());
    }

    // ── T-F10-11: Caso B — stock insuficiente → rechazado antes de Lucode ────

    @Test
    @DisplayName("T-F10-11: Caso B — stock insuficiente es rechazado antes de llamar a Lucode")
    void casoB_stockInsuficiente_rechazadoAntesLucode() {
        GuiaRemision guia = buildBorradorDelivery(79L, null, null); // cantidad=3
        when(guiaRepo.findByIdAndTenantIdForUpdate(79L, TENANT)).thenReturn(Optional.of(guia));
        when(tenantRepo.findByTenantId(TENANT)).thenReturn(Optional.of(buildTenantLegacyConDespacho()));
        when(sucursalRepo.countByTenantIdAndActivoTrue(TENANT)).thenReturn(0L);
        when(stockLoteService.getStockVigente(TENANT, 5L, null)).thenReturn(2); // solo 2 en stock, necesita 3

        assertThatThrownBy(() -> service.emitirGRE(79L))
                .isInstanceOf(BadRequestException.class)
                .hasMessageContaining("Stock insuficiente");

        verify(lucodeGREService, never()).emitirGRE(any(), any());
    }

    // ── T-F10-12: Caso B — reconciliar registra salida con sucursalId=null ───

    @Test
    @DisplayName("T-F10-12: Caso B — reconciliarGREAceptada registra SALIDA con sucursalId=null")
    void casoB_reconciliar_registraSalidaNull() {
        GuiaRemision guia = buildBorradorDelivery(80L, null, null);
        guia.setEstadoTributario(EstadoTributarioGRE.ERROR);

        when(guiaRepo.findByIdAndTenantIdForUpdate(80L, TENANT)).thenReturn(Optional.of(guia));
        when(movimientoRepo.existsByReferenciaAndTipoAndTenantId("GRE-80", "SALIDA", TENANT))
                .thenReturn(false);
        when(guiaRepo.save(any())).thenAnswer(inv -> inv.getArgument(0));

        ReconciliarGRERequest req = new ReconciliarGRERequest();
        req.setLucodeHash("hashRecon80");
        req.setXmlUrl("https://sandbox.lucode.pe/xml/T001-80.xml");
        req.setCdrUrl("https://sandbox.lucode.pe/cdr/T001-80.zip");

        service.reconciliarGREAceptada(80L, req);

        ArgumentCaptor<MovimientoInventario> captor = ArgumentCaptor.forClass(MovimientoInventario.class);
        verify(movimientoService, times(1)).crearMovimiento(captor.capture());
        assertThat(captor.getValue().getSucursalId()).isNull();
        assertThat(captor.getValue().getTipo()).isEqualTo("SALIDA");
    }

    // ── T-F10-13: Caso A — reconciliar registra salida con sucursalId concreto

    @Test
    @DisplayName("T-F10-13: Caso A — reconciliarGREAceptada registra SALIDA con sucursalId concreto")
    void casoA_reconciliar_registraSalidaConSucursal() {
        Sucursal origen = buildSucursal(10L);
        GuiaRemision guia = buildBorradorDelivery(81L, origen, null);
        guia.setEstadoTributario(EstadoTributarioGRE.ERROR);

        when(guiaRepo.findByIdAndTenantIdForUpdate(81L, TENANT)).thenReturn(Optional.of(guia));
        when(movimientoRepo.existsByReferenciaAndTipoAndTenantId("GRE-81", "SALIDA", TENANT))
                .thenReturn(false);
        when(guiaRepo.save(any())).thenAnswer(inv -> inv.getArgument(0));

        ReconciliarGRERequest req = new ReconciliarGRERequest();
        req.setLucodeHash("hashRecon81");
        req.setXmlUrl("https://sandbox.lucode.pe/xml/T001-81.xml");
        req.setCdrUrl("https://sandbox.lucode.pe/cdr/T001-81.zip");

        service.reconciliarGREAceptada(81L, req);

        ArgumentCaptor<MovimientoInventario> captor = ArgumentCaptor.forClass(MovimientoInventario.class);
        verify(movimientoService, times(1)).crearMovimiento(captor.capture());
        assertThat(captor.getValue().getSucursalId()).isEqualTo(10L);
        assertThat(captor.getValue().getTipo()).isEqualTo("SALIDA");
    }

    // ── T-F10-14: Caso B — MovimientoInventario.sucursalId es null ────────────

    @Test
    @DisplayName("T-F10-14: Caso B — MovimientoInventario creado tiene sucursalId=null y tipo SALIDA")
    void casoB_movimientoInventario_sucursalIdNull() {
        GuiaRemision guia = buildBorradorDelivery(82L, null, null);

        when(guiaRepo.findByIdAndTenantIdForUpdate(82L, TENANT)).thenReturn(Optional.of(guia));
        when(tenantRepo.findByTenantId(TENANT)).thenReturn(Optional.of(buildTenantLegacyConDespacho()));
        when(sucursalRepo.countByTenantIdAndActivoTrue(TENANT)).thenReturn(0L);
        when(stockLoteService.getStockVigente(TENANT, 5L, null)).thenReturn(100);
        when(guiaRepo.saveAndFlush(any())).thenAnswer(inv -> inv.getArgument(0));
        when(lucodeGREService.emitirGRE(any(), any())).thenReturn(buildLucodeExito());
        when(guiaRepo.save(any())).thenAnswer(inv -> inv.getArgument(0));

        service.emitirGRE(82L);

        ArgumentCaptor<MovimientoInventario> captor = ArgumentCaptor.forClass(MovimientoInventario.class);
        verify(movimientoService).crearMovimiento(captor.capture());
        MovimientoInventario mov = captor.getValue();
        assertThat(mov.getSucursalId()).isNull();
        assertThat(mov.getTipo()).isEqualTo("SALIDA");
        assertThat(mov.getTenantId()).isEqualTo(TENANT);
        assertThat(mov.getReferencia()).isEqualTo("GRE-82");
        assertThat(mov.getCantidad()).isEqualTo(3);
    }
}
