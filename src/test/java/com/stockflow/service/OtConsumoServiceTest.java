package com.stockflow.service;

import com.stockflow.dto.OtConsumoMaterialDTO;
import com.stockflow.dto.RegistrarConsumoOtRequestDTO;
import com.stockflow.entity.MovimientoInventario;
import com.stockflow.entity.OrdenTrabajo;
import com.stockflow.entity.OtConsumoLote;
import com.stockflow.entity.OtConsumoMaterial;
import com.stockflow.entity.Producto;
import com.stockflow.entity.ProductoPresentacion;
import com.stockflow.entity.ProductoStockSucursal;
import com.stockflow.entity.StockLote;
import com.stockflow.entity.Sucursal;
import com.stockflow.entity.UnidadMedida;
import com.stockflow.entity.Usuario;
import com.stockflow.exception.BadRequestException;
import com.stockflow.exception.ResourceNotFoundException;
import com.stockflow.repository.MovimientoInventarioRepository;
import com.stockflow.repository.OrdenTrabajoRepository;
import com.stockflow.repository.OtConsumoClasificacionRepository;
import com.stockflow.repository.OtConsumoDevolucionLoteRepository;
import com.stockflow.repository.OtConsumoDevolucionRepository;
import com.stockflow.repository.OtConsumoLoteRepository;
import com.stockflow.repository.OtConsumoMaterialRepository;
import com.stockflow.repository.ProductoPresentacionRepository;
import com.stockflow.repository.ProductoRepository;
import com.stockflow.repository.ProductoStockSucursalRepository;
import com.stockflow.repository.StockLoteRepository;
import com.stockflow.repository.UsuarioRepository;
import com.stockflow.service.impl.OtConsumoServiceImpl;
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
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mockStatic;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class OtConsumoServiceTest {

    private static final String TENANT_A = "tenant-a";
    private static final Long OT_ID     = 1L;
    private static final Long USER_ID   = 99L;

    @Mock private OrdenTrabajoRepository         otRepository;
    @Mock private ProductoRepository             productoRepository;
    @Mock private ProductoPresentacionRepository presentacionRepository;
    @Mock private ProductoStockSucursalRepository stockSucursalRepository;
    @Mock private StockLoteRepository            stockLoteRepository;
    @Mock private OtConsumoMaterialRepository    consumoRepository;
    @Mock private OtConsumoLoteRepository        consumoLoteRepository;
    @Mock private OtConsumoDevolucionRepository      devolucionRepository;
    @Mock private OtConsumoDevolucionLoteRepository  devolucionLoteRepository;
    @Mock private OtConsumoClasificacionRepository   clasificacionRepository;
    @Mock private MovimientoInventarioRepository movimientoRepository;
    @Mock private UsuarioRepository              usuarioRepository;
    @Mock private TenantService                  tenantService;

    @InjectMocks
    private OtConsumoServiceImpl service;

    private MockedStatic<TenantContext> tenantContextMock;

    @BeforeEach
    void setUp() {
        tenantContextMock = mockStatic(TenantContext.class);
        tenantContextMock.when(TenantContext::getCurrentTenant).thenReturn(TENANT_A);
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

    private OrdenTrabajo buildOt(String estado) {
        Sucursal sucursal = Sucursal.builder().id(10L).build();
        return OrdenTrabajo.builder()
                .id(OT_ID)
                .tenantId(TENANT_A)
                .numeroOt("OT-2026-0001")
                .estado(estado)
                .sucursal(sucursal)
                .build();
    }

    private Producto buildProducto(String tipo, int stock) {
        return Producto.builder()
                .id(100L)
                .tenantId(TENANT_A)
                .tipo(tipo)
                .activo(true)
                .stockActual(stock)
                .costoUnitario(BigDecimal.TEN)
                .build();
    }

    private StockLote buildLote(Long id, int stock, LocalDate vence) {
        return StockLote.builder()
                .id(id)
                .productoId(100L)
                .tenantId(TENANT_A)
                .sucursalId(10L)
                .stockActual(stock)
                .fechaVencimiento(vence)
                .build();
    }

    private OtConsumoMaterial buildSavedConsumo(OrdenTrabajo ot, Producto p) {
        return OtConsumoMaterial.builder()
                .id(200L)
                .tenantId(TENANT_A)
                .ordenTrabajo(ot)
                .productoId(p.getId())
                .factorPresentacion(1)
                .cantidadPresentacion(5)
                .cantidadBase(5)
                .createdAt(LocalDateTime.now())
                .build();
    }

    private RegistrarConsumoOtRequestDTO buildReq(int cantidad) {
        return RegistrarConsumoOtRequestDTO.builder()
                .productoId(100L)
                .cantidadPresentacion(cantidad)
                .build();
    }

    private OrdenTrabajo buildOtSinSucursal(String estado) {
        return OrdenTrabajo.builder()
                .id(OT_ID)
                .tenantId(TENANT_A)
                .numeroOt("OT-2026-0001")
                .estado(estado)
                .sucursal(null)
                .build();
    }

    private Usuario buildUsuario() {
        return Usuario.builder().id(USER_ID).build();
    }

    // ── T1: SERVICIO no descuenta stock ───────────────────────────────────────

    @Test
    void registrarConsumo_tipoServicio_noDescuentaStockNiCreaMovimiento() {
        OrdenTrabajo ot = buildOt("ABIERTA");
        Producto servicio = buildProducto("SERVICIO", 0);
        OtConsumoMaterial consumo = buildSavedConsumo(ot, servicio);

        when(otRepository.findByIdAndTenantIdForUpdate(OT_ID, TENANT_A)).thenReturn(Optional.of(ot));
        when(productoRepository.findByIdForUpdate(100L)).thenReturn(Optional.of(servicio));
        when(consumoRepository.save(any())).thenReturn(consumo);

        OtConsumoMaterialDTO result = service.registrarConsumo(OT_ID, buildReq(5));

        assertThat(result.getProductoId()).isEqualTo(100L);
        verify(movimientoRepository, never()).save(any());
        verify(stockLoteRepository, never()).existsByProductoIdAndTenantId(anyLong(), anyString());
    }

    // ── T2: Estado no permitido ───────────────────────────────────────────────

    @Test
    void registrarConsumo_estadoCerrada_lanzaBadRequestException() {
        OrdenTrabajo ot = buildOt("CERRADA_TECN");
        when(otRepository.findByIdAndTenantIdForUpdate(OT_ID, TENANT_A)).thenReturn(Optional.of(ot));

        assertThatThrownBy(() -> service.registrarConsumo(OT_ID, buildReq(1)))
                .isInstanceOf(BadRequestException.class)
                .hasMessageContaining("CERRADA_TECN");
    }

    // ── T3: Producto de otro tenant ───────────────────────────────────────────

    @Test
    void registrarConsumo_productoOtroTenant_lanzaResourceNotFoundException() {
        OrdenTrabajo ot = buildOt("ABIERTA");
        Producto p = buildProducto("PRODUCTO", 10);
        p.setTenantId("otro-tenant");

        when(otRepository.findByIdAndTenantIdForUpdate(OT_ID, TENANT_A)).thenReturn(Optional.of(ot));
        when(productoRepository.findByIdForUpdate(100L)).thenReturn(Optional.of(p));

        assertThatThrownBy(() -> service.registrarConsumo(OT_ID, buildReq(1)))
                .isInstanceOf(ResourceNotFoundException.class)
                .hasMessageContaining("Producto no encontrado");
    }

    // ── T4: Producto inactivo ─────────────────────────────────────────────────

    @Test
    void registrarConsumo_productoInactivo_lanzaBadRequestException() {
        OrdenTrabajo ot = buildOt("ABIERTA");
        Producto p = buildProducto("PRODUCTO", 10);
        p.setActivo(false);

        when(otRepository.findByIdAndTenantIdForUpdate(OT_ID, TENANT_A)).thenReturn(Optional.of(ot));
        when(productoRepository.findByIdForUpdate(100L)).thenReturn(Optional.of(p));

        assertThatThrownBy(() -> service.registrarConsumo(OT_ID, buildReq(1)))
                .isInstanceOf(BadRequestException.class)
                .hasMessageContaining("activo");
    }

    // ── T5: Cantidad cero ─────────────────────────────────────────────────────

    @Test
    void registrarConsumo_cantidadCero_lanzaBadRequestException() {
        OrdenTrabajo ot = buildOt("ABIERTA");
        when(otRepository.findByIdAndTenantIdForUpdate(OT_ID, TENANT_A)).thenReturn(Optional.of(ot));

        RegistrarConsumoOtRequestDTO req = RegistrarConsumoOtRequestDTO.builder()
                .productoId(100L)
                .cantidadPresentacion(0)
                .build();

        assertThatThrownBy(() -> service.registrarConsumo(OT_ID, req))
                .isInstanceOf(BadRequestException.class)
                .hasMessageContaining("mayor que cero");
    }

    // ── T6: Cantidad negativa ─────────────────────────────────────────────────

    @Test
    void registrarConsumo_cantidadNegativa_lanzaBadRequestException() {
        OrdenTrabajo ot = buildOt("ABIERTA");
        when(otRepository.findByIdAndTenantIdForUpdate(OT_ID, TENANT_A)).thenReturn(Optional.of(ot));

        RegistrarConsumoOtRequestDTO req = RegistrarConsumoOtRequestDTO.builder()
                .productoId(100L)
                .cantidadPresentacion(-3)
                .build();

        assertThatThrownBy(() -> service.registrarConsumo(OT_ID, req))
                .isInstanceOf(BadRequestException.class)
                .hasMessageContaining("mayor que cero");
    }

    // ── T7: Stock global insuficiente ─────────────────────────────────────────

    @Test
    void registrarConsumo_stockGlobalInsuficiente_lanzaBadRequestException() {
        OrdenTrabajo ot = buildOt("EN_PROCESO");
        Producto p = buildProducto("PRODUCTO", 2);

        when(otRepository.findByIdAndTenantIdForUpdate(OT_ID, TENANT_A)).thenReturn(Optional.of(ot));
        when(productoRepository.findByIdForUpdate(100L)).thenReturn(Optional.of(p));
        when(stockSucursalRepository.findByProductoIdAndSucursalIdForUpdate(100L, 10L))
                .thenReturn(Optional.empty());

        assertThatThrownBy(() -> service.registrarConsumo(OT_ID, buildReq(5)))
                .isInstanceOf(BadRequestException.class)
                .hasMessageContaining("Stock global insuficiente");
    }

    // ── T8: Stock de sucursal insuficiente ────────────────────────────────────

    @Test
    void registrarConsumo_stockSucursalInsuficiente_lanzaBadRequestException() {
        OrdenTrabajo ot = buildOt("ABIERTA");
        Producto p = buildProducto("PRODUCTO", 100);

        ProductoStockSucursal ss = ProductoStockSucursal.builder()
                .id(50L).stockActual(2).build();

        when(otRepository.findByIdAndTenantIdForUpdate(OT_ID, TENANT_A)).thenReturn(Optional.of(ot));
        when(productoRepository.findByIdForUpdate(100L)).thenReturn(Optional.of(p));
        when(stockSucursalRepository.findByProductoIdAndSucursalIdForUpdate(100L, 10L))
                .thenReturn(Optional.of(ss));

        assertThatThrownBy(() -> service.registrarConsumo(OT_ID, buildReq(5)))
                .isInstanceOf(BadRequestException.class)
                .hasMessageContaining("Stock de sucursal insuficiente");
    }

    // ── T9: Sin lotes — descuenta stockActual del producto ────────────────────

    @Test
    void registrarConsumo_sinLotes_descontaStockProducto() {
        OrdenTrabajo ot = buildOt("ABIERTA");
        Producto p = buildProducto("PRODUCTO", 20);
        OtConsumoMaterial consumo = buildSavedConsumo(ot, p);

        when(otRepository.findByIdAndTenantIdForUpdate(OT_ID, TENANT_A)).thenReturn(Optional.of(ot));
        when(productoRepository.findByIdForUpdate(100L)).thenReturn(Optional.of(p));
        when(stockSucursalRepository.findByProductoIdAndSucursalIdForUpdate(100L, 10L))
                .thenReturn(Optional.empty());
        when(stockLoteRepository.existsByProductoIdAndTenantId(100L, TENANT_A)).thenReturn(false);
        when(usuarioRepository.findById(USER_ID)).thenReturn(Optional.of(buildUsuario()));
        when(movimientoRepository.save(any())).thenReturn(MovimientoInventario.builder().build());
        when(consumoRepository.save(any())).thenReturn(consumo);

        service.registrarConsumo(OT_ID, buildReq(5));

        ArgumentCaptor<Producto> captor = ArgumentCaptor.forClass(Producto.class);
        verify(productoRepository).save(captor.capture());
        assertThat(captor.getValue().getStockActual()).isEqualTo(15);
    }

    // ── T10: Sin lotes — descuenta stockSucursal si existe ───────────────────

    @Test
    void registrarConsumo_sinLotes_descontaStockSucursal_siExiste() {
        OrdenTrabajo ot = buildOt("ABIERTA");
        Producto p = buildProducto("PRODUCTO", 20);
        ProductoStockSucursal ss = ProductoStockSucursal.builder()
                .id(50L).stockActual(15).build();
        OtConsumoMaterial consumo = buildSavedConsumo(ot, p);

        when(otRepository.findByIdAndTenantIdForUpdate(OT_ID, TENANT_A)).thenReturn(Optional.of(ot));
        when(productoRepository.findByIdForUpdate(100L)).thenReturn(Optional.of(p));
        when(stockSucursalRepository.findByProductoIdAndSucursalIdForUpdate(100L, 10L))
                .thenReturn(Optional.of(ss));
        when(stockLoteRepository.existsByProductoIdAndTenantId(100L, TENANT_A)).thenReturn(false);
        when(usuarioRepository.findById(USER_ID)).thenReturn(Optional.of(buildUsuario()));
        when(movimientoRepository.save(any())).thenReturn(MovimientoInventario.builder().build());
        when(consumoRepository.save(any())).thenReturn(consumo);

        service.registrarConsumo(OT_ID, buildReq(5));

        ArgumentCaptor<ProductoStockSucursal> captor = ArgumentCaptor.forClass(ProductoStockSucursal.class);
        verify(stockSucursalRepository).save(captor.capture());
        assertThat(captor.getValue().getStockActual()).isEqualTo(10);
    }

    // ── T11: Sin lotes — crea movimiento de inventario ───────────────────────

    @Test
    void registrarConsumo_sinLotes_creaMovimientoInventario() {
        OrdenTrabajo ot = buildOt("ABIERTA");
        Producto p = buildProducto("PRODUCTO", 20);
        Usuario usuario = buildUsuario();
        OtConsumoMaterial consumo = buildSavedConsumo(ot, p);

        when(otRepository.findByIdAndTenantIdForUpdate(OT_ID, TENANT_A)).thenReturn(Optional.of(ot));
        when(productoRepository.findByIdForUpdate(100L)).thenReturn(Optional.of(p));
        when(stockSucursalRepository.findByProductoIdAndSucursalIdForUpdate(100L, 10L))
                .thenReturn(Optional.empty());
        when(stockLoteRepository.existsByProductoIdAndTenantId(100L, TENANT_A)).thenReturn(false);
        when(usuarioRepository.findById(USER_ID)).thenReturn(Optional.of(usuario));
        when(movimientoRepository.save(any())).thenReturn(MovimientoInventario.builder().build());
        when(consumoRepository.save(any())).thenReturn(consumo);

        service.registrarConsumo(OT_ID, buildReq(5));

        ArgumentCaptor<MovimientoInventario> captor = ArgumentCaptor.forClass(MovimientoInventario.class);
        verify(movimientoRepository).save(captor.capture());
        assertThat(captor.getValue().getTipo()).isEqualTo("CONSUMO_OT");
        assertThat(captor.getValue().getCantidad()).isEqualTo(5);
        assertThat(captor.getValue().getReferencia()).isEqualTo("OT-2026-0001");
        assertThat(captor.getValue().getUsuario()).isNotNull();
        assertThat(captor.getValue().getUsuario().getId()).isEqualTo(USER_ID);
    }

    // ── T12: Con lotes — orden FEFO correcto ──────────────────────────────────

    @Test
    void registrarConsumo_conLotes_fefoOrdenCorrecto() {
        OrdenTrabajo ot = buildOt("EN_PROCESO");
        Producto p = buildProducto("PRODUCTO", 20);

        LocalDate pronto = LocalDate.now().plusDays(10);
        LocalDate lejos  = LocalDate.now().plusDays(100);

        StockLote loteA = buildLote(1L, 10, lejos);   // vence más tarde
        StockLote loteB = buildLote(2L, 10, pronto);  // vence más pronto → FEFO primero

        OtConsumoMaterial consumo = buildSavedConsumo(ot, p);
        consumo.setCantidadBase(5);

        when(otRepository.findByIdAndTenantIdForUpdate(OT_ID, TENANT_A)).thenReturn(Optional.of(ot));
        when(productoRepository.findByIdForUpdate(100L)).thenReturn(Optional.of(p));
        when(stockSucursalRepository.findByProductoIdAndSucursalIdForUpdate(100L, 10L))
                .thenReturn(Optional.empty());
        when(stockLoteRepository.existsByProductoIdAndTenantId(100L, TENANT_A)).thenReturn(true);
        when(stockLoteRepository.sumStockVigenteConSucursal(eq(100L), eq(TENANT_A), eq(10L), any())).thenReturn(20);
        // FEFO devuelve B primero (vence más pronto)
        when(stockLoteRepository.findVigentesFefoConSucursal(eq(100L), eq(TENANT_A), eq(10L), any()))
                .thenReturn(List.of(loteB, loteA));
        when(stockLoteRepository.findByIdForUpdate(1L)).thenReturn(Optional.of(loteA));
        when(stockLoteRepository.findByIdForUpdate(2L)).thenReturn(Optional.of(loteB));
        when(stockLoteRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));
        when(usuarioRepository.findById(USER_ID)).thenReturn(Optional.of(buildUsuario()));
        when(movimientoRepository.save(any())).thenReturn(MovimientoInventario.builder().build());
        when(consumoRepository.save(any())).thenReturn(consumo);
        when(consumoLoteRepository.saveAll(any())).thenReturn(List.of());

        OtConsumoMaterialDTO result = service.registrarConsumo(OT_ID, buildReq(5));

        // Debe haberse consumido del loteB (FEFO) primero
        assertThat(loteB.getStockActual()).isEqualTo(5); // 10 - 5
        assertThat(loteA.getStockActual()).isEqualTo(10); // intacto
    }

    // ── T13: Con lotes — distribución entre varios lotes ─────────────────────

    @Test
    void registrarConsumo_conLotes_variosLotes_distribucionCorrecta() {
        OrdenTrabajo ot = buildOt("DIAGNOSTICADA");
        Producto p = buildProducto("PRODUCTO", 15);

        LocalDate pronto = LocalDate.now().plusDays(5);
        LocalDate lejos  = LocalDate.now().plusDays(50);

        StockLote lote1 = buildLote(3L, 4, pronto);  // se agota primero
        StockLote lote2 = buildLote(4L, 11, lejos);  // recibe el resto

        OtConsumoMaterial consumo = buildSavedConsumo(ot, p);
        consumo.setCantidadBase(10);

        when(otRepository.findByIdAndTenantIdForUpdate(OT_ID, TENANT_A)).thenReturn(Optional.of(ot));
        when(productoRepository.findByIdForUpdate(100L)).thenReturn(Optional.of(p));
        when(stockSucursalRepository.findByProductoIdAndSucursalIdForUpdate(100L, 10L))
                .thenReturn(Optional.empty());
        when(stockLoteRepository.existsByProductoIdAndTenantId(100L, TENANT_A)).thenReturn(true);
        when(stockLoteRepository.sumStockVigenteConSucursal(eq(100L), eq(TENANT_A), eq(10L), any())).thenReturn(15);
        when(stockLoteRepository.findVigentesFefoConSucursal(eq(100L), eq(TENANT_A), eq(10L), any()))
                .thenReturn(List.of(lote1, lote2));
        when(stockLoteRepository.findByIdForUpdate(3L)).thenReturn(Optional.of(lote1));
        when(stockLoteRepository.findByIdForUpdate(4L)).thenReturn(Optional.of(lote2));
        when(stockLoteRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));
        when(usuarioRepository.findById(USER_ID)).thenReturn(Optional.of(buildUsuario()));
        when(movimientoRepository.save(any())).thenReturn(MovimientoInventario.builder().build());
        when(consumoRepository.save(any())).thenReturn(consumo);
        when(consumoLoteRepository.saveAll(any())).thenReturn(List.of());

        service.registrarConsumo(OT_ID, buildReq(10));

        assertThat(lote1.getStockActual()).isEqualTo(0);  // 4 - 4
        assertThat(lote2.getStockActual()).isEqualTo(5);  // 11 - 6
    }

    // ── T14: Presentación multiplica cantidadBase ─────────────────────────────

    @Test
    void registrarConsumo_presentacion_multiplicaCantidadBase() {
        OrdenTrabajo ot = buildOt("ABIERTA");
        Producto p = buildProducto("PRODUCTO", 50);

        Producto prodPresentacion = Producto.builder().id(100L).build();
        ProductoPresentacion pres = ProductoPresentacion.builder()
                .id(77L).producto(prodPresentacion).factor(6).build();

        OtConsumoMaterial consumo = OtConsumoMaterial.builder()
                .id(200L).tenantId(TENANT_A).ordenTrabajo(ot)
                .productoId(100L).factorPresentacion(6)
                .cantidadPresentacion(3).cantidadBase(18)
                .createdAt(LocalDateTime.now()).build();

        RegistrarConsumoOtRequestDTO req = RegistrarConsumoOtRequestDTO.builder()
                .productoId(100L).presentacionId(77L).cantidadPresentacion(3).build();

        when(otRepository.findByIdAndTenantIdForUpdate(OT_ID, TENANT_A)).thenReturn(Optional.of(ot));
        when(productoRepository.findByIdForUpdate(100L)).thenReturn(Optional.of(p));
        when(presentacionRepository.findById(77L)).thenReturn(Optional.of(pres));
        when(stockSucursalRepository.findByProductoIdAndSucursalIdForUpdate(100L, 10L))
                .thenReturn(Optional.empty());
        when(stockLoteRepository.existsByProductoIdAndTenantId(100L, TENANT_A)).thenReturn(false);
        when(usuarioRepository.findById(USER_ID)).thenReturn(Optional.of(buildUsuario()));
        when(movimientoRepository.save(any())).thenReturn(MovimientoInventario.builder().build());
        when(consumoRepository.save(any())).thenReturn(consumo);

        OtConsumoMaterialDTO result = service.registrarConsumo(OT_ID, req);

        assertThat(result.getCantidadBase()).isEqualTo(18);
        assertThat(result.getFactorPresentacion()).isEqualTo(6);
    }

    // ── T15: Presentación de otro producto ───────────────────────────────────

    @Test
    void registrarConsumo_presentacionOtroProducto_lanzaBadRequestException() {
        OrdenTrabajo ot = buildOt("ABIERTA");
        Producto p = buildProducto("PRODUCTO", 20);

        // Presentación pertenece al producto 999, no al 100
        Producto otroProd = Producto.builder().id(999L).build();
        ProductoPresentacion pres = ProductoPresentacion.builder()
                .id(77L).producto(otroProd).factor(2).build();

        RegistrarConsumoOtRequestDTO req = RegistrarConsumoOtRequestDTO.builder()
                .productoId(100L).presentacionId(77L).cantidadPresentacion(2).build();

        when(otRepository.findByIdAndTenantIdForUpdate(OT_ID, TENANT_A)).thenReturn(Optional.of(ot));
        when(productoRepository.findByIdForUpdate(100L)).thenReturn(Optional.of(p));
        when(presentacionRepository.findById(77L)).thenReturn(Optional.of(pres));

        assertThatThrownBy(() -> service.registrarConsumo(OT_ID, req))
                .isInstanceOf(BadRequestException.class)
                .hasMessageContaining("no pertenece al producto");
    }

    // ── T16: Stock en lotes insuficiente ─────────────────────────────────────

    @Test
    void registrarConsumo_conLotes_lotesInsuficientes_lanzaBadRequestException() {
        OrdenTrabajo ot = buildOt("ABIERTA");
        Producto p = buildProducto("PRODUCTO", 20);

        when(otRepository.findByIdAndTenantIdForUpdate(OT_ID, TENANT_A)).thenReturn(Optional.of(ot));
        when(productoRepository.findByIdForUpdate(100L)).thenReturn(Optional.of(p));
        when(stockSucursalRepository.findByProductoIdAndSucursalIdForUpdate(100L, 10L))
                .thenReturn(Optional.empty());
        when(stockLoteRepository.existsByProductoIdAndTenantId(100L, TENANT_A)).thenReturn(true);
        when(stockLoteRepository.sumStockVigenteConSucursal(eq(100L), eq(TENANT_A), eq(10L), any())).thenReturn(3);

        assertThatThrownBy(() -> service.registrarConsumo(OT_ID, buildReq(10)))
                .isInstanceOf(BadRequestException.class)
                .hasMessageContaining("insuficiente");
    }

    // ── FT-1: Lotes vencidos no se descuentan; lotes vigentes sí ─────────────

    @Test
    void registrarConsumo_conLotes_loteVencidoNoSeDescuenta_loteVigenteSeDescuenta() {
        OrdenTrabajo ot = buildOt("EN_PROCESO");
        Producto p = buildProducto("PRODUCTO", 20);

        LocalDate ayer   = LocalDate.now().minusDays(1);
        LocalDate manana = LocalDate.now().plusDays(30);

        StockLote loteVencido = buildLote(10L, 8, ayer);    // vencido — no debe consumirse
        StockLote loteVigente = buildLote(11L, 10, manana);  // vigente — debe consumirse

        OtConsumoMaterial consumo = buildSavedConsumo(ot, p);
        consumo.setCantidadBase(5);

        when(otRepository.findByIdAndTenantIdForUpdate(OT_ID, TENANT_A)).thenReturn(Optional.of(ot));
        when(productoRepository.findByIdForUpdate(100L)).thenReturn(Optional.of(p));
        when(stockSucursalRepository.findByProductoIdAndSucursalIdForUpdate(100L, 10L))
                .thenReturn(Optional.empty());
        when(stockLoteRepository.existsByProductoIdAndTenantId(100L, TENANT_A)).thenReturn(true);
        when(stockLoteRepository.sumStockVigenteConSucursal(eq(100L), eq(TENANT_A), eq(10L), any())).thenReturn(10);
        // FEFO solo devuelve el vigente (la query filtra vencidos)
        when(stockLoteRepository.findVigentesFefoConSucursal(eq(100L), eq(TENANT_A), eq(10L), any()))
                .thenReturn(List.of(loteVigente));
        when(stockLoteRepository.findByIdForUpdate(11L)).thenReturn(Optional.of(loteVigente));
        when(stockLoteRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));
        when(usuarioRepository.findById(USER_ID)).thenReturn(Optional.of(buildUsuario()));
        when(movimientoRepository.save(any())).thenReturn(MovimientoInventario.builder().build());
        when(consumoRepository.save(any())).thenReturn(consumo);
        when(consumoLoteRepository.saveAll(any())).thenReturn(List.of());

        service.registrarConsumo(OT_ID, buildReq(5));

        // Lote vencido intacto
        assertThat(loteVencido.getStockActual()).isEqualTo(8);
        // Lote vigente descontado
        assertThat(loteVigente.getStockActual()).isEqualTo(5); // 10 - 5
    }

    // ── FT-2: OT sin sucursal usa query FEFO sin sucursal ────────────────────

    @Test
    void registrarConsumo_otSinSucursal_usaFEFOSinSucursal() {
        OrdenTrabajo ot = buildOtSinSucursal("ABIERTA");
        Producto p = buildProducto("PRODUCTO", 20);

        LocalDate manana = LocalDate.now().plusDays(30);
        StockLote lote = StockLote.builder()
                .id(20L).productoId(100L).tenantId(TENANT_A)
                .sucursalId(null).stockActual(10).fechaVencimiento(manana)
                .build();

        OtConsumoMaterial consumo = buildSavedConsumo(ot, p);

        when(otRepository.findByIdAndTenantIdForUpdate(OT_ID, TENANT_A)).thenReturn(Optional.of(ot));
        when(productoRepository.findByIdForUpdate(100L)).thenReturn(Optional.of(p));
        when(stockLoteRepository.existsByProductoIdAndTenantId(100L, TENANT_A)).thenReturn(true);
        when(stockLoteRepository.sumStockVigente(eq(100L), eq(TENANT_A), any())).thenReturn(10);
        when(stockLoteRepository.findVigentesFefo(eq(100L), eq(TENANT_A), any()))
                .thenReturn(List.of(lote));
        when(stockLoteRepository.findByIdForUpdate(20L)).thenReturn(Optional.of(lote));
        when(stockLoteRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));
        when(usuarioRepository.findById(USER_ID)).thenReturn(Optional.of(buildUsuario()));
        when(movimientoRepository.save(any())).thenReturn(MovimientoInventario.builder().build());
        when(consumoRepository.save(any())).thenReturn(consumo);
        when(consumoLoteRepository.saveAll(any())).thenReturn(List.of());

        service.registrarConsumo(OT_ID, buildReq(5));

        // Verificar que se usó findVigentesFefo (sin sucursal), no la variante con sucursal
        verify(stockLoteRepository).findVigentesFefo(eq(100L), eq(TENANT_A), any());
        verify(stockLoteRepository, never()).findVigentesFefoConSucursal(anyLong(), anyString(), anyLong(), any());
        // Stock descontado correctamente
        assertThat(lote.getStockActual()).isEqualTo(5); // 10 - 5
    }

    // ── T-O1a: sumLotesTodos == stockSucursal → flujo continúa sin excepción ──

    @Test
    void registrarConsumo_consistenciaLotesOk_continuaFlujoNormal() {
        OrdenTrabajo ot = buildOt("EN_PROCESO");
        Producto p = buildProducto("PRODUCTO", 20);
        ProductoStockSucursal ss = ProductoStockSucursal.builder()
                .id(50L).stockActual(15).build();

        LocalDate manana = LocalDate.now().plusDays(30);
        StockLote lote = buildLote(5L, 15, manana);
        OtConsumoMaterial consumo = buildSavedConsumo(ot, p);

        when(otRepository.findByIdAndTenantIdForUpdate(OT_ID, TENANT_A)).thenReturn(Optional.of(ot));
        when(productoRepository.findByIdForUpdate(100L)).thenReturn(Optional.of(p));
        when(stockSucursalRepository.findByProductoIdAndSucursalIdForUpdate(100L, 10L))
                .thenReturn(Optional.of(ss));
        when(stockLoteRepository.existsByProductoIdAndTenantId(100L, TENANT_A)).thenReturn(true);
        when(stockLoteRepository.sumStockTotalLotesBySucursal(100L, TENANT_A, 10L)).thenReturn(15);
        when(stockLoteRepository.sumStockVigenteConSucursal(eq(100L), eq(TENANT_A), eq(10L), any())).thenReturn(15);
        when(stockLoteRepository.findVigentesFefoConSucursal(eq(100L), eq(TENANT_A), eq(10L), any()))
                .thenReturn(List.of(lote));
        when(stockLoteRepository.findByIdForUpdate(5L)).thenReturn(Optional.of(lote));
        when(stockLoteRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));
        when(usuarioRepository.findById(USER_ID)).thenReturn(Optional.of(buildUsuario()));
        when(movimientoRepository.save(any())).thenReturn(MovimientoInventario.builder().build());
        when(consumoRepository.save(any())).thenReturn(consumo);
        when(consumoLoteRepository.saveAll(any())).thenReturn(List.of());

        OtConsumoMaterialDTO result = service.registrarConsumo(OT_ID, buildReq(5));

        assertThat(result).isNotNull();
        verify(consumoRepository).save(any());
    }

    // ── T-O1b: sumLotesTodos > stockSucursal → BadRequestException ────────────

    @Test
    void registrarConsumo_sumLotesMayorQueStockSucursal_lanzaBadRequestException() {
        OrdenTrabajo ot = buildOt("EN_PROCESO");
        Producto p = buildProducto("PRODUCTO", 20);
        ProductoStockSucursal ss = ProductoStockSucursal.builder()
                .id(50L).stockActual(10).build();

        when(otRepository.findByIdAndTenantIdForUpdate(OT_ID, TENANT_A)).thenReturn(Optional.of(ot));
        when(productoRepository.findByIdForUpdate(100L)).thenReturn(Optional.of(p));
        when(stockSucursalRepository.findByProductoIdAndSucursalIdForUpdate(100L, 10L))
                .thenReturn(Optional.of(ss));
        when(stockLoteRepository.existsByProductoIdAndTenantId(100L, TENANT_A)).thenReturn(true);
        when(stockLoteRepository.sumStockTotalLotesBySucursal(100L, TENANT_A, 10L)).thenReturn(15);

        assertThatThrownBy(() -> service.registrarConsumo(OT_ID, buildReq(5)))
                .isInstanceOf(BadRequestException.class)
                .hasMessageContaining("stockSucursal=10")
                .hasMessageContaining("sumLotesTodos=15");
    }

    // ── T-O1c: sumLotesTodos < stockSucursal → BadRequestException ────────────

    @Test
    void registrarConsumo_sumLotesMenorQueStockSucursal_lanzaBadRequestException() {
        OrdenTrabajo ot = buildOt("EN_PROCESO");
        Producto p = buildProducto("PRODUCTO", 20);
        ProductoStockSucursal ss = ProductoStockSucursal.builder()
                .id(50L).stockActual(20).build();

        when(otRepository.findByIdAndTenantIdForUpdate(OT_ID, TENANT_A)).thenReturn(Optional.of(ot));
        when(productoRepository.findByIdForUpdate(100L)).thenReturn(Optional.of(p));
        when(stockSucursalRepository.findByProductoIdAndSucursalIdForUpdate(100L, 10L))
                .thenReturn(Optional.of(ss));
        when(stockLoteRepository.existsByProductoIdAndTenantId(100L, TENANT_A)).thenReturn(true);
        when(stockLoteRepository.sumStockTotalLotesBySucursal(100L, TENANT_A, 10L)).thenReturn(12);

        assertThatThrownBy(() -> service.registrarConsumo(OT_ID, buildReq(5)))
                .isInstanceOf(BadRequestException.class)
                .hasMessageContaining("stockSucursal=20")
                .hasMessageContaining("sumLotesTodos=12");
    }

    // ── T-O1d: Lote vencido participa en la suma de consistencia ──────────────

    @Test
    void registrarConsumo_loteVencidoParticipaEnSumaConsistencia_noBloqueaConsumo() {
        OrdenTrabajo ot = buildOt("EN_PROCESO");
        Producto p = buildProducto("PRODUCTO", 20);
        // stockSucursal = 15 = loteVencido(5) + loteVigente(10)
        ProductoStockSucursal ss = ProductoStockSucursal.builder()
                .id(50L).stockActual(15).build();

        LocalDate ayer   = LocalDate.now().minusDays(1);
        LocalDate manana = LocalDate.now().plusDays(30);
        StockLote loteVencido = buildLote(40L, 5, ayer);
        StockLote loteVigente = buildLote(41L, 10, manana);
        OtConsumoMaterial consumo = buildSavedConsumo(ot, p);

        when(otRepository.findByIdAndTenantIdForUpdate(OT_ID, TENANT_A)).thenReturn(Optional.of(ot));
        when(productoRepository.findByIdForUpdate(100L)).thenReturn(Optional.of(p));
        when(stockSucursalRepository.findByProductoIdAndSucursalIdForUpdate(100L, 10L))
                .thenReturn(Optional.of(ss));
        when(stockLoteRepository.existsByProductoIdAndTenantId(100L, TENANT_A)).thenReturn(true);
        // La suma TODOS (incluyendo vencido) == 15 == stockSucursal → consistencia OK
        when(stockLoteRepository.sumStockTotalLotesBySucursal(100L, TENANT_A, 10L)).thenReturn(15);
        // Solo vigentes para I2 y FEFO
        when(stockLoteRepository.sumStockVigenteConSucursal(eq(100L), eq(TENANT_A), eq(10L), any())).thenReturn(10);
        when(stockLoteRepository.findVigentesFefoConSucursal(eq(100L), eq(TENANT_A), eq(10L), any()))
                .thenReturn(List.of(loteVigente));
        when(stockLoteRepository.findByIdForUpdate(41L)).thenReturn(Optional.of(loteVigente));
        when(stockLoteRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));
        when(usuarioRepository.findById(USER_ID)).thenReturn(Optional.of(buildUsuario()));
        when(movimientoRepository.save(any())).thenReturn(MovimientoInventario.builder().build());
        when(consumoRepository.save(any())).thenReturn(consumo);
        when(consumoLoteRepository.saveAll(any())).thenReturn(List.of());

        service.registrarConsumo(OT_ID, buildReq(5));

        verify(stockLoteRepository).sumStockTotalLotesBySucursal(100L, TENANT_A, 10L);
        // Lote vencido no se descuenta; lote vigente sí
        assertThat(loteVencido.getStockActual()).isEqualTo(5);
        assertThat(loteVigente.getStockActual()).isEqualTo(5); // 10 - 5
    }

    // ── T-O1e: FEFO usa solo lotes vigentes aunque consistencia incluyó vencidos

    @Test
    void registrarConsumo_consistenciaUsaTodos_fefoUsaSoloVigentes() {
        OrdenTrabajo ot = buildOt("ABIERTA");
        Producto p = buildProducto("PRODUCTO", 20);
        // stockSucursal = 18 = loteVencido(8) + loteVigente(10)
        ProductoStockSucursal ss = ProductoStockSucursal.builder()
                .id(50L).stockActual(18).build();

        LocalDate ayer   = LocalDate.now().minusDays(5);
        LocalDate manana = LocalDate.now().plusDays(60);
        StockLote loteVencido = buildLote(50L, 8, ayer);
        StockLote loteVigente = buildLote(51L, 10, manana);
        OtConsumoMaterial consumo = buildSavedConsumo(ot, p);

        when(otRepository.findByIdAndTenantIdForUpdate(OT_ID, TENANT_A)).thenReturn(Optional.of(ot));
        when(productoRepository.findByIdForUpdate(100L)).thenReturn(Optional.of(p));
        when(stockSucursalRepository.findByProductoIdAndSucursalIdForUpdate(100L, 10L))
                .thenReturn(Optional.of(ss));
        when(stockLoteRepository.existsByProductoIdAndTenantId(100L, TENANT_A)).thenReturn(true);
        when(stockLoteRepository.sumStockTotalLotesBySucursal(100L, TENANT_A, 10L)).thenReturn(18);
        when(stockLoteRepository.sumStockVigenteConSucursal(eq(100L), eq(TENANT_A), eq(10L), any())).thenReturn(10);
        when(stockLoteRepository.findVigentesFefoConSucursal(eq(100L), eq(TENANT_A), eq(10L), any()))
                .thenReturn(List.of(loteVigente));
        when(stockLoteRepository.findByIdForUpdate(51L)).thenReturn(Optional.of(loteVigente));
        when(stockLoteRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));
        when(usuarioRepository.findById(USER_ID)).thenReturn(Optional.of(buildUsuario()));
        when(movimientoRepository.save(any())).thenReturn(MovimientoInventario.builder().build());
        when(consumoRepository.save(any())).thenReturn(consumo);
        when(consumoLoteRepository.saveAll(any())).thenReturn(List.of());

        service.registrarConsumo(OT_ID, buildReq(5));

        // O1 usó sumStockTotalLotesBySucursal (todos)
        verify(stockLoteRepository).sumStockTotalLotesBySucursal(100L, TENANT_A, 10L);
        // FEFO usó findVigentesFefoConSucursal (solo vigentes)
        verify(stockLoteRepository).findVigentesFefoConSucursal(eq(100L), eq(TENANT_A), eq(10L), any());
        // El lote vencido no fue tocado por FEFO
        assertThat(loteVencido.getStockActual()).isEqualTo(8);
        assertThat(loteVigente.getStockActual()).isEqualTo(5); // 10 - 5
    }

    // ══════════════════════════════════════════════════════════════════════════
    // Tests para getConsumos
    // ══════════════════════════════════════════════════════════════════════════

    // ── GC-1: OT de otro tenant → ResourceNotFoundException ──────────────────

    @Test
    void getConsumos_otOtroTenant_lanzaResourceNotFoundException() {
        when(otRepository.findByIdAndTenantId(OT_ID, TENANT_A)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> service.getConsumos(OT_ID))
                .isInstanceOf(ResourceNotFoundException.class)
                .hasMessageContaining("Orden de trabajo no encontrada");

        verify(consumoRepository, never()).findByOrdenTrabajo_IdAndTenantIdOrderByCreatedAtAsc(anyLong(), anyString());
    }

    // ── GC-2: OT sin consumos → lista vacía ───────────────────────────────────

    @Test
    void getConsumos_sinConsumos_devuelveListaVacia() {
        OrdenTrabajo ot = buildOt("ABIERTA");
        when(otRepository.findByIdAndTenantId(OT_ID, TENANT_A)).thenReturn(Optional.of(ot));
        when(consumoRepository.findByOrdenTrabajo_IdAndTenantIdOrderByCreatedAtAsc(OT_ID, TENANT_A))
                .thenReturn(List.of());

        List<OtConsumoMaterialDTO> result = service.getConsumos(OT_ID);

        assertThat(result).isNotNull().isEmpty();
        verify(productoRepository, never()).findAllById(any());
    }

    // ── GC-3: Mapeo correcto de campos desnormalizados ────────────────────────

    @Test
    void getConsumos_mapeoProductoNombreYRegistradoPorNombre() {
        OrdenTrabajo ot = buildOt("EN_PROCESO");
        Producto prod = buildProducto("PRODUCTO", 10);
        prod.setNombre("Amoxicilina 500mg");

        OtConsumoMaterial consumo = OtConsumoMaterial.builder()
                .id(200L).tenantId(TENANT_A).ordenTrabajo(ot)
                .productoId(prod.getId()).presentacionId(null)
                .factorPresentacion(1).cantidadPresentacion(3).cantidadBase(3)
                .registradoPor(USER_ID).createdAt(java.time.LocalDateTime.now())
                .build();

        Usuario usuario = buildUsuario();
        usuario.setNombre("Dr. Pérez");

        when(otRepository.findByIdAndTenantId(OT_ID, TENANT_A)).thenReturn(Optional.of(ot));
        when(consumoRepository.findByOrdenTrabajo_IdAndTenantIdOrderByCreatedAtAsc(OT_ID, TENANT_A))
                .thenReturn(List.of(consumo));
        when(productoRepository.findAllById(Set.of(prod.getId()))).thenReturn(List.of(prod));
        when(presentacionRepository.findByIdInWithUnidadMedida(Set.of())).thenReturn(List.of());
        when(consumoLoteRepository.findByConsumoIdInOrderByCreatedAtAsc(Set.of(200L))).thenReturn(List.of());
        when(devolucionRepository.findByConsumoIdInOrderByCreatedAtAsc(any())).thenReturn(List.of());
        when(devolucionRepository.sumCantidadDevueltaGroupByConsumoId(any())).thenReturn(List.of());
        when(clasificacionRepository.findByConsumoIdInOrderByCreatedAtAsc(any())).thenReturn(List.of());
        when(clasificacionRepository.sumCantidadClasificadaGroupByConsumoId(any())).thenReturn(List.of());
        when(usuarioRepository.findAllById(Set.of(USER_ID))).thenReturn(List.of(usuario));

        List<OtConsumoMaterialDTO> result = service.getConsumos(OT_ID);

        assertThat(result).hasSize(1);
        OtConsumoMaterialDTO dto = result.get(0);
        assertThat(dto.getProductoNombre()).isEqualTo("Amoxicilina 500mg");
        assertThat(dto.getPresentacionNombre()).isNull();
        assertThat(dto.getRegistradoPorNombre()).isEqualTo("Dr. Pérez");
        assertThat(dto.getLotes()).isEmpty();
    }

    // ── GC-4: Mapeo correcto de presentacionNombre con unidadMedida ───────────

    @Test
    void getConsumos_mapeoPresentacionNombreConUnidadMedida() {
        OrdenTrabajo ot = buildOt("ABIERTA");
        Producto prod = buildProducto("PRODUCTO", 20);
        prod.setNombre("Paracetamol");

        UnidadMedida um = UnidadMedida.builder().id(5L).nombre("CAJA").build();
        ProductoPresentacion pres = ProductoPresentacion.builder()
                .id(77L).producto(prod).factor(20).unidadMedida(um).build();

        OtConsumoLote lote = OtConsumoLote.builder()
                .id(1L).consumoId(200L).stockLoteId(50L).cantidad(2)
                .tenantId(TENANT_A).productoId(prod.getId())
                .createdAt(java.time.LocalDateTime.now()).build();

        OtConsumoMaterial consumo = OtConsumoMaterial.builder()
                .id(200L).tenantId(TENANT_A).ordenTrabajo(ot)
                .productoId(prod.getId()).presentacionId(77L)
                .factorPresentacion(20).cantidadPresentacion(1).cantidadBase(20)
                .registradoPor(null).createdAt(java.time.LocalDateTime.now())
                .build();

        when(otRepository.findByIdAndTenantId(OT_ID, TENANT_A)).thenReturn(Optional.of(ot));
        when(consumoRepository.findByOrdenTrabajo_IdAndTenantIdOrderByCreatedAtAsc(OT_ID, TENANT_A))
                .thenReturn(List.of(consumo));
        when(productoRepository.findAllById(Set.of(prod.getId()))).thenReturn(List.of(prod));
        when(presentacionRepository.findByIdInWithUnidadMedida(Set.of(77L))).thenReturn(List.of(pres));
        when(consumoLoteRepository.findByConsumoIdInOrderByCreatedAtAsc(Set.of(200L))).thenReturn(List.of(lote));
        when(devolucionRepository.findByConsumoIdInOrderByCreatedAtAsc(any())).thenReturn(List.of());
        when(devolucionRepository.sumCantidadDevueltaGroupByConsumoId(any())).thenReturn(List.of());
        when(clasificacionRepository.findByConsumoIdInOrderByCreatedAtAsc(any())).thenReturn(List.of());
        when(clasificacionRepository.sumCantidadClasificadaGroupByConsumoId(any())).thenReturn(List.of());
        when(usuarioRepository.findAllById(Set.of())).thenReturn(List.of());

        List<OtConsumoMaterialDTO> result = service.getConsumos(OT_ID);

        assertThat(result).hasSize(1);
        OtConsumoMaterialDTO dto = result.get(0);
        assertThat(dto.getPresentacionNombre()).isEqualTo("CAJA ×20");
        assertThat(dto.getRegistradoPorNombre()).isNull();
        assertThat(dto.getLotes()).hasSize(1);
        assertThat(dto.getLotes().get(0).getStockLoteId()).isEqualTo(50L);
        assertThat(dto.getLotes().get(0).getCantidad()).isEqualTo(2);
    }

    // ── GC-5: registradoPor usa email cuando nombre es null ──────────────────

    @Test
    void getConsumos_registradoPorSinNombre_usaEmail() {
        OrdenTrabajo ot = buildOt("ABIERTA");
        Producto prod = buildProducto("SERVICIO", 0);
        prod.setNombre("Revisión general");

        OtConsumoMaterial consumo = OtConsumoMaterial.builder()
                .id(201L).tenantId(TENANT_A).ordenTrabajo(ot)
                .productoId(prod.getId()).presentacionId(null)
                .factorPresentacion(1).cantidadPresentacion(1).cantidadBase(1)
                .registradoPor(USER_ID).createdAt(java.time.LocalDateTime.now())
                .build();

        Usuario usuario = buildUsuario();
        usuario.setNombre(null);
        usuario.setEmail("tecnico@empresa.com");

        when(otRepository.findByIdAndTenantId(OT_ID, TENANT_A)).thenReturn(Optional.of(ot));
        when(consumoRepository.findByOrdenTrabajo_IdAndTenantIdOrderByCreatedAtAsc(OT_ID, TENANT_A))
                .thenReturn(List.of(consumo));
        when(productoRepository.findAllById(Set.of(prod.getId()))).thenReturn(List.of(prod));
        when(presentacionRepository.findByIdInWithUnidadMedida(Set.of())).thenReturn(List.of());
        when(consumoLoteRepository.findByConsumoIdInOrderByCreatedAtAsc(Set.of(201L))).thenReturn(List.of());
        when(devolucionRepository.findByConsumoIdInOrderByCreatedAtAsc(any())).thenReturn(List.of());
        when(devolucionRepository.sumCantidadDevueltaGroupByConsumoId(any())).thenReturn(List.of());
        when(clasificacionRepository.findByConsumoIdInOrderByCreatedAtAsc(any())).thenReturn(List.of());
        when(clasificacionRepository.sumCantidadClasificadaGroupByConsumoId(any())).thenReturn(List.of());
        when(usuarioRepository.findAllById(Set.of(USER_ID))).thenReturn(List.of(usuario));

        List<OtConsumoMaterialDTO> result = service.getConsumos(OT_ID);

        assertThat(result).hasSize(1);
        assertThat(result.get(0).getRegistradoPorNombre()).isEqualTo("tecnico@empresa.com");
    }

    // ── FT-3: Presentación factor > 1 + lotes — descuento usa cantidadBase ──

    @Test
    void registrarConsumo_presentacionFactorMayorUno_conLotes_descontaCantidadBase() {
        OrdenTrabajo ot = buildOt("ABIERTA");
        Producto p = buildProducto("PRODUCTO", 60);

        Producto prodPres = Producto.builder().id(100L).build();
        ProductoPresentacion pres = ProductoPresentacion.builder()
                .id(88L).producto(prodPres).factor(4).build();

        LocalDate manana = LocalDate.now().plusDays(30);
        StockLote lote = buildLote(30L, 20, manana);

        OtConsumoMaterial consumo = OtConsumoMaterial.builder()
                .id(200L).tenantId(TENANT_A).ordenTrabajo(ot)
                .productoId(100L).factorPresentacion(4)
                .cantidadPresentacion(3).cantidadBase(12)
                .createdAt(java.time.LocalDateTime.now()).build();

        RegistrarConsumoOtRequestDTO req = RegistrarConsumoOtRequestDTO.builder()
                .productoId(100L).presentacionId(88L).cantidadPresentacion(3).build();

        when(otRepository.findByIdAndTenantIdForUpdate(OT_ID, TENANT_A)).thenReturn(Optional.of(ot));
        when(productoRepository.findByIdForUpdate(100L)).thenReturn(Optional.of(p));
        when(presentacionRepository.findById(88L)).thenReturn(Optional.of(pres));
        when(stockSucursalRepository.findByProductoIdAndSucursalIdForUpdate(100L, 10L))
                .thenReturn(Optional.empty());
        when(stockLoteRepository.existsByProductoIdAndTenantId(100L, TENANT_A)).thenReturn(true);
        when(stockLoteRepository.sumStockVigenteConSucursal(eq(100L), eq(TENANT_A), eq(10L), any())).thenReturn(20);
        when(stockLoteRepository.findVigentesFefoConSucursal(eq(100L), eq(TENANT_A), eq(10L), any()))
                .thenReturn(List.of(lote));
        when(stockLoteRepository.findByIdForUpdate(30L)).thenReturn(Optional.of(lote));
        when(stockLoteRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));
        when(usuarioRepository.findById(USER_ID)).thenReturn(Optional.of(buildUsuario()));
        when(movimientoRepository.save(any())).thenReturn(MovimientoInventario.builder().build());
        when(consumoRepository.save(any())).thenReturn(consumo);
        when(consumoLoteRepository.saveAll(any())).thenReturn(List.of());

        OtConsumoMaterialDTO result = service.registrarConsumo(OT_ID, req);

        // cantidadBase = 3 presentaciones × factor 4 = 12
        assertThat(result.getCantidadBase()).isEqualTo(12);
        assertThat(result.getCantidadPresentacion()).isEqualTo(3);
        assertThat(result.getFactorPresentacion()).isEqualTo(4);
        // El lote se descuenta con cantidadBase (12), no cantidadPresentacion (3)
        assertThat(lote.getStockActual()).isEqualTo(8); // 20 - 12
    }
}
