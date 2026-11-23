package com.stockflow.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.stockflow.dto.gre.GuiaRemisionResponse;
import com.stockflow.dto.gre.lucode.LucodeGREPayload;
import com.stockflow.dto.gre.lucode.LucodeGREResponse;
import com.stockflow.entity.Producto;
import com.stockflow.entity.ProductoPresentacion;
import com.stockflow.entity.Sucursal;
import com.stockflow.entity.Tenant;
import com.stockflow.entity.Venta;
import com.stockflow.entity.gre.*;
import com.stockflow.exception.BadRequestException;
import com.stockflow.repository.*;
import com.stockflow.service.impl.GuiaRemisionPdfService;
import com.stockflow.service.impl.GuiaRemisionServiceImpl;
import com.stockflow.service.impl.LucodeGREService;
import com.stockflow.util.TenantContext;
import org.junit.jupiter.api.*;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.*;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
@DisplayName("GuiaRemision — validación de stock antes de emisión")
class GuiaRemisionStockValidationTest {

    private static final String TENANT_ID        = "negocio-test";
    private static final Long   GUIA_ID          = 1L;
    private static final Long   PRODUCTO_ID      = 10L;
    private static final Long   SUCURSAL_ID      = 20L;
    private static final Long   PRESENTACION_ID  = 100L;

    @Mock private GuiaRemisionRepository           guiaRepo;
    @Mock private GuiaRemisionEventoRepository     eventoRepo;
    @Mock private SucursalRepository               sucursalRepo;
    @Mock private ProductoRepository               productoRepo;
    @Mock private TenantRepository                 tenantRepo;
    @Mock private VentaRepository                  ventaRepo;
    @Mock private ProductoPresentacionRepository   presentacionRepo;
    @Mock private MovimientoInventarioService      movimientoService;
    @Mock private LucodeGREService                 lucodeGREService;
    @Mock private GuiaRemisionPdfService           pdfService;
    @Mock private StockLoteService                 stockLoteService;
    @Spy  private ObjectMapper                     objectMapper = new ObjectMapper();

    @InjectMocks private GuiaRemisionServiceImpl service;

    private static MockedStatic<TenantContext> tenantCtx;

    @BeforeAll
    static void setUpStatic() {
        tenantCtx = Mockito.mockStatic(TenantContext.class);
        tenantCtx.when(TenantContext::getCurrentTenant).thenReturn(TENANT_ID);
        tenantCtx.when(TenantContext::getCurrentUserId).thenReturn(1L);
    }

    @AfterAll
    static void tearDownStatic() {
        tenantCtx.close();
    }

    @BeforeEach
    void setUp() {
        Tenant tenant = Tenant.builder()
                .tenantId(TENANT_ID)
                .oseToken("tok_test")
                .serieGuia("T001")
                .build();
        when(tenantRepo.findByTenantId(TENANT_ID)).thenReturn(Optional.of(tenant));
        when(guiaRepo.saveAndFlush(any())).thenAnswer(inv -> inv.getArgument(0));
        when(guiaRepo.save(any())).thenAnswer(inv -> inv.getArgument(0));
        when(eventoRepo.save(any())).thenAnswer(inv -> inv.getArgument(0));
    }

    // ── Helpers ──────────────────────────────────────────────────────────────

    private Producto buildProducto() {
        return Producto.builder()
                .id(PRODUCTO_ID)
                .tenantId(TENANT_ID)
                .nombre("Paracetamol 500mg")
                .stockActual(50)
                .build();
    }

    private Sucursal buildSucursal() {
        return Sucursal.builder()
                .id(SUCURSAL_ID)
                .tenantId(TENANT_ID)
                .nombre("Tienda Principal")
                .build();
    }

    private GuiaRemision buildGuiaConItems(Producto producto, BigDecimal cantidad) {
        GuiaRemisionItem item = GuiaRemisionItem.builder()
                .producto(producto)
                .descripcion(producto != null ? producto.getNombre() : "Ítem sin producto")
                .cantidad(cantidad)
                .unidadMedida("NIU")
                .tenantId(TENANT_ID)
                .build();

        return GuiaRemision.builder()
                .id(GUIA_ID)
                .tenantId(TENANT_ID)
                .serie("T001")
                .correlativo(1)
                .tipoTraslado(TipoTrasladoGRE.SUCURSALES)
                .motivoTrasladoCodigo("01")
                .modalidadTransporte(ModalidadTransporteGRE.PRIVADO)
                .fechaTraslado(LocalDate.now())
                .estadoTributario(EstadoTributarioGRE.BORRADOR)
                .estadoLogistico(EstadoLogisticoGRE.PENDIENTE)
                .sucursalOrigen(buildSucursal())
                .items(new ArrayList<>(List.of(item)))
                .build();
    }

    private GuiaRemision buildGuiaConItemsConPresentacion(Producto producto, BigDecimal cantidad, Long presentacionId) {
        GuiaRemisionItem item = GuiaRemisionItem.builder()
                .producto(producto)
                .descripcion(producto != null ? producto.getNombre() : "Ítem")
                .cantidad(cantidad)
                .unidadMedida("NIU")
                .tenantId(TENANT_ID)
                .presentacionId(presentacionId)
                .build();

        return GuiaRemision.builder()
                .id(GUIA_ID)
                .tenantId(TENANT_ID)
                .serie("T001")
                .correlativo(1)
                .tipoTraslado(TipoTrasladoGRE.SUCURSALES)
                .motivoTrasladoCodigo("01")
                .modalidadTransporte(ModalidadTransporteGRE.PRIVADO)
                .fechaTraslado(LocalDate.now())
                .estadoTributario(EstadoTributarioGRE.BORRADOR)
                .estadoLogistico(EstadoLogisticoGRE.PENDIENTE)
                .sucursalOrigen(buildSucursal())
                .items(new ArrayList<>(List.of(item)))
                .build();
    }

    private ProductoPresentacion buildPresentacion(Long id, Producto producto, int factor) {
        return ProductoPresentacion.builder()
                .id(id)
                .producto(producto)
                .factor(factor)
                .tenantId(TENANT_ID)
                .build();
    }

    private LucodeGREResponse exitoLucode() {
        LucodeGREPayload payload = new LucodeGREPayload();
        payload.setHash("hash-ok");
        LucodeGREResponse resp = new LucodeGREResponse();
        resp.setSuccess(true);
        resp.setPayload(payload);
        return resp;
    }

    // ── Tests ────────────────────────────────────────────────────────────────

    @Test
    @DisplayName("GRE-STOCK-1: Stock vigente suficiente → emite y descuenta inventario")
    void stockSuficiente_emisionExitosa() {
        Producto prod = buildProducto();
        GuiaRemision guia = buildGuiaConItems(prod, BigDecimal.valueOf(5));
        when(guiaRepo.findByIdAndTenantIdForUpdate(GUIA_ID, TENANT_ID)).thenReturn(Optional.of(guia));
        when(stockLoteService.getStockVigente(TENANT_ID, PRODUCTO_ID, SUCURSAL_ID)).thenReturn(10);
        when(lucodeGREService.emitirGRE(any(), anyString())).thenReturn(exitoLucode());

        GuiaRemisionResponse resp = service.emitirGRE(GUIA_ID);

        assertThat(resp.getEstadoTributario()).isEqualTo(EstadoTributarioGRE.EMITIDA);
        verify(lucodeGREService).emitirGRE(any(), anyString());
        verify(movimientoService, atLeastOnce()).crearMovimiento(any());
    }

    @Test
    @DisplayName("GRE-STOCK-2: Stock vigente insuficiente → BadRequestException, Lucode no llamado")
    void stockInsuficiente_lanzaBadRequest() {
        Producto prod = buildProducto();
        GuiaRemision guia = buildGuiaConItems(prod, BigDecimal.valueOf(10));
        when(guiaRepo.findByIdAndTenantIdForUpdate(GUIA_ID, TENANT_ID)).thenReturn(Optional.of(guia));
        when(stockLoteService.getStockVigente(TENANT_ID, PRODUCTO_ID, SUCURSAL_ID)).thenReturn(3);

        assertThatThrownBy(() -> service.emitirGRE(GUIA_ID))
                .isInstanceOf(BadRequestException.class)
                .hasMessageContaining("Stock insuficiente");

        verifyNoInteractions(lucodeGREService);
        verifyNoInteractions(movimientoService);
    }

    @Test
    @DisplayName("GRE-STOCK-3: Cantidad = 0 → BadRequestException, Lucode no llamado")
    void cantidadCero_lanzaBadRequest() {
        Producto prod = buildProducto();
        GuiaRemision guia = buildGuiaConItems(prod, BigDecimal.ZERO);
        when(guiaRepo.findByIdAndTenantIdForUpdate(GUIA_ID, TENANT_ID)).thenReturn(Optional.of(guia));
        when(stockLoteService.getStockVigente(any(), any(), any())).thenReturn(100);

        assertThatThrownBy(() -> service.emitirGRE(GUIA_ID))
                .isInstanceOf(BadRequestException.class)
                .hasMessageContaining("mayor a cero");

        verifyNoInteractions(lucodeGREService);
    }

    @Test
    @DisplayName("GRE-STOCK-4: Cantidad negativa → BadRequestException, Lucode no llamado")
    void cantidadNegativa_lanzaBadRequest() {
        Producto prod = buildProducto();
        GuiaRemision guia = buildGuiaConItems(prod, BigDecimal.valueOf(-1));
        when(guiaRepo.findByIdAndTenantIdForUpdate(GUIA_ID, TENANT_ID)).thenReturn(Optional.of(guia));
        when(stockLoteService.getStockVigente(any(), any(), any())).thenReturn(100);

        assertThatThrownBy(() -> service.emitirGRE(GUIA_ID))
                .isInstanceOf(BadRequestException.class)
                .hasMessageContaining("mayor a cero");

        verifyNoInteractions(lucodeGREService);
    }

    @Test
    @DisplayName("GRE-STOCK-5: Caso D (DELIVERY con venta) → validación de stock omitida, Lucode llamado")
    void conVenta_saltaValidacionStock() {
        // F-10: la validación de stock se omite SOLO cuando hay venta asociada (POS ya lo hizo).
        // Antes (F-09) se omitía también sin sucursal — esa lógica fue reemplazada por F-10.
        Venta venta = new Venta();
        venta.setId(99L);
        venta.setTenantId(TENANT_ID);

        Producto prod = buildProducto();
        GuiaRemision guia = buildGuiaConItems(prod, BigDecimal.valueOf(1000));
        guia.setVenta(venta);        // Caso D: venta presente → stock ya descontó el POS
        guia.setSucursalOrigen(null); // sin sucursal (tenant legacy)
        when(guiaRepo.findByIdAndTenantIdForUpdate(GUIA_ID, TENANT_ID)).thenReturn(Optional.of(guia));
        when(lucodeGREService.emitirGRE(any(), anyString())).thenReturn(exitoLucode());

        GuiaRemisionResponse resp = service.emitirGRE(GUIA_ID);

        assertThat(resp.getEstadoTributario()).isEqualTo(EstadoTributarioGRE.EMITIDA);
        verifyNoInteractions(stockLoteService);
    }

    @Test
    @DisplayName("GRE-STOCK-6: Sin lotes registrados → usa stockActual del producto como fallback, OK")
    void sinLotes_usaStockActualFallback_exito() {
        Producto prod = buildProducto(); // stockActual = 50
        GuiaRemision guia = buildGuiaConItems(prod, BigDecimal.valueOf(20));
        when(guiaRepo.findByIdAndTenantIdForUpdate(GUIA_ID, TENANT_ID)).thenReturn(Optional.of(guia));
        when(stockLoteService.getStockVigente(TENANT_ID, PRODUCTO_ID, SUCURSAL_ID)).thenReturn(null);
        when(lucodeGREService.emitirGRE(any(), anyString())).thenReturn(exitoLucode());

        GuiaRemisionResponse resp = service.emitirGRE(GUIA_ID);

        assertThat(resp.getEstadoTributario()).isEqualTo(EstadoTributarioGRE.EMITIDA);
    }

    @Test
    @DisplayName("GRE-STOCK-7: Sin lotes y stockActual insuficiente → BadRequestException")
    void sinLotes_stockActualInsuficiente_lanzaBadRequest() {
        Producto prod = buildProducto(); // stockActual = 50
        GuiaRemision guia = buildGuiaConItems(prod, BigDecimal.valueOf(60));
        when(guiaRepo.findByIdAndTenantIdForUpdate(GUIA_ID, TENANT_ID)).thenReturn(Optional.of(guia));
        when(stockLoteService.getStockVigente(TENANT_ID, PRODUCTO_ID, SUCURSAL_ID)).thenReturn(null);

        assertThatThrownBy(() -> service.emitirGRE(GUIA_ID))
                .isInstanceOf(BadRequestException.class)
                .hasMessageContaining("Stock insuficiente");

        verifyNoInteractions(lucodeGREService);
    }

    @Test
    @DisplayName("GRE-STOCK-8: Stock de lote priorizado sobre stockActual: lote=2, cantidad=5 → BadRequestException")
    void stockLoteVigentePriorizadoSobreStockActual() {
        Producto prod = buildProducto(); // stockActual = 50 (alto, pero no se usa)
        GuiaRemision guia = buildGuiaConItems(prod, BigDecimal.valueOf(5));
        when(guiaRepo.findByIdAndTenantIdForUpdate(GUIA_ID, TENANT_ID)).thenReturn(Optional.of(guia));
        when(stockLoteService.getStockVigente(TENANT_ID, PRODUCTO_ID, SUCURSAL_ID)).thenReturn(2);

        assertThatThrownBy(() -> service.emitirGRE(GUIA_ID))
                .isInstanceOf(BadRequestException.class)
                .hasMessageContaining("Stock insuficiente");

        verifyNoInteractions(lucodeGREService);
    }

    @Test
    @DisplayName("GRE-STOCK-9: Cantidad exactamente igual al stock vigente → límite exacto OK")
    void cantidadExacta_stockJusto_emisionExitosa() {
        Producto prod = buildProducto();
        GuiaRemision guia = buildGuiaConItems(prod, BigDecimal.valueOf(5));
        when(guiaRepo.findByIdAndTenantIdForUpdate(GUIA_ID, TENANT_ID)).thenReturn(Optional.of(guia));
        when(stockLoteService.getStockVigente(TENANT_ID, PRODUCTO_ID, SUCURSAL_ID)).thenReturn(5);
        when(lucodeGREService.emitirGRE(any(), anyString())).thenReturn(exitoLucode());

        GuiaRemisionResponse resp = service.emitirGRE(GUIA_ID);

        assertThat(resp.getEstadoTributario()).isEqualTo(EstadoTributarioGRE.EMITIDA);
    }

    @Test
    @DisplayName("GRE-STOCK-10: Múltiples ítems — segundo sin stock → BadRequestException con nombre del producto")
    void multiplesItems_segundoSinStock_lanzaBadRequest() {
        Producto prod1 = Producto.builder().id(10L).tenantId(TENANT_ID).nombre("Producto A").stockActual(50).build();
        Producto prod2 = Producto.builder().id(11L).tenantId(TENANT_ID).nombre("Producto B").stockActual(50).build();

        GuiaRemisionItem item1 = GuiaRemisionItem.builder()
                .producto(prod1).descripcion("A").cantidad(BigDecimal.valueOf(5))
                .unidadMedida("NIU").tenantId(TENANT_ID).build();
        GuiaRemisionItem item2 = GuiaRemisionItem.builder()
                .producto(prod2).descripcion("B").cantidad(BigDecimal.valueOf(10))
                .unidadMedida("NIU").tenantId(TENANT_ID).build();

        GuiaRemision guia = GuiaRemision.builder()
                .id(GUIA_ID).tenantId(TENANT_ID).serie("T001").correlativo(1)
                .tipoTraslado(TipoTrasladoGRE.SUCURSALES).motivoTrasladoCodigo("01")
                .modalidadTransporte(ModalidadTransporteGRE.PRIVADO).fechaTraslado(LocalDate.now())
                .estadoTributario(EstadoTributarioGRE.BORRADOR).estadoLogistico(EstadoLogisticoGRE.PENDIENTE)
                .sucursalOrigen(buildSucursal())
                .items(new ArrayList<>(List.of(item1, item2)))
                .build();

        when(guiaRepo.findByIdAndTenantIdForUpdate(GUIA_ID, TENANT_ID)).thenReturn(Optional.of(guia));
        when(stockLoteService.getStockVigente(TENANT_ID, 10L, SUCURSAL_ID)).thenReturn(20);
        when(stockLoteService.getStockVigente(TENANT_ID, 11L, SUCURSAL_ID)).thenReturn(3);

        assertThatThrownBy(() -> service.emitirGRE(GUIA_ID))
                .isInstanceOf(BadRequestException.class)
                .hasMessageContaining("Producto B");

        verifyNoInteractions(lucodeGREService);
    }

    @Test
    @DisplayName("GRE-STOCK-11: Múltiples ítems con stock suficiente → todos pasan validación, Lucode llamado")
    void multiplesItems_todosConStock_emisionExitosa() {
        Producto prod1 = Producto.builder().id(10L).tenantId(TENANT_ID).nombre("Producto A").stockActual(50).build();
        Producto prod2 = Producto.builder().id(11L).tenantId(TENANT_ID).nombre("Producto B").stockActual(50).build();

        GuiaRemisionItem item1 = GuiaRemisionItem.builder()
                .producto(prod1).descripcion("A").cantidad(BigDecimal.valueOf(5))
                .unidadMedida("NIU").tenantId(TENANT_ID).build();
        GuiaRemisionItem item2 = GuiaRemisionItem.builder()
                .producto(prod2).descripcion("B").cantidad(BigDecimal.valueOf(3))
                .unidadMedida("NIU").tenantId(TENANT_ID).build();

        GuiaRemision guia = GuiaRemision.builder()
                .id(GUIA_ID).tenantId(TENANT_ID).serie("T001").correlativo(1)
                .tipoTraslado(TipoTrasladoGRE.SUCURSALES).motivoTrasladoCodigo("01")
                .modalidadTransporte(ModalidadTransporteGRE.PRIVADO).fechaTraslado(LocalDate.now())
                .estadoTributario(EstadoTributarioGRE.BORRADOR).estadoLogistico(EstadoLogisticoGRE.PENDIENTE)
                .sucursalOrigen(buildSucursal())
                .items(new ArrayList<>(List.of(item1, item2)))
                .build();

        when(guiaRepo.findByIdAndTenantIdForUpdate(GUIA_ID, TENANT_ID)).thenReturn(Optional.of(guia));
        when(stockLoteService.getStockVigente(TENANT_ID, 10L, SUCURSAL_ID)).thenReturn(20);
        when(stockLoteService.getStockVigente(TENANT_ID, 11L, SUCURSAL_ID)).thenReturn(10);
        when(lucodeGREService.emitirGRE(any(), anyString())).thenReturn(exitoLucode());

        GuiaRemisionResponse resp = service.emitirGRE(GUIA_ID);

        assertThat(resp.getEstadoTributario()).isEqualTo(EstadoTributarioGRE.EMITIDA);
        verify(lucodeGREService).emitirGRE(any(), anyString());
    }

    @Test
    @DisplayName("GRE-STOCK-12: Producto pertenece a otro tenant → BadRequestException, Lucode no llamado")
    void productoOtroTenant_lanzaBadRequest() {
        Producto prod = Producto.builder()
                .id(PRODUCTO_ID).tenantId("otro-tenant").nombre("Producto X").stockActual(100).build();
        GuiaRemision guia = buildGuiaConItems(prod, BigDecimal.valueOf(5));
        when(guiaRepo.findByIdAndTenantIdForUpdate(GUIA_ID, TENANT_ID)).thenReturn(Optional.of(guia));

        assertThatThrownBy(() -> service.emitirGRE(GUIA_ID))
                .isInstanceOf(BadRequestException.class)
                .hasMessageContaining("tenant");

        verifyNoInteractions(lucodeGREService);
        verifyNoInteractions(stockLoteService);
    }

    @Test
    @DisplayName("GRE-STOCK-13: Ítem sin producto (null) → BadRequestException, Lucode no llamado")
    void productoNull_lanzaBadRequest() {
        GuiaRemision guia = buildGuiaConItems(null, BigDecimal.valueOf(5));
        when(guiaRepo.findByIdAndTenantIdForUpdate(GUIA_ID, TENANT_ID)).thenReturn(Optional.of(guia));

        assertThatThrownBy(() -> service.emitirGRE(GUIA_ID))
                .isInstanceOf(BadRequestException.class)
                .hasMessageContaining("tenant");

        verifyNoInteractions(lucodeGREService);
        verifyNoInteractions(stockLoteService);
    }

    // ── Presentaciones farmacéuticas ─────────────────────────────────────────

    @Test
    @DisplayName("GRE-PRES-1: Factor=1 → validación equivalente a sin presentación, stock=10 qty=5 → OK")
    void presentacion_factorUno_validaStockDirectamente() {
        Producto prod = buildProducto();
        GuiaRemision guia = buildGuiaConItemsConPresentacion(prod, BigDecimal.valueOf(5), PRESENTACION_ID);
        ProductoPresentacion pres = buildPresentacion(PRESENTACION_ID, prod, 1);

        when(guiaRepo.findByIdAndTenantIdForUpdate(GUIA_ID, TENANT_ID)).thenReturn(Optional.of(guia));
        when(presentacionRepo.findById(PRESENTACION_ID)).thenReturn(Optional.of(pres));
        when(stockLoteService.getStockVigente(TENANT_ID, PRODUCTO_ID, SUCURSAL_ID)).thenReturn(10);
        when(lucodeGREService.emitirGRE(any(), anyString())).thenReturn(exitoLucode());

        GuiaRemisionResponse resp = service.emitirGRE(GUIA_ID);

        assertThat(resp.getEstadoTributario()).isEqualTo(EstadoTributarioGRE.EMITIDA);
        verify(movimientoService, atLeastOnce()).crearMovimiento(any());
    }

    @Test
    @DisplayName("GRE-PRES-2: Factor=10, qty=3 → requiere 30 unidades base, stock=35 → OK")
    void presentacion_factorDiez_cantidadTres_validaTreinta() {
        Producto prod = buildProducto();
        GuiaRemision guia = buildGuiaConItemsConPresentacion(prod, BigDecimal.valueOf(3), PRESENTACION_ID);
        ProductoPresentacion pres = buildPresentacion(PRESENTACION_ID, prod, 10);

        when(guiaRepo.findByIdAndTenantIdForUpdate(GUIA_ID, TENANT_ID)).thenReturn(Optional.of(guia));
        when(presentacionRepo.findById(PRESENTACION_ID)).thenReturn(Optional.of(pres));
        when(stockLoteService.getStockVigente(TENANT_ID, PRODUCTO_ID, SUCURSAL_ID)).thenReturn(35);
        when(lucodeGREService.emitirGRE(any(), anyString())).thenReturn(exitoLucode());

        GuiaRemisionResponse resp = service.emitirGRE(GUIA_ID);

        assertThat(resp.getEstadoTributario()).isEqualTo(EstadoTributarioGRE.EMITIDA);
        // Verifica que se registró salida de 30 unidades base (3 × 10)
        verify(movimientoService, atLeastOnce()).crearMovimiento(
                argThat(m -> m.getCantidad() != null && m.getCantidad() == 30));
    }

    @Test
    @DisplayName("GRE-PRES-3: Factor=10, qty=3 → requiere 30 unidades base, stock=25 → BadRequestException")
    void presentacion_factorDiez_stockInsuficiente_rechaza() {
        Producto prod = buildProducto();
        GuiaRemision guia = buildGuiaConItemsConPresentacion(prod, BigDecimal.valueOf(3), PRESENTACION_ID);
        ProductoPresentacion pres = buildPresentacion(PRESENTACION_ID, prod, 10);

        when(guiaRepo.findByIdAndTenantIdForUpdate(GUIA_ID, TENANT_ID)).thenReturn(Optional.of(guia));
        when(presentacionRepo.findById(PRESENTACION_ID)).thenReturn(Optional.of(pres));
        when(stockLoteService.getStockVigente(TENANT_ID, PRODUCTO_ID, SUCURSAL_ID)).thenReturn(25);

        assertThatThrownBy(() -> service.emitirGRE(GUIA_ID))
                .isInstanceOf(BadRequestException.class)
                .hasMessageContaining("Stock insuficiente");

        verifyNoInteractions(lucodeGREService);
        verifyNoInteractions(movimientoService);
    }

    @Test
    @DisplayName("GRE-PRES-4: presentacionId inexistente → BadRequestException, Lucode no llamado")
    void presentacion_idInexistente_lanzaException() {
        Producto prod = buildProducto();
        GuiaRemision guia = buildGuiaConItemsConPresentacion(prod, BigDecimal.valueOf(5), 999L);

        when(guiaRepo.findByIdAndTenantIdForUpdate(GUIA_ID, TENANT_ID)).thenReturn(Optional.of(guia));
        when(presentacionRepo.findById(999L)).thenReturn(Optional.empty());
        when(stockLoteService.getStockVigente(any(), any(), any())).thenReturn(100);

        assertThatThrownBy(() -> service.emitirGRE(GUIA_ID))
                .isInstanceOf(BadRequestException.class)
                .hasMessageContaining("Presentación inválida");

        verifyNoInteractions(lucodeGREService);
    }

    @Test
    @DisplayName("GRE-PRES-5: presentación pertenece a otro producto → BadRequestException")
    void presentacion_perteneceAOtroProducto_lanzaException() {
        Producto prod = buildProducto();
        Producto otroProducto = Producto.builder().id(999L).tenantId(TENANT_ID).nombre("Otro").build();
        GuiaRemision guia = buildGuiaConItemsConPresentacion(prod, BigDecimal.valueOf(5), PRESENTACION_ID);
        ProductoPresentacion presDeOtroProducto = buildPresentacion(PRESENTACION_ID, otroProducto, 10);

        when(guiaRepo.findByIdAndTenantIdForUpdate(GUIA_ID, TENANT_ID)).thenReturn(Optional.of(guia));
        when(presentacionRepo.findById(PRESENTACION_ID)).thenReturn(Optional.of(presDeOtroProducto));
        when(stockLoteService.getStockVigente(any(), any(), any())).thenReturn(100);

        assertThatThrownBy(() -> service.emitirGRE(GUIA_ID))
                .isInstanceOf(BadRequestException.class)
                .hasMessageContaining("Presentación inválida");

        verifyNoInteractions(lucodeGREService);
    }

    @Test
    @DisplayName("GRE-PRES-6: presentación pertenece a otro tenant → BadRequestException")
    void presentacion_perteneceAOtroTenant_lanzaException() {
        Producto prod = buildProducto();
        GuiaRemision guia = buildGuiaConItemsConPresentacion(prod, BigDecimal.valueOf(5), PRESENTACION_ID);
        ProductoPresentacion presOtroTenant = ProductoPresentacion.builder()
                .id(PRESENTACION_ID)
                .producto(prod)
                .factor(10)
                .tenantId("otro-tenant")
                .build();

        when(guiaRepo.findByIdAndTenantIdForUpdate(GUIA_ID, TENANT_ID)).thenReturn(Optional.of(guia));
        when(presentacionRepo.findById(PRESENTACION_ID)).thenReturn(Optional.of(presOtroTenant));
        when(stockLoteService.getStockVigente(any(), any(), any())).thenReturn(100);

        assertThatThrownBy(() -> service.emitirGRE(GUIA_ID))
                .isInstanceOf(BadRequestException.class)
                .hasMessageContaining("Presentación inválida");

        verifyNoInteractions(lucodeGREService);
    }

    @Test
    @DisplayName("GRE-PRES-7: Cantidad cero o negativa con presentacionId → BadRequestException (antes de consultar presentación)")
    void presentacion_cantidadCeroONegativa_rechaza() {
        Producto prod = buildProducto();

        GuiaRemision guiaCero = buildGuiaConItemsConPresentacion(prod, BigDecimal.ZERO, PRESENTACION_ID);
        when(guiaRepo.findByIdAndTenantIdForUpdate(GUIA_ID, TENANT_ID)).thenReturn(Optional.of(guiaCero));
        when(stockLoteService.getStockVigente(any(), any(), any())).thenReturn(100);

        assertThatThrownBy(() -> service.emitirGRE(GUIA_ID))
                .isInstanceOf(BadRequestException.class)
                .hasMessageContaining("mayor a cero");

        GuiaRemision guiaNegativa = buildGuiaConItemsConPresentacion(prod, BigDecimal.valueOf(-1), PRESENTACION_ID);
        when(guiaRepo.findByIdAndTenantIdForUpdate(GUIA_ID, TENANT_ID)).thenReturn(Optional.of(guiaNegativa));

        assertThatThrownBy(() -> service.emitirGRE(GUIA_ID))
                .isInstanceOf(BadRequestException.class)
                .hasMessageContaining("mayor a cero");

        // presentacionRepo nunca debe consultarse: la validación falla antes
        verifyNoInteractions(presentacionRepo);
        verifyNoInteractions(lucodeGREService);
    }

    // ── Respuesta 422 de Lucode ───────────────────────────────────────────────

    @Test
    @DisplayName("GRE-422-1: Lucode lanza BadRequestException (422) → no se descuenta inventario")
    void lucodeRespuesta422_noDescontaInventario() {
        Producto prod = buildProducto();
        GuiaRemision guia = buildGuiaConItems(prod, BigDecimal.valueOf(5));
        when(guiaRepo.findByIdAndTenantIdForUpdate(GUIA_ID, TENANT_ID)).thenReturn(Optional.of(guia));
        when(stockLoteService.getStockVigente(TENANT_ID, PRODUCTO_ID, SUCURSAL_ID)).thenReturn(10);
        when(lucodeGREService.emitirGRE(any(), anyString()))
                .thenThrow(new BadRequestException("Lucode GRE: horaDeEmision: formato H:i:s requerido | " +
                        "puntoDePartida.ubigeo: requerido | puntoDeLlegada.ubigeo: requerido | " +
                        "destinatarioDireccion: requerido"));

        assertThatThrownBy(() -> service.emitirGRE(GUIA_ID))
                .isInstanceOf(BadRequestException.class);

        verifyNoInteractions(movimientoService);
    }

    @Test
    @DisplayName("GRE-422-2: Lucode lanza BadRequestException → guía queda en estado ERROR, no EMITIENDO")
    void lucodeRespuesta422_estadoError() {
        Producto prod = buildProducto();
        GuiaRemision guia = buildGuiaConItems(prod, BigDecimal.valueOf(5));
        when(guiaRepo.findByIdAndTenantIdForUpdate(GUIA_ID, TENANT_ID)).thenReturn(Optional.of(guia));
        when(stockLoteService.getStockVigente(TENANT_ID, PRODUCTO_ID, SUCURSAL_ID)).thenReturn(10);
        when(lucodeGREService.emitirGRE(any(), anyString()))
                .thenThrow(new BadRequestException("Lucode GRE: validación fallida"));

        assertThatThrownBy(() -> service.emitirGRE(GUIA_ID))
                .isInstanceOf(BadRequestException.class);

        assertThat(guia.getEstadoTributario()).isEqualTo(EstadoTributarioGRE.ERROR);
    }

    // ── Doble descuento DELIVERY ──────────────────────────────────────────────

    @Test
    @DisplayName("GRE-DELIVERY-1: GRE DELIVERY vinculada a Venta → NO registra salida de inventario (evita doble descuento)")
    void deliveryVinculadaAVenta_noRegistraSalidaInventario() {
        Producto prod = buildProducto();
        GuiaRemision guia = buildGuiaConItems(prod, BigDecimal.valueOf(5));
        Venta venta = new Venta();
        venta.setId(100L);
        guia.setVenta(venta);

        when(guiaRepo.findByIdAndTenantIdForUpdate(GUIA_ID, TENANT_ID)).thenReturn(Optional.of(guia));
        when(lucodeGREService.emitirGRE(any(), anyString())).thenReturn(exitoLucode());

        GuiaRemisionResponse resp = service.emitirGRE(GUIA_ID);

        assertThat(resp.getEstadoTributario()).isEqualTo(EstadoTributarioGRE.EMITIDA);
        // El POS ya descontó inventario; GRE no debe volver a hacerlo
        verifyNoInteractions(movimientoService);
    }
}
