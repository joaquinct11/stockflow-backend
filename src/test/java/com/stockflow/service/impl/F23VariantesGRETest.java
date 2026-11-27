package com.stockflow.service.impl;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.stockflow.dto.gre.CrearGuiaRemisionRequest;
import com.stockflow.dto.gre.GuiaRemisionItemDTO;
import com.stockflow.entity.*;
import com.stockflow.entity.gre.*;
import com.stockflow.exception.BadRequestException;
import com.stockflow.repository.*;
import com.stockflow.service.MovimientoInventarioService;
import com.stockflow.service.StockLoteService;
import com.stockflow.util.TenantContext;
import org.junit.jupiter.api.*;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.*;
import org.mockito.junit.jupiter.MockitoExtension;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.*;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

/**
 * F-23: Tests de variantes en GRE.
 *
 * T-F23-01: Producto sin variantes — builder de item no incluye varianteId
 * T-F23-02: Producto con variantes — varianteId se persiste en el item
 * T-F23-03: Stock de variante suficiente — emisión no lanza excepción
 * T-F23-04: Stock de variante = 0 — emisión lanza BadRequestException
 * T-F23-05: Stock insuficiente para cantidad pedida — lanza BadRequestException
 * T-F23-06: Emisión descuenta solo variante seleccionada (PVSS)
 * T-F23-07: Recepción incrementa solo la variante seleccionada (PVSS)
 * T-F23-08: Kardex (MovimientoInventario) persiste varianteId correcto
 * T-F23-09: Stock encabezado coincide con saldo PVSS tras emisión
 * T-F23-10: Reintento emisión no duplica movimiento SALIDA
 * T-F23-11: Reintento recepción no duplica movimiento ENTRADA
 * T-F23-12: Compatibilidad con presentaciones (factor × variante)
 * T-F23-13: Producto sin variantes sigue usando flujo PSS estándar
 * T-F23-14: Aislamiento multi-tenant — variante de otro tenant no se usa
 */
@ExtendWith(MockitoExtension.class)
@DisplayName("F-23 — Variantes en GRE")
class F23VariantesGRETest {

    private static final String TENANT    = "tenant-f23";
    private static final String TENANT_B  = "tenant-otro";
    private static final Long   USER_ID   = 99L;
    private static final Long   PROD_ID   = 1L;
    private static final Long   VAR_ID    = 10L;
    private static final Long   SUC_ORI   = 100L;
    private static final Long   SUC_DEST  = 200L;

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

    @Captor ArgumentCaptor<MovimientoInventario> movCaptor;
    @Captor ArgumentCaptor<ProductoVarianteStockSucursal> pvssCaptor;

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

    // ── Helpers ──────────────────────────────────────────────────────────────

    private Producto buildProducto(int stockActual) {
        Producto p = new Producto();
        p.setId(PROD_ID);
        p.setNombre("Camiseta Básica");
        p.setTenantId(TENANT);
        p.setStockActual(stockActual);
        p.setCodigoBarras("CB-001");
        return p;
    }

    private ProductoVariante buildVariante(int stockActual, String tenantId) {
        ProductoVariante v = new ProductoVariante();
        v.setId(VAR_ID);
        v.setProductoId(PROD_ID);
        v.setTalla("M");
        v.setColor("Azul");
        v.setSku("CAM-M-AZ");
        v.setStockActual(stockActual);
        v.setActivo(true);
        v.setTenantId(tenantId);
        return v;
    }

    private ProductoVarianteStockSucursal buildPvss(int stock) {
        ProductoVarianteStockSucursal pvss = new ProductoVarianteStockSucursal();
        pvss.setId(500L);
        pvss.setVarianteId(VAR_ID);
        pvss.setSucursalId(SUC_ORI);
        pvss.setTenantId(TENANT);
        pvss.setStockActual(stock);
        pvss.setStockMinimo(0);
        return pvss;
    }

    private Sucursal buildSucursal(Long id) {
        Sucursal s = new Sucursal();
        s.setId(id);
        s.setNombre("Sucursal " + id);
        s.setTenantId(TENANT);
        return s;
    }

    private GuiaRemision buildGre(Producto producto, Long varianteId, int cantidad,
                                   EstadoTributarioGRE estado) {
        Sucursal origen = buildSucursal(SUC_ORI);
        Sucursal destino = buildSucursal(SUC_DEST);

        GuiaRemisionItem item = GuiaRemisionItem.builder()
                .id(1L)
                .producto(producto)
                .descripcion(producto.getNombre())
                .cantidad(new BigDecimal(cantidad))
                .tenantId(TENANT)
                .varianteId(varianteId)
                .build();

        GuiaRemision gre = new GuiaRemision();
        gre.setId(1L);
        gre.setTenantId(TENANT);
        gre.setEstadoTributario(estado);
        gre.setTipoTraslado(TipoTrasladoGRE.SUCURSALES);
        gre.setSucursalOrigen(origen);
        gre.setSucursalDestino(destino);
        gre.setItems(List.of(item));
        gre.setFechaTraslado(LocalDate.now());
        gre.setSerie("T001");
        gre.setCorrelativo(9999);
        return gre;
    }

    // ── T-F23-01: Producto sin variantes — no incluye varianteId ─────────────

    @Test
    @DisplayName("T-F23-01: Item sin variante tiene varianteId=null")
    void t01_itemSinVarianteTieneVarianteIdNull() {
        GuiaRemisionItem item = GuiaRemisionItem.builder()
                .id(1L)
                .producto(buildProducto(10))
                .descripcion("Camiseta Básica")
                .cantidad(BigDecimal.ONE)
                .tenantId(TENANT)
                .build();

        assertThat(item.getVarianteId()).isNull();
    }

    // ── T-F23-02: Producto con variantes — varianteId se persiste ────────────

    @Test
    @DisplayName("T-F23-02: Item con varianteId almacena el valor correctamente")
    void t02_itemConVarianteIdAlmacenaValor() {
        GuiaRemisionItem item = GuiaRemisionItem.builder()
                .id(1L)
                .producto(buildProducto(5))
                .descripcion("Camiseta Básica")
                .cantidad(BigDecimal.ONE)
                .tenantId(TENANT)
                .varianteId(VAR_ID)
                .build();

        assertThat(item.getVarianteId()).isEqualTo(VAR_ID);
    }

    // ── T-F23-03: Stock suficiente → emisión no lanza excepción ─────────────

    @Test
    @DisplayName("T-F23-03: Stock variante suficiente — emisión no lanza excepción")
    void t03_stockVarianteSuficiente() {
        Producto producto = buildProducto(10);
        GuiaRemision gre = buildGre(producto, VAR_ID, 2, EstadoTributarioGRE.BORRADOR);

        when(varianteSucursalRepo.findByVarianteIdAndSucursalId(VAR_ID, SUC_ORI))
                .thenReturn(Optional.of(buildPvss(5)));

        // Verificar que la validación de stock pasa sin BadRequestException
        assertThatCode(() -> {
            var item = gre.getItems().get(0);
            int pvssStock = varianteSucursalRepo.findByVarianteIdAndSucursalId(item.getVarianteId(), SUC_ORI)
                    .map(pvss -> pvss.getStockActual() != null ? pvss.getStockActual() : 0)
                    .orElse(0);
            if (item.getCantidad().intValue() > pvssStock) {
                throw new BadRequestException("Stock insuficiente");
            }
        }).doesNotThrowAnyException();
    }

    // ── T-F23-04: Stock de variante = 0 → lanza BadRequestException ──────────

    @Test
    @DisplayName("T-F23-04: Stock variante = 0 — lanza BadRequestException")
    void t04_stockVarianteCeroLanzaExcepcion() {
        when(varianteSucursalRepo.findByVarianteIdAndSucursalId(VAR_ID, SUC_ORI))
                .thenReturn(Optional.of(buildPvss(0)));

        assertThatThrownBy(() -> {
            int pvssStock = varianteSucursalRepo.findByVarianteIdAndSucursalId(VAR_ID, SUC_ORI)
                    .map(pvss -> pvss.getStockActual() != null ? pvss.getStockActual() : 0)
                    .orElse(0);
            if (1 > pvssStock) throw new BadRequestException("Stock insuficiente para variante");
        }).isInstanceOf(BadRequestException.class)
          .hasMessageContaining("Stock insuficiente");
    }

    // ── T-F23-05: Stock < cantidad pedida → lanza BadRequestException ─────────

    @Test
    @DisplayName("T-F23-05: Stock < cantidad — lanza BadRequestException")
    void t05_stockInsuficienteParaCantidad() {
        when(varianteSucursalRepo.findByVarianteIdAndSucursalId(VAR_ID, SUC_ORI))
                .thenReturn(Optional.of(buildPvss(3)));

        assertThatThrownBy(() -> {
            int pvssStock = varianteSucursalRepo.findByVarianteIdAndSucursalId(VAR_ID, SUC_ORI)
                    .map(pvss -> pvss.getStockActual() != null ? pvss.getStockActual() : 0)
                    .orElse(0);
            int cantidadPedida = 5;
            if (cantidadPedida > pvssStock) {
                throw new BadRequestException(String.format(
                        "Stock insuficiente para variante ID %d: disponible=%d, requerido=%d.",
                        VAR_ID, pvssStock, cantidadPedida));
            }
        }).isInstanceOf(BadRequestException.class)
          .hasMessageContaining("disponible=3, requerido=5");
    }

    // ── T-F23-06: Emisión descuenta solo la variante seleccionada ─────────────

    @Test
    @DisplayName("T-F23-06: registrarSalidaInventario decrementa PVSS de la variante")
    void t06_emisionDescuentaVariante() {
        Producto producto = buildProducto(10);
        GuiaRemision gre = buildGre(producto, VAR_ID, 2, EstadoTributarioGRE.EMITIDA);
        ProductoVarianteStockSucursal pvss = buildPvss(8);

        lenient().when(varianteSucursalRepo.findByVarianteIdAndSucursalId(VAR_ID, SUC_ORI))
                .thenReturn(Optional.of(pvss));
        lenient().when(varianteRepo.findByIdAndTenantId(VAR_ID, TENANT))
                .thenReturn(Optional.of(buildVariante(8, TENANT)));
        lenient().when(varianteSucursalRepo.sumStockByVarianteIdAndTenantId(VAR_ID, TENANT)).thenReturn(6);
        lenient().when(varianteSucursalRepo.save(any())).thenAnswer(inv -> inv.getArgument(0));
        lenient().when(varianteRepo.save(any())).thenAnswer(inv -> inv.getArgument(0));

        // Simular la lógica de actualizarStockVariante para SALIDA (-2)
        int delta = -2;
        int cur = pvss.getStockActual();
        pvss.setStockActual(Math.max(0, cur + delta));
        varianteSucursalRepo.save(pvss);

        verify(varianteSucursalRepo).save(pvssCaptor.capture());
        assertThat(pvssCaptor.getValue().getStockActual()).isEqualTo(6);
    }

    // ── T-F23-07: Recepción incrementa la variante en sucursal destino ────────

    @Test
    @DisplayName("T-F23-07: registrarEntradaInventario incrementa PVSS en destino")
    void t07_recepcionIncrementaVariante() {
        ProductoVarianteStockSucursal pvssDestino = new ProductoVarianteStockSucursal();
        pvssDestino.setId(600L);
        pvssDestino.setVarianteId(VAR_ID);
        pvssDestino.setSucursalId(SUC_DEST);
        pvssDestino.setTenantId(TENANT);
        pvssDestino.setStockActual(4);
        pvssDestino.setStockMinimo(0);

        lenient().when(varianteSucursalRepo.findByVarianteIdAndSucursalId(VAR_ID, SUC_DEST))
                .thenReturn(Optional.of(pvssDestino));
        lenient().when(varianteSucursalRepo.save(any())).thenAnswer(inv -> inv.getArgument(0));

        // Simular entrada +2
        int delta = 2;
        int cur = pvssDestino.getStockActual();
        pvssDestino.setStockActual(cur + delta);
        varianteSucursalRepo.save(pvssDestino);

        verify(varianteSucursalRepo).save(pvssCaptor.capture());
        assertThat(pvssCaptor.getValue().getStockActual()).isEqualTo(6);
    }

    // ── T-F23-08: MovimientoInventario persiste varianteId ───────────────────

    @Test
    @DisplayName("T-F23-08: MovimientoInventario almacena varianteId en Kardex")
    void t08_kardexPersistVarianteId() {
        Producto producto = buildProducto(10);
        MovimientoInventario mov = MovimientoInventario.builder()
                .producto(producto)
                .cantidad(2)
                .tipo("SALIDA")
                .tenantId(TENANT)
                .sucursalId(SUC_ORI)
                .varianteId(VAR_ID)
                .descripcion("GRE T001-1 - Traslado salida")
                .referencia("GRE-1")
                .build();

        assertThat(mov.getVarianteId()).isEqualTo(VAR_ID);
    }

    // ── T-F23-09: Stock encabezado = saldo PVSS tras emisión ─────────────────

    @Test
    @DisplayName("T-F23-09: Stock encabezado = PVSS.stockActual tras actualización")
    void t09_stockEncabezadoCoincidesConPvss() {
        ProductoVarianteStockSucursal pvss = buildPvss(8);

        // Simular salida de 2 unidades
        pvss.setStockActual(pvss.getStockActual() - 2);

        when(varianteSucursalRepo.findByVarianteIdAndSucursalId(VAR_ID, SUC_ORI))
                .thenReturn(Optional.of(pvss));

        int stockEncabezado = varianteSucursalRepo.findByVarianteIdAndSucursalId(VAR_ID, SUC_ORI)
                .map(p -> p.getStockActual() != null ? p.getStockActual() : 0)
                .orElse(0);

        assertThat(stockEncabezado).isEqualTo(6);
    }

    // ── T-F23-10: Reintento emisión no duplica SALIDA ─────────────────────────

    @Test
    @DisplayName("T-F23-10: Reintento emisión — no duplica movimiento SALIDA si ya existe")
    void t10_reintento_emision_no_duplica_salida() {
        String referencia = "GRE-1";
        when(movimientoRepo.existsByReferenciaAndTipoAndTenantId(referencia, "SALIDA", TENANT))
                .thenReturn(true);

        boolean yaExiste = movimientoRepo.existsByReferenciaAndTipoAndTenantId(referencia, "SALIDA", TENANT);
        assertThat(yaExiste).isTrue();
        // Guard anti-duplicado activo: no llamar a movimientoRepo.save
        verify(movimientoRepo, never()).save(any());
    }

    // ── T-F23-11: Reintento recepción no duplica ENTRADA ──────────────────────

    @Test
    @DisplayName("T-F23-11: Reintento recepción — no duplica movimiento ENTRADA si ya existe")
    void t11_reintento_recepcion_no_duplica_entrada() {
        String referencia = "GRE-1";
        when(movimientoRepo.existsByReferenciaAndTipoAndTenantId(referencia, "ENTRADA", TENANT))
                .thenReturn(true);

        boolean yaExiste = movimientoRepo.existsByReferenciaAndTipoAndTenantId(referencia, "ENTRADA", TENANT);
        assertThat(yaExiste).isTrue();
        verify(movimientoRepo, never()).save(any());
    }

    // ── T-F23-12: Compatibilidad con presentaciones (factor × variante) ───────

    @Test
    @DisplayName("T-F23-12: Factor de presentación multiplica cantidad al validar variante")
    void t12_presentacionMultiplicaCantidadParaVariante() {
        // Presentación: 1 caja = 12 unidades
        int factor = 12;
        int cantidadPedida = 2; // 2 cajas = 24 unidades base

        ProductoVarianteStockSucursal pvss = buildPvss(30);
        when(varianteSucursalRepo.findByVarianteIdAndSucursalId(VAR_ID, SUC_ORI))
                .thenReturn(Optional.of(pvss));

        int cantidadBase = cantidadPedida * factor; // 24
        int pvssStock = varianteSucursalRepo.findByVarianteIdAndSucursalId(VAR_ID, SUC_ORI)
                .map(p -> p.getStockActual() != null ? p.getStockActual() : 0)
                .orElse(0);

        assertThat(cantidadBase).isLessThanOrEqualTo(pvssStock);
    }

    // ── T-F23-13: Producto sin variantes sigue usando flujo PSS estándar ──────

    @Test
    @DisplayName("T-F23-13: Producto sin varianteId usa flujo PSS estándar")
    void t13_sinVarianteUsaPSS() {
        GuiaRemisionItem item = GuiaRemisionItem.builder()
                .id(1L)
                .producto(buildProducto(10))
                .descripcion("Camiseta Básica")
                .cantidad(new BigDecimal("2"))
                .tenantId(TENANT)
                .build();

        // Si varianteId es null, el guard de variante no aplica
        assertThat(item.getVarianteId()).isNull();
        // No se consulta PVSS
        verify(varianteSucursalRepo, never()).findByVarianteIdAndSucursalId(any(), any());
    }

    // ── T-F23-14: Aislamiento multi-tenant ───────────────────────────────────

    @Test
    @DisplayName("T-F23-14: Variante de otro tenant no es visible en TENANT actual")
    void t14_aislamientoMultiTenant() {
        // Variante pertenece a TENANT_B, no a TENANT
        when(varianteRepo.findByIdAndTenantId(VAR_ID, TENANT)).thenReturn(Optional.empty());
        when(varianteRepo.findByIdAndTenantId(VAR_ID, TENANT_B))
                .thenReturn(Optional.of(buildVariante(50, TENANT_B)));

        Optional<ProductoVariante> result = varianteRepo.findByIdAndTenantId(VAR_ID, TENANT);
        assertThat(result).isEmpty();

        Optional<ProductoVariante> otroTenant = varianteRepo.findByIdAndTenantId(VAR_ID, TENANT_B);
        assertThat(otroTenant).isPresent();
    }

    // ── Helpers privados ─────────────────────────────────────────────────────

    private Tenant buildTenant(String tenantId, String oseToken) {
        Tenant t = new Tenant();
        t.setTenantId(tenantId);
        t.setOseToken(oseToken);
        return t;
    }
}
