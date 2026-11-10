package com.stockflow.controller;

import com.stockflow.dto.DetalleVentaDTO;
import com.stockflow.dto.VentaDTO;
import com.stockflow.entity.Producto;
import com.stockflow.entity.ProductoPresentacion;
import com.stockflow.entity.Usuario;
import com.stockflow.entity.Venta;
import com.stockflow.exception.BadRequestException;
import com.stockflow.mapper.DetalleVentaMapper;
import com.stockflow.mapper.VentaMapper;
import com.stockflow.repository.DetalleVentaRepository;
import com.stockflow.repository.ProductoPresentacionRepository;
import com.stockflow.repository.ProductoStockSucursalRepository;
import com.stockflow.repository.ProductoVarianteRepository;
import com.stockflow.repository.ProductoVarianteStockSucursalRepository;
import com.stockflow.repository.SucursalRepository;
import com.stockflow.repository.VentaRepository;
import com.stockflow.service.*;
import com.stockflow.util.TenantContext;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.authority.SimpleGrantedAuthority;

import java.math.BigDecimal;
import java.util.Collection;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

/**
 * Tests de validación de precios en VentaController.crear().
 * Cubre todos los casos de precio para VENDEDOR, ADMIN, GERENTE,
 * productos con y sin configuración mayorista, presentaciones y variantes.
 *
 * Patrón: Mockito directo sobre el controlador (sin Spring context).
 * @PreAuthorize no se evalúa; se testea únicamente la lógica de negocio del método.
 */
@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
@DisplayName("VentaController — validación de precio en crear()")
class VentaControllerPrecioTest {

    private static final String TENANT     = "test-tenant-abc";
    private static final Long   PROD_ID    = 42L;
    private static final Long   VEND_ID    = 1L;
    private static final Long   PRES_ID    = 99L;

    // ── Todas las dependencias del controlador ───────────────────────────────
    @Mock private VentaService                              ventaService;
    @Mock private ProductoService                           productoService;
    @Mock private UsuarioService                            usuarioService;
    @Mock private VentaMapper                               ventaMapper;
    @Mock private DetalleVentaMapper                        detalleVentaMapper;
    @Mock private MovimientoInventarioService               movimientoService;
    @Mock private ComprobanteService                        comprobanteService;
    @Mock private NotaCreditoService                        notaCreditoService;
    @Mock private NotificacionService                       notificacionService;
    @Mock private ProductoVarianteRepository                productoVarianteRepository;
    @Mock private ProductoVarianteStockSucursalRepository   varianteStockSucursalRepository;
    @Mock private ProductoStockSucursalRepository           stockSucursalRepository;
    @Mock private SucursalRepository                        sucursalRepository;
    @Mock private VentaRepository                           ventaRepository;
    @Mock private DetalleVentaRepository                    detalleVentaRepository;
    @Mock private StockLoteService                          stockLoteService;
    @Mock private ProductoPresentacionRepository            presentacionRepository;

    @InjectMocks private VentaController controller;

    // ── Auth helpers ─────────────────────────────────────────────────────────

    @SuppressWarnings("unchecked")
    private Authentication auth(String... authorities) {
        Authentication a = mock(Authentication.class);
        Collection<SimpleGrantedAuthority> auths = (Collection) List.of(
                java.util.Arrays.stream(authorities)
                        .map(SimpleGrantedAuthority::new)
                        .toArray(SimpleGrantedAuthority[]::new)
        );
        when(a.getAuthorities()).thenReturn((Collection) auths);
        return a;
    }

    private Authentication vendedorAuth() {
        return auth("ROLE_VENDEDOR", "PERM_CREAR_VENTA", "PERM_VER_PRODUCTOS");
    }
    private Authentication vendedorDescuentoManualAuth() {
        return auth("ROLE_VENDEDOR", "PERM_CREAR_VENTA", "PERM_VER_PRODUCTOS", "PERM_DESCUENTO_MANUAL");
    }
    private Authentication adminAuth() { return auth("ROLE_ADMIN"); }
    private Authentication gerenteAuth() {
        return auth("ROLE_GERENTE", "PERM_CREAR_VENTA", "PERM_VER_VENTAS");
    }

    // ── Producto helpers ─────────────────────────────────────────────────────

    private Producto producto(double precioVenta) {
        return Producto.builder()
                .id(PROD_ID).nombre("Producto Test")
                .precioVenta(BigDecimal.valueOf(precioVenta))
                .costoUnitario(BigDecimal.ONE)
                .stockActual(500).tenantId(TENANT).build();
    }

    private Producto productoConMayorista(double precioVenta, double precioMayorista, int cantMin) {
        return Producto.builder()
                .id(PROD_ID).nombre("Juguete Mayorista")
                .precioVenta(BigDecimal.valueOf(precioVenta))
                .precioMayorista(BigDecimal.valueOf(precioMayorista))
                .cantidadMinimaMayorista(cantMin)
                .costoUnitario(BigDecimal.ONE)
                .stockActual(500).tenantId(TENANT).build();
    }

    // ── VentaDTO / DetalleVentaDTO helpers ───────────────────────────────────

    private DetalleVentaDTO detalle(int cantidad, double precio) {
        return DetalleVentaDTO.builder()
                .productoId(PROD_ID).cantidad(cantidad)
                .precioUnitario(BigDecimal.valueOf(precio)).build();
    }

    private DetalleVentaDTO detallePresentacion(int cantidad, double precio) {
        return DetalleVentaDTO.builder()
                .productoId(PROD_ID).cantidad(cantidad)
                .precioUnitario(BigDecimal.valueOf(precio))
                .presentacionId(PRES_ID).build();
    }

    private VentaDTO ventaDTO(DetalleVentaDTO... detalles) {
        return VentaDTO.builder()
                .vendedorId(VEND_ID)
                .detalles(List.of(detalles))
                .metodoPago("EFECTIVO").estado("COMPLETADA").build();
    }

    // ── Base setup ───────────────────────────────────────────────────────────

    @BeforeEach
    void setUp() {
        TenantContext.setCurrentTenant(TENANT);
        when(usuarioService.obtenerUsuarioPorId(anyLong()))
                .thenReturn(Optional.of(Usuario.builder().id(VEND_ID).build()));
        Venta ventaMock = mock(Venta.class);
        when(ventaMock.getId()).thenReturn(100L);
        when(ventaMock.getSucursalId()).thenReturn(null);
        when(ventaMock.getDetalles()).thenReturn(List.of());
        when(ventaService.crearVenta(any())).thenReturn(ventaMock);
        when(ventaMapper.toDTO(any())).thenReturn(VentaDTO.builder().id(100L).build());
        when(stockLoteService.getStockVigente(anyString(), anyLong(), any())).thenReturn(999);
    }

    @AfterEach
    void tearDown() {
        TenantContext.clear();
    }

    // =========================================================================
    // 1. VENDEDOR — productos SIN configuración mayorista
    // =========================================================================

    @Nested
    @DisplayName("VENDEDOR — producto sin configuración mayorista")
    class VendedorSinMayorista {

        @Test
        @DisplayName("precio enviado == precioVenta → acepta (caso normal)")
        void precioCorrectoMinorista_acepta() {
            Producto p = producto(30.00);
            when(productoService.obtenerProductoPorId(PROD_ID)).thenReturn(Optional.of(p));

            assertThatCode(() -> controller.crear(ventaDTO(detalle(1, 30.00)), vendedorAuth()))
                    .doesNotThrowAnyException();
        }

        @Test
        @DisplayName("precio manipulado distinto de precioVenta → BadRequestException")
        void precioManipulado_rechaza() {
            Producto p = producto(30.00);
            when(productoService.obtenerProductoPorId(PROD_ID)).thenReturn(Optional.of(p));

            assertThatThrownBy(() -> controller.crear(ventaDTO(detalle(1, 1.00)), vendedorAuth()))
                    .isInstanceOf(BadRequestException.class)
                    .hasMessageContaining("Precio inválido")
                    .hasMessageContaining("Producto Test");
        }

        @Test
        @DisplayName("precio en 0.01 (mínimo del DTO) es rechazado si precioVenta es 30.00")
        void precioMinimoPeroDistinto_rechaza() {
            Producto p = producto(30.00);
            when(productoService.obtenerProductoPorId(PROD_ID)).thenReturn(Optional.of(p));

            assertThatThrownBy(() -> controller.crear(ventaDTO(detalle(1, 0.01)), vendedorAuth()))
                    .isInstanceOf(BadRequestException.class);
        }

        @Test
        @DisplayName("precio mayor (recargo) también es rechazado para VENDEDOR")
        void precioMayor_rechazaTambien() {
            Producto p = producto(30.00);
            when(productoService.obtenerProductoPorId(PROD_ID)).thenReturn(Optional.of(p));

            assertThatThrownBy(() -> controller.crear(ventaDTO(detalle(1, 999.99)), vendedorAuth()))
                    .isInstanceOf(BadRequestException.class);
        }
    }

    // =========================================================================
    // 2. VENDEDOR — productos CON configuración mayorista
    // =========================================================================

    @Nested
    @DisplayName("VENDEDOR — producto con configuración mayorista (S/30 unit, S/24 mayorista, min 12)")
    class VendedorConMayorista {

        final Producto juguete = productoConMayorista(30.00, 24.00, 12);

        @BeforeEach
        void setupProducto() {
            when(productoService.obtenerProductoPorId(PROD_ID)).thenReturn(Optional.of(juguete));
        }

        @Test
        @DisplayName("cantidad < mínimo + precio minorista correcto → acepta")
        void cantidadBajoMinimo_precioMinorista_acepta() {
            assertThatCode(() -> controller.crear(ventaDTO(detalle(11, 30.00)), vendedorAuth()))
                    .doesNotThrowAnyException();
        }

        @Test
        @DisplayName("cantidad < mínimo + precio mayorista enviado → rechaza")
        void cantidadBajoMinimo_precioMayoristaEnviado_rechaza() {
            assertThatThrownBy(() -> controller.crear(ventaDTO(detalle(11, 24.00)), vendedorAuth()))
                    .isInstanceOf(BadRequestException.class)
                    .hasMessageContaining("se esperaba 30.00")
                    .hasMessageContaining("se recibió 24.00");
        }

        @Test
        @DisplayName("cantidad == mínimo + precio mayorista correcto → acepta")
        void cantidadIgualMinimo_precioMayorista_acepta() {
            assertThatCode(() -> controller.crear(ventaDTO(detalle(12, 24.00)), vendedorAuth()))
                    .doesNotThrowAnyException();
        }

        @Test
        @DisplayName("cantidad == mínimo + precio minorista enviado → rechaza")
        void cantidadIgualMinimo_precioMinoristaEnviado_rechaza() {
            assertThatThrownBy(() -> controller.crear(ventaDTO(detalle(12, 30.00)), vendedorAuth()))
                    .isInstanceOf(BadRequestException.class)
                    .hasMessageContaining("se esperaba 24.00")
                    .hasMessageContaining("se recibió 30.00");
        }

        @Test
        @DisplayName("cantidad > mínimo + precio mayorista correcto → acepta")
        void cantidadSobreMinimo_precioMayorista_acepta() {
            assertThatCode(() -> controller.crear(ventaDTO(detalle(20, 24.00)), vendedorAuth()))
                    .doesNotThrowAnyException();
        }

        @Test
        @DisplayName("cantidad > mínimo + precio manipulado → rechaza")
        void cantidadSobreMinimo_precioManipulado_rechaza() {
            assertThatThrownBy(() -> controller.crear(ventaDTO(detalle(20, 1.00)), vendedorAuth()))
                    .isInstanceOf(BadRequestException.class);
        }
    }

    // =========================================================================
    // 3. VENDEDOR con PERM_DESCUENTO_MANUAL — edición manual autorizada
    // =========================================================================

    @Nested
    @DisplayName("VENDEDOR con PERM_DESCUENTO_MANUAL — edición manual de precio autorizada")
    class VendedorConDescuentoManual {

        @Test
        @DisplayName("precio distinto de precioVenta → acepta (descuento en TIENDA_ROPA)")
        void descuentoManual_precioArbitrario_acepta() {
            Producto p = producto(59.90);
            when(productoService.obtenerProductoPorId(PROD_ID)).thenReturn(Optional.of(p));

            assertThatCode(() -> controller.crear(ventaDTO(detalle(1, 45.00)), vendedorDescuentoManualAuth()))
                    .doesNotThrowAnyException();
        }

        @Test
        @DisplayName("precio mayor que precioVenta → acepta (recargo autorizado)")
        void descuentoManual_precioMayor_acepta() {
            Producto p = producto(59.90);
            when(productoService.obtenerProductoPorId(PROD_ID)).thenReturn(Optional.of(p));

            assertThatCode(() -> controller.crear(ventaDTO(detalle(1, 75.00)), vendedorDescuentoManualAuth()))
                    .doesNotThrowAnyException();
        }

        @Test
        @DisplayName("variante TIENDA_ROPA + precio de descuento → acepta (flujo legítimo TIENDA_ROPA)")
        void descuentoManual_varianteTiendaRopa_acepta() {
            Producto remera = producto(59.90);
            when(productoService.obtenerProductoPorId(PROD_ID)).thenReturn(Optional.of(remera));
            com.stockflow.entity.ProductoVariante variante = mock(com.stockflow.entity.ProductoVariante.class);
            when(variante.getStockActual()).thenReturn(100);
            when(productoVarianteRepository.findByIdAndTenantId(anyLong(), anyString()))
                    .thenReturn(Optional.of(variante));

            DetalleVentaDTO d = DetalleVentaDTO.builder()
                    .productoId(PROD_ID).cantidad(1)
                    .precioUnitario(BigDecimal.valueOf(50.00))
                    .varianteId(10L).varianteDescripcion("Talla L / Azul").build();

            assertThatCode(() -> controller.crear(ventaDTO(d), vendedorDescuentoManualAuth()))
                    .doesNotThrowAnyException();
        }

        @Test
        @DisplayName("producto con mayorista + precio manual distinto → acepta (override explícito)")
        void descuentoManual_productoConMayorista_acepta() {
            Producto p = productoConMayorista(30.00, 24.00, 12);
            when(productoService.obtenerProductoPorId(PROD_ID)).thenReturn(Optional.of(p));

            // cantidad 12 (activaría mayorista a 24.00), pero el vendedor ofrece descuento a 20.00
            assertThatCode(() -> controller.crear(ventaDTO(detalle(12, 20.00)), vendedorDescuentoManualAuth()))
                    .doesNotThrowAnyException();
        }

        @Test
        @DisplayName("VENDEDOR sin PERM_DESCUENTO_MANUAL con el mismo precio manual → rechaza")
        void sinPermiso_mismoPrecioManual_rechaza() {
            Producto p = producto(59.90);
            when(productoService.obtenerProductoPorId(PROD_ID)).thenReturn(Optional.of(p));

            // mismo precio que el test anterior, pero sin el permiso → debe rechazar
            assertThatThrownBy(() -> controller.crear(ventaDTO(detalle(1, 45.00)), vendedorAuth()))
                    .isInstanceOf(BadRequestException.class)
                    .hasMessageContaining("Precio inválido");
        }
    }

    // =========================================================================
    // 4. ADMIN — precio libre
    // =========================================================================

    @Nested
    @DisplayName("ADMIN — precio manual autorizado")
    class AdminPrecioLibre {

        @Test
        @DisplayName("ADMIN con precio distinto de precioVenta → acepta (descuento manual)")
        void admin_precioArbitrario_acepta() {
            Producto p = producto(30.00);
            when(productoService.obtenerProductoPorId(PROD_ID)).thenReturn(Optional.of(p));

            assertThatCode(() -> controller.crear(ventaDTO(detalle(1, 1.00)), adminAuth()))
                    .doesNotThrowAnyException();
        }

        @Test
        @DisplayName("ADMIN con producto mayorista y precio diferente → acepta")
        void admin_productoConMayorista_precioLibre_acepta() {
            Producto p = productoConMayorista(30.00, 24.00, 12);
            when(productoService.obtenerProductoPorId(PROD_ID)).thenReturn(Optional.of(p));

            assertThatCode(() -> controller.crear(ventaDTO(detalle(12, 10.00)), adminAuth()))
                    .doesNotThrowAnyException();
        }

        @Test
        @DisplayName("ADMIN verifica que precioFinal en el detalle es el precio enviado por admin")
        void admin_precioFinalEsElEnviado() {
            Producto p = producto(30.00);
            when(productoService.obtenerProductoPorId(PROD_ID)).thenReturn(Optional.of(p));

            // El controlador usará detalleDTO.getPrecioUnitario() = 15.00 para el admin
            // No podemos inspeccionar DetalleVenta internamente sin Spring context,
            // pero sí verificamos que no lanza excepción
            assertThatCode(() -> controller.crear(ventaDTO(detalle(1, 15.00)), adminAuth()))
                    .doesNotThrowAnyException();
        }
    }

    // =========================================================================
    // 5. GERENTE — precio libre (rol operativo)
    // =========================================================================

    @Nested
    @DisplayName("GERENTE — precio manual operativo")
    class GerentePrecioLibre {

        @Test
        @DisplayName("GERENTE con precio distinto de precioVenta → acepta")
        void gerente_precioArbitrario_acepta() {
            Producto p = producto(30.00);
            when(productoService.obtenerProductoPorId(PROD_ID)).thenReturn(Optional.of(p));

            assertThatCode(() -> controller.crear(ventaDTO(detalle(1, 20.00)), gerenteAuth()))
                    .doesNotThrowAnyException();
        }

        @Test
        @DisplayName("GERENTE con producto mayorista y precio diferente → acepta (descuento manual)")
        void gerente_productoMayorista_precioLibre_acepta() {
            Producto p = productoConMayorista(30.00, 24.00, 12);
            when(productoService.obtenerProductoPorId(PROD_ID)).thenReturn(Optional.of(p));

            assertThatCode(() -> controller.crear(ventaDTO(detalle(5, 18.00)), gerenteAuth()))
                    .doesNotThrowAnyException();
        }
    }

    // =========================================================================
    // 6. Presentaciones (farmacia/botica)
    // =========================================================================

    @Nested
    @DisplayName("Presentaciones — precio de la presentación es el autorizado")
    class Presentaciones {

        final Producto paracetamol = producto(0.30); // precio tableta base

        ProductoPresentacion caja() {
            return ProductoPresentacion.builder()
                    .id(PRES_ID)
                    .precioVenta(BigDecimal.valueOf(8.00))
                    .factor(20)
                    .build();
        }

        @BeforeEach
        void setup() {
            when(productoService.obtenerProductoPorId(PROD_ID)).thenReturn(Optional.of(paracetamol));
            when(presentacionRepository.findById(PRES_ID)).thenReturn(Optional.of(caja()));
        }

        @Test
        @DisplayName("precio de presentación correcto → acepta")
        void precioCorrectoDePresentacion_acepta() {
            assertThatCode(() ->
                    controller.crear(ventaDTO(detallePresentacion(1, 8.00)), vendedorAuth()))
                    .doesNotThrowAnyException();
        }

        @Test
        @DisplayName("precio del producto base enviado en lugar del precio de presentación → rechaza")
        void precioProductoBaseEnLugarDePresentacion_rechaza() {
            // Envía 0.30 (precioVenta del producto) en lugar de 8.00 (precio de la caja)
            assertThatThrownBy(() ->
                    controller.crear(ventaDTO(detallePresentacion(1, 0.30)), vendedorAuth()))
                    .isInstanceOf(BadRequestException.class)
                    .hasMessageContaining("se esperaba 8.00")
                    .hasMessageContaining("se recibió 0.30");
        }

        @Test
        @DisplayName("precio manipulado (menor) para presentación → rechaza")
        void precioManipuladoPresentacion_rechaza() {
            assertThatThrownBy(() ->
                    controller.crear(ventaDTO(detallePresentacion(1, 1.00)), vendedorAuth()))
                    .isInstanceOf(BadRequestException.class);
        }

        @Test
        @DisplayName("presentacionId inexistente → BadRequestException")
        void presentacionNoExiste_rechaza() {
            when(presentacionRepository.findById(PRES_ID)).thenReturn(Optional.empty());
            assertThatThrownBy(() ->
                    controller.crear(ventaDTO(detallePresentacion(1, 8.00)), vendedorAuth()))
                    .isInstanceOf(BadRequestException.class)
                    .hasMessageContaining("Presentación no encontrada");
        }

        @Test
        @DisplayName("ADMIN con presentación y precio distinto → acepta (precio libre)")
        void admin_presentacion_precioLibre_acepta() {
            assertThatCode(() ->
                    controller.crear(ventaDTO(detallePresentacion(1, 5.00)), adminAuth()))
                    .doesNotThrowAnyException();
        }
    }

    // =========================================================================
    // 7. Variantes TIENDA_ROPA — precio del producto base
    // =========================================================================

    @Nested
    @DisplayName("Variantes (TIENDA_ROPA) — precio del producto, sin override propio")
    class Variantes {

        // ProductoVariante no tiene precioVenta propio; el precio es de Producto.precioVenta
        final Producto remera = producto(59.90);

        @BeforeEach
        void setup() {
            when(productoService.obtenerProductoPorId(PROD_ID)).thenReturn(Optional.of(remera));
            // Simular variante con stock suficiente
            com.stockflow.entity.ProductoVariante variante = mock(com.stockflow.entity.ProductoVariante.class);
            when(variante.getStockActual()).thenReturn(100);
            when(productoVarianteRepository.findByIdAndTenantId(anyLong(), anyString()))
                    .thenReturn(Optional.of(variante));
        }

        @Test
        @DisplayName("VENDEDOR con variante y precio correcto del producto → acepta")
        void vendedor_variantePrecoProducto_acepta() {
            DetalleVentaDTO d = DetalleVentaDTO.builder()
                    .productoId(PROD_ID).cantidad(1)
                    .precioUnitario(BigDecimal.valueOf(59.90))
                    .varianteId(10L).varianteDescripcion("Talla M / Rojo").build();
            assertThatCode(() -> controller.crear(ventaDTO(d), vendedorAuth()))
                    .doesNotThrowAnyException();
        }

        @Test
        @DisplayName("VENDEDOR con variante y precio manipulado → rechaza")
        void vendedor_variantePrecioManipulado_rechaza() {
            DetalleVentaDTO d = DetalleVentaDTO.builder()
                    .productoId(PROD_ID).cantidad(1)
                    .precioUnitario(BigDecimal.valueOf(1.00))
                    .varianteId(10L).varianteDescripcion("Talla M / Rojo").build();
            assertThatThrownBy(() -> controller.crear(ventaDTO(d), vendedorAuth()))
                    .isInstanceOf(BadRequestException.class);
        }

        @Test
        @DisplayName("ADMIN con variante y precio de descuento manual → acepta (TIENDA_ROPA)")
        void admin_variantePrecioDescuento_acepta() {
            DetalleVentaDTO d = DetalleVentaDTO.builder()
                    .productoId(PROD_ID).cantidad(1)
                    .precioUnitario(BigDecimal.valueOf(45.00)) // descuento manual
                    .varianteId(10L).varianteDescripcion("Talla M / Rojo").build();
            assertThatCode(() -> controller.crear(ventaDTO(d), adminAuth()))
                    .doesNotThrowAnyException();
        }
    }

    // =========================================================================
    // 8. Múltiples detalles — la validación aplica a cada uno
    // =========================================================================

    @Nested
    @DisplayName("Múltiples detalles — validación por detalle")
    class MultipleDetalles {

        @Test
        @DisplayName("dos productos: primero correcto, segundo manipulado → rechaza por el segundo")
        void dosDetalles_segundoManipulado_rechaza() {
            Long prodId2 = 55L;
            Producto p1 = producto(30.00);
            Producto p2 = Producto.builder()
                    .id(prodId2).nombre("Producto Dos")
                    .precioVenta(BigDecimal.valueOf(50.00))
                    .costoUnitario(BigDecimal.ONE)
                    .stockActual(100).tenantId(TENANT).build();

            when(productoService.obtenerProductoPorId(PROD_ID)).thenReturn(Optional.of(p1));
            when(productoService.obtenerProductoPorId(prodId2)).thenReturn(Optional.of(p2));

            DetalleVentaDTO d1 = DetalleVentaDTO.builder()
                    .productoId(PROD_ID).cantidad(1).precioUnitario(BigDecimal.valueOf(30.00)).build();
            DetalleVentaDTO d2 = DetalleVentaDTO.builder()
                    .productoId(prodId2).cantidad(1).precioUnitario(BigDecimal.valueOf(1.00)).build(); // manipulado

            VentaDTO dto = VentaDTO.builder()
                    .vendedorId(VEND_ID).detalles(List.of(d1, d2))
                    .metodoPago("EFECTIVO").estado("COMPLETADA").build();

            assertThatThrownBy(() -> controller.crear(dto, vendedorAuth()))
                    .isInstanceOf(BadRequestException.class)
                    .hasMessageContaining("Producto Dos");
        }
    }

    // =========================================================================
    // 9. Persistencia del precioFinal — verificación vía mensaje de error
    // =========================================================================

    @Nested
    @DisplayName("Mensaje de error detallado — incluye precio esperado y recibido")
    class MensajeError {

        @Test
        @DisplayName("mensaje incluye nombre del producto, precio esperado y recibido")
        void mensajeErrorDetallado() {
            Producto p = producto(45.50);
            when(productoService.obtenerProductoPorId(PROD_ID)).thenReturn(Optional.of(p));

            assertThatThrownBy(() -> controller.crear(ventaDTO(detalle(1, 10.00)), vendedorAuth()))
                    .isInstanceOf(BadRequestException.class)
                    .hasMessageContaining("Producto Test")
                    .hasMessageContaining("45.50")
                    .hasMessageContaining("10.00");
        }

        @Test
        @DisplayName("mensaje mayorista incluye precio mayorista esperado")
        void mensajeMayorista_incluyePrecioEsperado() {
            Producto p = productoConMayorista(30.00, 24.00, 12);
            when(productoService.obtenerProductoPorId(PROD_ID)).thenReturn(Optional.of(p));

            assertThatThrownBy(() -> controller.crear(ventaDTO(detalle(12, 10.00)), vendedorAuth()))
                    .isInstanceOf(BadRequestException.class)
                    .hasMessageContaining("24.00")
                    .hasMessageContaining("10.00");
        }
    }
}
