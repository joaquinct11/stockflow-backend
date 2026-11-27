package com.stockflow.service.impl;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.stockflow.dto.gre.*;
import com.stockflow.entity.*;
import com.stockflow.entity.gre.*;
import com.stockflow.exception.BadRequestException;
import com.stockflow.repository.*;
import com.stockflow.service.MovimientoInventarioService;
import com.stockflow.service.StockLoteService;
import com.stockflow.util.TenantContext;
import org.junit.jupiter.api.*;
import org.junit.jupiter.api.extension.ExtendWith;
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
 * F-28: Unificar origen GRE con datos de sucursal y validar disponibilidad de sucursales destino.
 *
 * Tests:
 *  F28-1:  Tenant nuevo con sucursal: GRE DELIVERY usa ubigeo y dirección de la sucursal (preconfigurado en LucodeGREService)
 *  F28-2:  GRE entre sucursales: origen y destino con datos correctos → borrador creado
 *  F28-3:  GRE DELIVERY con sucursal origen → sucursal asociada en la guía
 *  F28-4:  Tenant legacy sin sucursales: usa configuración de despacho (campo sucursalOrigen null)
 *  F28-5:  Tenant con sucursales: NO usa ubigeoDespacho del tenant como origen (sucursal tiene prioridad)
 *  F28-6:  Sucursal destino de otro tenant: rechazada con BadRequest
 *  F28-7:  Origen y destino iguales: rechazado con BadRequest
 *  F28-8:  Tenant con una sucursal: no puede realizar traslado SUCURSALES
 *  F28-9:  Tenant sin sucursales activas: no puede realizar traslado SUCURSALES
 *  F28-10: Tenant con dos o más sucursales: puede crear GRE SUCURSALES
 *  F28-11: Tenant con una sucursal: puede crear GRE DELIVERY (no bloqueado por F-28)
 *  F28-12: Conteo de sucursales activas se verifica antes de resolver origen/destino
 */
@ExtendWith(MockitoExtension.class)
@DisplayName("F-28: Origen GRE con datos de sucursal y validación de disponibilidad")
class F28OrigenSucursalGRETest {

    private static final String TENANT  = "tenant-f28";
    private static final Long   USER_ID = 99L;

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
    @Mock ProductoStockSucursalRepository  stockSucursalRepo;
    @Mock ProductoVarianteRepository       varianteRepo;
    @Mock ProductoVarianteStockSucursalRepository varianteSucursalRepo;
    @Mock GreSerieContadorService          serieContadorService;

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
        return buildTenant(null, null);
    }

    private Tenant buildTenant(String ubigeoDespacho, String direccionDespacho) {
        Tenant t = new Tenant();
        t.setTenantId(TENANT);
        t.setSerieGuia("T001");
        t.setNombre("Negocio Test F28");
        t.setOseToken("tok-test");
        t.setUbigeoDespacho(ubigeoDespacho);
        t.setDireccionDespacho(direccionDespacho);
        return t;
    }

    private Sucursal buildSucursal(Long id, String nombre, String ubigeo, String direccionCompleta) {
        Sucursal s = new Sucursal();
        s.setId(id);
        s.setNombre(nombre);
        s.setTenantId(TENANT);
        s.setUbigeo(ubigeo);
        s.setDireccionCompleta(direccionCompleta);
        s.setActivo(true);
        return s;
    }

    private Producto buildProducto(Long id) {
        Producto p = new Producto();
        p.setId(id);
        p.setNombre("Producto F28");
        p.setTenantId(TENANT);
        p.setStockActual(50);
        return p;
    }

    private CrearGuiaRemisionRequest buildSucursalesRequest(Long origenId, Long destinoId) {
        GuiaRemisionItemDTO item = new GuiaRemisionItemDTO();
        item.setProductoId(5L);
        item.setCantidad(new BigDecimal("3"));
        item.setUnidadMedida("NIU");

        TransporteDTO transporte = new TransporteDTO();
        transporte.setDireccionPartida("Origen");
        transporte.setDireccionLlegada("Destino");

        CrearGuiaRemisionRequest req = new CrearGuiaRemisionRequest();
        req.setTipoTraslado(TipoTrasladoGRE.SUCURSALES);
        req.setMotivoTrasladoCodigo("04");
        req.setModalidadTransporte(ModalidadTransporteGRE.PRIVADO);
        req.setFechaTraslado(LocalDate.now());
        req.setSucursalOrigenId(origenId);
        req.setSucursalDestinoId(destinoId);
        req.setItems(List.of(item));
        req.setTransporte(transporte);
        return req;
    }

    private CrearGuiaRemisionRequest buildDeliveryRequest(Long origenId) {
        GuiaRemisionItemDTO item = new GuiaRemisionItemDTO();
        item.setProductoId(5L);
        item.setCantidad(new BigDecimal("1"));
        item.setUnidadMedida("NIU");

        TransporteDTO transporte = new TransporteDTO();
        transporte.setDireccionPartida("Origen");
        transporte.setDireccionLlegada("Destino del cliente");

        CrearGuiaRemisionRequest req = new CrearGuiaRemisionRequest();
        req.setTipoTraslado(TipoTrasladoGRE.DELIVERY);
        req.setMotivoTrasladoCodigo("01");
        req.setModalidadTransporte(ModalidadTransporteGRE.PRIVADO);
        req.setFechaTraslado(LocalDate.now());
        req.setSucursalOrigenId(origenId);
        req.setDestinatarioRazonSocial("Cliente F28");
        req.setDestinatarioTipoDoc("6");
        req.setDestinatarioNumDoc("20100000001");
        req.setItems(List.of(item));
        req.setTransporte(transporte);
        return req;
    }

    private void mockSaveGuia() {
        when(guiaRepo.save(any())).thenAnswer(inv -> {
            GuiaRemision g = inv.getArgument(0);
            if (g.getId() == null) g.setId(100L);
            return g;
        });
    }

    // ── F28-1: Tenant con sucursal — sucursal asociada en DELIVERY ────────────

    @Test
    @DisplayName("F28-1: Tenant con sucursal — sucursalOrigen queda asociada en la GRE DELIVERY")
    void delivery_conSucursalOrigen_sucursalAsociadaEnGuia() {
        Sucursal origen = buildSucursal(10L, "Tienda Lima", "150101", "Av. Lima 100");
        Producto producto = buildProducto(5L);

        when(guiaRepo.existsByIdempotencyKey(any())).thenReturn(false);
        when(tenantRepo.findByTenantId(TENANT)).thenReturn(Optional.of(buildTenant()));
        when(guiaRepo.findMaxCorrelativo(TENANT, "T001")).thenReturn(0);
        when(sucursalRepo.findByIdAndTenantId(10L, TENANT)).thenReturn(Optional.of(origen));
        when(productoRepo.findById(5L)).thenReturn(Optional.of(producto));
        mockSaveGuia();

        service.crearBorrador(buildDeliveryRequest(10L));

        verify(guiaRepo, atLeast(1)).save(argThat(g ->
                g.getSucursalOrigen() != null
                && g.getSucursalOrigen().getId().equals(10L)
                && "150101".equals(g.getSucursalOrigen().getUbigeo())
        ));
    }

    // ── F28-2: GRE entre sucursales con origen y destino correctos ────────────

    @Test
    @DisplayName("F28-2: GRE SUCURSALES con origen y destino válidos crea borrador")
    void sucursales_origenDestinoValidos_creaBorrador() {
        Sucursal origen  = buildSucursal(10L, "Tienda Lima",    "150101", "Av. Lima 100");
        Sucursal destino = buildSucursal(20L, "Tienda Arequipa","040101", "Av. Arequipa 200");
        Producto producto = buildProducto(5L);

        when(guiaRepo.existsByIdempotencyKey(any())).thenReturn(false);
        when(tenantRepo.findByTenantId(TENANT)).thenReturn(Optional.of(buildTenant()));
        when(guiaRepo.findMaxCorrelativo(TENANT, "T001")).thenReturn(0);
        when(sucursalRepo.countByTenantIdAndActivoTrue(TENANT)).thenReturn(2L);
        when(sucursalRepo.findByIdAndTenantId(10L, TENANT)).thenReturn(Optional.of(origen));
        when(sucursalRepo.findByIdAndTenantId(20L, TENANT)).thenReturn(Optional.of(destino));
        when(productoRepo.findById(5L)).thenReturn(Optional.of(producto));
        mockSaveGuia();

        assertThatNoException().isThrownBy(() -> service.crearBorrador(buildSucursalesRequest(10L, 20L)));

        verify(guiaRepo, atLeast(1)).save(argThat(g ->
                g.getSucursalOrigen() != null && g.getSucursalDestino() != null
                && g.getSucursalOrigen().getId().equals(10L)
                && g.getSucursalDestino().getId().equals(20L)
        ));
    }

    // ── F28-3: GRE DELIVERY con sucursal origen → sucursal en la guía ─────────

    @Test
    @DisplayName("F28-3: GRE DELIVERY independiente con sucursal origen asigna sucursalOrigen")
    void delivery_independiente_asignaSucursalOrigen() {
        Sucursal origen = buildSucursal(10L, "Almacén Central", "150101", "Jr. Almacén 1");
        Producto producto = buildProducto(5L);

        when(guiaRepo.existsByIdempotencyKey(any())).thenReturn(false);
        when(tenantRepo.findByTenantId(TENANT)).thenReturn(Optional.of(buildTenant()));
        when(guiaRepo.findMaxCorrelativo(TENANT, "T001")).thenReturn(0);
        when(sucursalRepo.findByIdAndTenantId(10L, TENANT)).thenReturn(Optional.of(origen));
        when(productoRepo.findById(5L)).thenReturn(Optional.of(producto));
        mockSaveGuia();

        service.crearBorrador(buildDeliveryRequest(10L));

        verify(guiaRepo, atLeast(1)).save(argThat(g ->
                g.getSucursalOrigen() != null
                && g.getSucursalOrigen().getUbigeo() != null
        ));
    }

    // ── F28-4: Tenant legacy sin sucursales → sucursalOrigen null en DELIVERY ──

    @Test
    @DisplayName("F28-4: Tenant legacy con punto de despacho — sucursalOrigen es null en DELIVERY con venta")
    void tenantLegacy_conVenta_sucursalOrigenNull() {
        Venta venta = new Venta();
        venta.setId(99L);
        venta.setTenantId(TENANT);

        Producto producto = buildProducto(5L);

        when(guiaRepo.existsByIdempotencyKey(any())).thenReturn(false);
        when(tenantRepo.findByTenantId(TENANT)).thenReturn(
                Optional.of(buildTenant("150101", "Av. Despacho Legacy 999")));
        when(guiaRepo.findMaxCorrelativo(TENANT, "T001")).thenReturn(0);
        when(ventaRepo.findById(99L)).thenReturn(Optional.of(venta));
        when(productoRepo.findById(5L)).thenReturn(Optional.of(producto));
        mockSaveGuia();

        CrearGuiaRemisionRequest req = buildDeliveryRequest(null);
        req.setVentaId(99L);
        req.setMotivoTrasladoCodigo("01");

        assertThatNoException().isThrownBy(() -> service.crearBorrador(req));

        verify(guiaRepo, atLeast(1)).save(argThat(g -> g.getSucursalOrigen() == null));
    }

    // ── F28-5: Sucursal tiene prioridad sobre tenant.ubigeoDespacho ───────────

    @Test
    @DisplayName("F28-5: Sucursal con ubigeo tiene prioridad; tenant.ubigeoDespacho no se usa como origen")
    void sucursal_tienePrioridadSobreTenantUbigeoDespacho() {
        // El tenant tiene ubigeoDespacho, pero la guía usará la sucursal de origen
        Sucursal origen = buildSucursal(10L, "Tienda Cusco", "130101", "Av. Cusco 500");
        Producto producto = buildProducto(5L);

        Tenant tenant = buildTenant("150101", "Av. Despacho Viejo 123"); // legacy
        when(guiaRepo.existsByIdempotencyKey(any())).thenReturn(false);
        when(tenantRepo.findByTenantId(TENANT)).thenReturn(Optional.of(tenant));
        when(guiaRepo.findMaxCorrelativo(TENANT, "T001")).thenReturn(0);
        when(sucursalRepo.findByIdAndTenantId(10L, TENANT)).thenReturn(Optional.of(origen));
        when(productoRepo.findById(5L)).thenReturn(Optional.of(producto));
        mockSaveGuia();

        service.crearBorrador(buildDeliveryRequest(10L));

        verify(guiaRepo, atLeast(1)).save(argThat(g ->
                g.getSucursalOrigen() != null
                && "130101".equals(g.getSucursalOrigen().getUbigeo())
        ));
    }

    // ── F28-6: Sucursal destino de otro tenant → rechazada ───────────────────

    @Test
    @DisplayName("F28-6: Sucursal destino de otro tenant es rechazada")
    void sucursalDestino_otroTenant_rechazada() {
        when(guiaRepo.existsByIdempotencyKey(any())).thenReturn(false);
        when(tenantRepo.findByTenantId(TENANT)).thenReturn(Optional.of(buildTenant()));
        when(guiaRepo.findMaxCorrelativo(TENANT, "T001")).thenReturn(0);
        when(sucursalRepo.countByTenantIdAndActivoTrue(TENANT)).thenReturn(2L);
        when(sucursalRepo.findByIdAndTenantId(10L, TENANT)).thenReturn(
                Optional.of(buildSucursal(10L, "Origen", "150101", "Av. Lima")));
        // La sucursal de destino (20L) no pertenece al tenant
        when(sucursalRepo.findByIdAndTenantId(20L, TENANT)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> service.crearBorrador(buildSucursalesRequest(10L, 20L)))
                .isInstanceOf(RuntimeException.class);

        verify(guiaRepo, never()).save(any());
    }

    // ── F28-7: Origen y destino iguales → rechazado ───────────────────────────

    @Test
    @DisplayName("F28-7: Origen y destino iguales en SUCURSALES son rechazados")
    void sucursales_origenDestinoIguales_rechazado() {
        Sucursal s = buildSucursal(10L, "Tienda Única", "150101", "Av. Única 1");

        when(guiaRepo.existsByIdempotencyKey(any())).thenReturn(false);
        when(tenantRepo.findByTenantId(TENANT)).thenReturn(Optional.of(buildTenant()));
        when(guiaRepo.findMaxCorrelativo(TENANT, "T001")).thenReturn(0);
        when(sucursalRepo.countByTenantIdAndActivoTrue(TENANT)).thenReturn(2L);
        when(sucursalRepo.findByIdAndTenantId(10L, TENANT)).thenReturn(Optional.of(s));

        assertThatThrownBy(() -> service.crearBorrador(buildSucursalesRequest(10L, 10L)))
                .isInstanceOf(BadRequestException.class)
                .hasMessageContaining("distintas");

        verify(guiaRepo, never()).save(any());
    }

    // ── F28-8: Tenant con una sucursal activa → no puede hacer SUCURSALES ─────

    @Test
    @DisplayName("F28-8: Tenant con una sucursal activa no puede hacer traslado SUCURSALES")
    void tenantConUnaSucursal_noPuedeSucursales() {
        when(guiaRepo.existsByIdempotencyKey(any())).thenReturn(false);
        when(tenantRepo.findByTenantId(TENANT)).thenReturn(Optional.of(buildTenant()));
        when(guiaRepo.findMaxCorrelativo(TENANT, "T001")).thenReturn(0);
        when(sucursalRepo.countByTenantIdAndActivoTrue(TENANT)).thenReturn(1L);

        assertThatThrownBy(() -> service.crearBorrador(buildSucursalesRequest(10L, 20L)))
                .isInstanceOf(BadRequestException.class)
                .hasMessageContaining("dos sucursales activas")
                .hasMessageContaining("1 sucursal(es)");

        verify(guiaRepo, never()).save(any());
    }

    // ── F28-9: Tenant sin sucursales activas → no puede hacer SUCURSALES ──────

    @Test
    @DisplayName("F28-9: Tenant sin sucursales activas no puede hacer traslado SUCURSALES")
    void tenantSinSucursales_noPuedeSucursales() {
        when(guiaRepo.existsByIdempotencyKey(any())).thenReturn(false);
        when(tenantRepo.findByTenantId(TENANT)).thenReturn(Optional.of(buildTenant()));
        when(guiaRepo.findMaxCorrelativo(TENANT, "T001")).thenReturn(0);
        when(sucursalRepo.countByTenantIdAndActivoTrue(TENANT)).thenReturn(0L);

        assertThatThrownBy(() -> service.crearBorrador(buildSucursalesRequest(10L, 20L)))
                .isInstanceOf(BadRequestException.class)
                .hasMessageContaining("dos sucursales activas")
                .hasMessageContaining("0 sucursal(es)");

        verify(guiaRepo, never()).save(any());
    }

    // ── F28-10: Tenant con dos sucursales → puede hacer SUCURSALES ───────────

    @Test
    @DisplayName("F28-10: Tenant con dos o más sucursales activas puede crear GRE SUCURSALES")
    void tenantConDosSucursales_puedeSucursales() {
        Sucursal origen  = buildSucursal(10L, "Almacén Norte",  "150101", "Av. Norte 1");
        Sucursal destino = buildSucursal(20L, "Almacén Sur",    "150110", "Av. Sur 1");
        Producto producto = buildProducto(5L);

        when(guiaRepo.existsByIdempotencyKey(any())).thenReturn(false);
        when(tenantRepo.findByTenantId(TENANT)).thenReturn(Optional.of(buildTenant()));
        when(guiaRepo.findMaxCorrelativo(TENANT, "T001")).thenReturn(0);
        when(sucursalRepo.countByTenantIdAndActivoTrue(TENANT)).thenReturn(2L);
        when(sucursalRepo.findByIdAndTenantId(10L, TENANT)).thenReturn(Optional.of(origen));
        when(sucursalRepo.findByIdAndTenantId(20L, TENANT)).thenReturn(Optional.of(destino));
        when(productoRepo.findById(5L)).thenReturn(Optional.of(producto));
        mockSaveGuia();

        assertThatNoException().isThrownBy(() -> service.crearBorrador(buildSucursalesRequest(10L, 20L)));
    }

    // ── F28-11: Tenant con una sucursal puede crear GRE DELIVERY ─────────────

    @Test
    @DisplayName("F28-11: Tenant con una sucursal puede crear GRE DELIVERY (F-28 no bloquea DELIVERY)")
    void tenantConUnaSucursal_puedeDELIVERY() {
        Sucursal origen = buildSucursal(10L, "Tienda Única", "150101", "Av. Única 1");
        Producto producto = buildProducto(5L);

        when(guiaRepo.existsByIdempotencyKey(any())).thenReturn(false);
        when(tenantRepo.findByTenantId(TENANT)).thenReturn(Optional.of(buildTenant()));
        when(guiaRepo.findMaxCorrelativo(TENANT, "T001")).thenReturn(0);
        when(sucursalRepo.findByIdAndTenantId(10L, TENANT)).thenReturn(Optional.of(origen));
        when(productoRepo.findById(5L)).thenReturn(Optional.of(producto));
        mockSaveGuia();

        // No se llama countByTenantIdAndActivoTrue para DELIVERY — no debería bloquearse
        assertThatNoException().isThrownBy(() -> service.crearBorrador(buildDeliveryRequest(10L)));

        verify(sucursalRepo, never()).countByTenantIdAndActivoTrue(TENANT);
    }

    // ── F28-12: Conteo verificado antes de resolver origen/destino ────────────

    @Test
    @DisplayName("F28-12: El conteo de sucursales activas se verifica antes de resolver origen/destino")
    void sucursales_conteoVerificadoAntesDeResolver() {
        when(guiaRepo.existsByIdempotencyKey(any())).thenReturn(false);
        when(tenantRepo.findByTenantId(TENANT)).thenReturn(Optional.of(buildTenant()));
        when(guiaRepo.findMaxCorrelativo(TENANT, "T001")).thenReturn(0);
        when(sucursalRepo.countByTenantIdAndActivoTrue(TENANT)).thenReturn(1L);

        assertThatThrownBy(() -> service.crearBorrador(buildSucursalesRequest(10L, 20L)))
                .isInstanceOf(BadRequestException.class);

        // findByIdAndTenantId nunca debe ser llamado cuando el conteo falla
        verify(sucursalRepo, never()).findByIdAndTenantId(anyLong(), any());
    }
}
