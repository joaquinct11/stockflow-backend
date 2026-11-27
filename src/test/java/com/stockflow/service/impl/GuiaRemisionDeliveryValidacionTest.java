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
 * Tests de validación F-09: una GRE DELIVERY sin venta asociada requiere sucursal de origen.
 *
 * Tests:
 *  T-DEL-1: DELIVERY sin venta y sin origen en crearBorrador → rechazado
 *  T-DEL-2: DELIVERY sin venta con origen en crearBorrador → permitido
 *  T-DEL-3: DELIVERY con venta en crearBorrador → conserva comportamiento (sin origen OK)
 *  T-DEL-4: SUCURSALES sin origen en crearBorrador → rechazado
 *  T-DEL-5: SUCURSALES sin destino en crearBorrador → rechazado
 *  T-DEL-6: Edición de borrador DELIVERY sin origen → rechazada
 *  T-DEL-7: Emisión de borrador DELIVERY sin origen → rechazada antes de Lucode
 *  T-DEL-8: No se generan movimientos de inventario cuando crearBorrador es rechazado
 *  T-DEL-9: DELIVERY independiente válido registra exactamente 1 SALIDA en emitirGRE
 */
@ExtendWith(MockitoExtension.class)
@DisplayName("GuiaRemisionService — Validación DELIVERY independiente (F-09)")
class GuiaRemisionDeliveryValidacionTest {

    private static final String TENANT  = "tenant-abc";
    private static final Long   USER_ID = 42L;

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
    }

    @AfterEach
    void tearDown() {
        TenantContext.clear();
    }

    // ── Helpers ───────────────────────────────────────────────────────────────

    private Tenant buildTenant() {
        Tenant t = new Tenant();
        t.setTenantId(TENANT);
        t.setSerieGuia("T001");
        t.setNombre("Empresa Test");
        t.setOseToken("tok-test");
        return t;
    }

    private Sucursal buildSucursal(Long id, String nombre) {
        Sucursal s = new Sucursal();
        s.setId(id);
        s.setNombre(nombre);
        return s;
    }

    private Producto buildProducto(Long id) {
        Producto p = new Producto();
        p.setId(id);
        p.setNombre("Producto Test");
        p.setTenantId(TENANT);
        p.setStockActual(100);
        return p;
    }

    private CrearGuiaRemisionRequest buildCrearRequest(TipoTrasladoGRE tipo,
                                                        Long sucursalOrigenId,
                                                        Long sucursalDestinoId,
                                                        Long ventaId) {
        GuiaRemisionItemDTO item = new GuiaRemisionItemDTO();
        item.setProductoId(5L);
        item.setCantidad(new BigDecimal("2"));
        item.setUnidadMedida("NIU");

        TransporteDTO transporte = new TransporteDTO();
        transporte.setDireccionPartida("Jr. Los Pinos 123");
        transporte.setDireccionLlegada("Av. Lima 456");

        CrearGuiaRemisionRequest req = new CrearGuiaRemisionRequest();
        req.setTipoTraslado(tipo);
        req.setMotivoTrasladoCodigo("04");
        req.setModalidadTransporte(ModalidadTransporteGRE.PRIVADO);
        req.setFechaTraslado(LocalDate.now());
        req.setSucursalOrigenId(sucursalOrigenId);
        req.setSucursalDestinoId(sucursalDestinoId);
        req.setVentaId(ventaId);
        req.setDestinatarioRazonSocial("Cliente Test");
        req.setItems(List.of(item));
        req.setTransporte(transporte);
        return req;
    }

    private GuiaRemision buildBorradorDelivery(Long id, Sucursal origen, Venta venta) {
        Producto producto = buildProducto(5L);
        GuiaRemisionItem item = GuiaRemisionItem.builder()
                .id(1L)
                .producto(producto)
                .descripcion("Producto Test")
                .cantidad(new BigDecimal("2"))
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
                .fechaTraslado(LocalDate.now())
                .sucursalOrigen(origen)
                .venta(venta)
                .estadoTributario(EstadoTributarioGRE.BORRADOR)
                .estadoLogistico(EstadoLogisticoGRE.PENDIENTE)
                .items(new java.util.ArrayList<>(List.of(item)))
                .build();

        item.setGuiaRemision(guia);
        return guia;
    }

    // ── T-DEL-1: DELIVERY sin venta y sin origen en crear → rechazado ─────────

    @Test
    @DisplayName("T-DEL-1: DELIVERY sin venta y sin origen es rechazado en crearBorrador (Caso A-fallido: tenant con sucursales)")
    void delivery_sinVenta_sinOrigen_rechazadoEnCrear() {
        when(guiaRepo.existsByIdempotencyKey(any())).thenReturn(false);
        when(tenantRepo.findByTenantId(TENANT)).thenReturn(Optional.of(buildTenant()));
        when(guiaRepo.findMaxCorrelativo(TENANT, "T001")).thenReturn(0);
        // Caso A-fallido: tenant tiene sucursales pero no se seleccionó origen
        when(sucursalRepo.countByTenantIdAndActivoTrue(TENANT)).thenReturn(1L);

        CrearGuiaRemisionRequest req = buildCrearRequest(TipoTrasladoGRE.DELIVERY, null, null, null);

        assertThatThrownBy(() -> service.crearBorrador(req))
                .isInstanceOf(BadRequestException.class)
                .hasMessageContaining("sucursal de origen");

        verify(guiaRepo, never()).save(any());
    }

    // ── T-DEL-2: DELIVERY sin venta con origen en crear → permitido ───────────

    @Test
    @DisplayName("T-DEL-2: DELIVERY sin venta con origen es aceptado en crearBorrador")
    void delivery_sinVenta_conOrigen_permitidoEnCrear() {
        Sucursal origen = buildSucursal(10L, "Almacén Central");
        Producto producto = buildProducto(5L);

        when(guiaRepo.existsByIdempotencyKey(any())).thenReturn(false);
        when(tenantRepo.findByTenantId(TENANT)).thenReturn(Optional.of(buildTenant()));
        when(guiaRepo.findMaxCorrelativo(TENANT, "T001")).thenReturn(0);
        when(sucursalRepo.findByIdAndTenantId(10L, TENANT)).thenReturn(Optional.of(origen));
        when(productoRepo.findById(5L)).thenReturn(Optional.of(producto));
        when(guiaRepo.save(any())).thenAnswer(inv -> {
            GuiaRemision g = inv.getArgument(0);
            if (g.getId() == null) g.setId(99L);
            return g;
        });

        CrearGuiaRemisionRequest req = buildCrearRequest(TipoTrasladoGRE.DELIVERY, 10L, null, null);

        assertThatNoException().isThrownBy(() -> service.crearBorrador(req));
        verify(guiaRepo, atLeast(1)).save(argThat(g -> g.getSucursalOrigen() != null));
    }

    // ── T-DEL-3: DELIVERY con venta → conserva comportamiento ────────────────

    @Test
    @DisplayName("T-DEL-3: DELIVERY con venta no requiere sucursal de origen")
    void delivery_conVenta_noRequiereOrigen() {
        Venta venta = new Venta();
        venta.setId(77L);
        venta.setTenantId(TENANT);

        Producto producto = buildProducto(5L);

        when(guiaRepo.existsByIdempotencyKey(any())).thenReturn(false);
        when(tenantRepo.findByTenantId(TENANT)).thenReturn(Optional.of(buildTenant()));
        when(guiaRepo.findMaxCorrelativo(TENANT, "T001")).thenReturn(0);
        when(ventaRepo.findById(77L)).thenReturn(Optional.of(venta));
        when(productoRepo.findById(5L)).thenReturn(Optional.of(producto));
        when(guiaRepo.save(any())).thenAnswer(inv -> {
            GuiaRemision g = inv.getArgument(0);
            if (g.getId() == null) g.setId(100L);
            return g;
        });

        // sucursalOrigenId=null, ventaId=77 → no se requiere origen
        CrearGuiaRemisionRequest req = buildCrearRequest(TipoTrasladoGRE.DELIVERY, null, null, 77L);

        assertThatNoException().isThrownBy(() -> service.crearBorrador(req));
        verify(guiaRepo, atLeast(1)).save(any());
    }

    // ── T-DEL-4: SUCURSALES sin origen → rechazado ────────────────────────────

    @Test
    @DisplayName("T-DEL-4: SUCURSALES sin sucursal de origen es rechazado en crearBorrador")
    void sucursales_sinOrigen_rechazado() {
        when(guiaRepo.existsByIdempotencyKey(any())).thenReturn(false);
        when(tenantRepo.findByTenantId(TENANT)).thenReturn(Optional.of(buildTenant()));
        when(guiaRepo.findMaxCorrelativo(TENANT, "T001")).thenReturn(0);
        when(sucursalRepo.countByTenantIdAndActivoTrue(TENANT)).thenReturn(2L);
        Sucursal destino = buildSucursal(20L, "Sucursal B");
        when(sucursalRepo.findByIdAndTenantId(20L, TENANT)).thenReturn(Optional.of(destino));

        // sucursalOrigenId=null, sucursalDestinoId=20 → requiere origen
        CrearGuiaRemisionRequest req = buildCrearRequest(TipoTrasladoGRE.SUCURSALES, null, 20L, null);

        assertThatThrownBy(() -> service.crearBorrador(req))
                .isInstanceOf(BadRequestException.class)
                .hasMessageContaining("sucursalOrigenId");

        verify(guiaRepo, never()).save(any());
    }

    // ── T-DEL-5: SUCURSALES sin destino → rechazado ───────────────────────────

    @Test
    @DisplayName("T-DEL-5: SUCURSALES sin sucursal de destino es rechazado en crearBorrador")
    void sucursales_sinDestino_rechazado() {
        when(guiaRepo.existsByIdempotencyKey(any())).thenReturn(false);
        when(tenantRepo.findByTenantId(TENANT)).thenReturn(Optional.of(buildTenant()));
        when(guiaRepo.findMaxCorrelativo(TENANT, "T001")).thenReturn(0);
        when(sucursalRepo.countByTenantIdAndActivoTrue(TENANT)).thenReturn(2L);
        Sucursal origen = buildSucursal(10L, "Almacén Central");
        when(sucursalRepo.findByIdAndTenantId(10L, TENANT)).thenReturn(Optional.of(origen));

        // sucursalOrigenId=10, sucursalDestinoId=null → requiere destino
        CrearGuiaRemisionRequest req = buildCrearRequest(TipoTrasladoGRE.SUCURSALES, 10L, null, null);

        assertThatThrownBy(() -> service.crearBorrador(req))
                .isInstanceOf(BadRequestException.class)
                .hasMessageContaining("sucursalOrigenId");

        verify(guiaRepo, never()).save(any());
    }

    // ── T-DEL-6: Edición de borrador DELIVERY sin origen → rechazada ──────────

    @Test
    @DisplayName("T-DEL-6: Editar borrador DELIVERY sin origen (Caso A-fallido: tenant con sucursales) es rechazado")
    void editar_deliveryBorradorSinOrigen_rechazado() {
        GuiaRemision guia = buildBorradorDelivery(55L, null, null); // sin origen, sin venta
        when(guiaRepo.findByIdAndTenantIdWithSucursales(55L, TENANT)).thenReturn(Optional.of(guia));
        // F-10: editarBorrador carga el tenant para pasar a validarOrigenDelivery
        when(tenantRepo.findByTenantId(TENANT)).thenReturn(Optional.of(buildTenant()));
        // Caso A-fallido: tenant tiene sucursales pero no se seleccionó origen
        when(sucursalRepo.countByTenantIdAndActivoTrue(TENANT)).thenReturn(1L);

        TransporteDTO td = new TransporteDTO();
        td.setDireccionPartida("Jr. Los Pinos 123");
        td.setDireccionLlegada("Av. Lima 456");
        GuiaRemisionItemDTO item = new GuiaRemisionItemDTO();
        item.setProductoId(5L);
        item.setCantidad(new BigDecimal("1"));
        item.setUnidadMedida("NIU");

        EditarBorradorRequest req = new EditarBorradorRequest();
        req.setModalidadTransporte(ModalidadTransporteGRE.PRIVADO);
        req.setFechaTraslado(LocalDate.now());
        req.setSucursalOrigenId(null); // no se asigna origen nuevo
        req.setItems(List.of(item));
        req.setTransporte(td);

        assertThatThrownBy(() -> service.editarBorrador(55L, req))
                .isInstanceOf(BadRequestException.class)
                .hasMessageContaining("sucursal de origen");

        verify(guiaRepo, never()).save(any());
    }

    // ── T-DEL-7: Emisión de borrador sin origen → rechazada antes de Lucode ───

    @Test
    @DisplayName("T-DEL-7: Emitir borrador DELIVERY sin origen (Caso A-fallido: tenant con sucursales) rechazado antes de Lucode")
    void emitir_borradorSinOrigen_rechazadoAntesLucode() {
        GuiaRemision guia = buildBorradorDelivery(55L, null, null);
        when(guiaRepo.findByIdAndTenantIdForUpdate(55L, TENANT)).thenReturn(Optional.of(guia));
        when(tenantRepo.findByTenantId(TENANT)).thenReturn(Optional.of(buildTenant()));
        // Caso A-fallido: tenant tiene sucursales pero no se seleccionó origen
        when(sucursalRepo.countByTenantIdAndActivoTrue(TENANT)).thenReturn(1L);

        assertThatThrownBy(() -> service.emitirGRE(55L))
                .isInstanceOf(BadRequestException.class)
                .hasMessageContaining("sucursal de origen");

        verify(lucodeGREService, never()).emitirGRE(any(), any());
        verify(guiaRepo, never()).saveAndFlush(any());
    }

    // ── T-DEL-8: No se generan movimientos ante rechazo en crearBorrador ──────

    @Test
    @DisplayName("T-DEL-8: No se generan movimientos de inventario cuando crearBorrador es rechazado por F-09")
    void noMovimientosSiCrearRechaza() {
        when(guiaRepo.existsByIdempotencyKey(any())).thenReturn(false);
        when(tenantRepo.findByTenantId(TENANT)).thenReturn(Optional.of(buildTenant()));
        when(guiaRepo.findMaxCorrelativo(TENANT, "T001")).thenReturn(0);

        CrearGuiaRemisionRequest req = buildCrearRequest(TipoTrasladoGRE.DELIVERY, null, null, null);

        assertThatThrownBy(() -> service.crearBorrador(req))
                .isInstanceOf(BadRequestException.class);

        verify(guiaRepo, never()).save(any());
        verify(movimientoService, never()).crearMovimiento(any());
    }

    // ── T-DEL-9: DELIVERY independiente válido registra salida una sola vez ───

    @Test
    @DisplayName("T-DEL-9: DELIVERY independiente con origen registra exactamente 1 SALIDA en emitirGRE")
    void emitir_deliveryIndependienteValido_registraUnaSalida() {
        Sucursal origen = buildSucursal(10L, "Almacén Central");
        GuiaRemision guia = buildBorradorDelivery(66L, origen, null);

        when(guiaRepo.findByIdAndTenantIdForUpdate(66L, TENANT)).thenReturn(Optional.of(guia));
        when(tenantRepo.findByTenantId(TENANT)).thenReturn(Optional.of(buildTenant()));
        when(stockLoteService.getStockVigente(TENANT, 5L, 10L)).thenReturn(50);
        when(guiaRepo.saveAndFlush(any())).thenAnswer(inv -> inv.getArgument(0));

        LucodeGREResponse lucodeResp = new LucodeGREResponse();
        lucodeResp.setSuccess(true);
        when(lucodeGREService.emitirGRE(any(), any())).thenReturn(lucodeResp);
        when(guiaRepo.save(any())).thenAnswer(inv -> inv.getArgument(0));

        service.emitirGRE(66L);

        // Exactamente 1 movimiento de SALIDA: 1 ítem, factor=1, cantidad=2
        ArgumentCaptor<MovimientoInventario> captor = ArgumentCaptor.forClass(MovimientoInventario.class);
        verify(movimientoService, times(1)).crearMovimiento(captor.capture());
        MovimientoInventario mov = captor.getValue();
        assertThat(mov.getTipo()).isEqualTo("SALIDA");
        assertThat(mov.getCantidad()).isEqualTo(2);
        assertThat(mov.getReferencia()).isEqualTo("GRE-66");
        assertThat(mov.getTenantId()).isEqualTo(TENANT);
    }
}
