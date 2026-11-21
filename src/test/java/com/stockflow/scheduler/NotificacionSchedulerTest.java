package com.stockflow.scheduler;

import com.stockflow.dto.ProductoStockInfo;
import com.stockflow.entity.OrdenCompra;
import com.stockflow.entity.Producto;
import com.stockflow.entity.Proveedor;
import com.stockflow.repository.CertificadoRepository;
import com.stockflow.repository.MovimientoInventarioRepository;
import com.stockflow.repository.NotificacionRepository;
import com.stockflow.repository.OrdenCompraRepository;
import com.stockflow.repository.ProductoRepository;
import com.stockflow.repository.SuscripcionRepository;
import com.stockflow.repository.TenantRepository;
import com.stockflow.repository.UsuarioRepository;
import com.stockflow.service.EmailService;
import com.stockflow.service.NotificacionService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;

import java.time.LocalDate;
import java.util.List;

import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
@DisplayName("NotificacionScheduler — optimización F-02 + aislamiento multi-tenant")
class NotificacionSchedulerTest {

    @Mock private TenantRepository               tenantRepository;
    @Mock private ProductoRepository             productoRepository;
    @Mock private MovimientoInventarioRepository movimientoRepository;
    @Mock private OrdenCompraRepository          ordenCompraRepository;
    @Mock private SuscripcionRepository          suscripcionRepository;
    @Mock private CertificadoRepository          certificadoRepository;
    @Mock private NotificacionService            notificacionService;
    @Mock private NotificacionRepository         notificacionRepository;
    @Mock private EmailService                   emailService;
    @Mock private UsuarioRepository              usuarioRepository;

    private NotificacionScheduler scheduler;

    @BeforeEach
    void setUp() {
        scheduler = new NotificacionScheduler(
                tenantRepository, productoRepository, movimientoRepository,
                ordenCompraRepository, suscripcionRepository, certificadoRepository,
                notificacionService, notificacionRepository, emailService, usuarioRepository
        );
        // Por defecto yaExisteNoLeida devuelve false (no hay duplicados)
        when(notificacionService.yaExisteNoLeida(any(), any(), any(), any())).thenReturn(false);
    }

    // ── Helpers ───────────────────────────────────────────────────────────────

    private ProductoStockInfo stockInfo(long id, String nombre, int stock, int minimo) {
        return new ProductoStockInfo() {
            public Long    getId()           { return id; }
            public String  getNombre()       { return nombre; }
            public Integer getStockActual()  { return stock; }
            public Integer getStockMinimo()  { return minimo; }
        };
    }

    private Producto producto(long id, String nombre, int stock) {
        return Producto.builder().id(id).nombre(nombre).stockActual(stock).build();
    }

    private OrdenCompra ordenCompra(long id, String tenantId, String nombreProveedor) {
        Proveedor prov = new Proveedor();
        prov.setNombre(nombreProveedor);
        OrdenCompra oc = new OrdenCompra();
        oc.setId(id);
        oc.setTenantId(tenantId);
        oc.setProveedor(prov);
        return oc;
    }

    // ── Casos de test ─────────────────────────────────────────────────────────

    @Test
    @DisplayName("NOT-01: Ningún tenant activo → no se consultan productos, no se crean notificaciones")
    void not01_sinTenantsActivos_noCalls() {
        when(tenantRepository.findAllActiveTenantIds()).thenReturn(List.of());

        scheduler.verificarStockBajo();

        verify(productoRepository, never()).findProductosBajoStockParaScheduler(any());
        verify(notificacionService, never()).notificarRoles(any(), any(), any(), any(), any(), any(), any());
    }

    @Test
    @DisplayName("NOT-02: 1 tenant activo, producto con stock bajo → notificación STOCK_BAJO creada")
    void not02_unTenantStockBajo_notifica() {
        when(tenantRepository.findAllActiveTenantIds()).thenReturn(List.of("t1"));
        when(productoRepository.findProductosBajoStockParaScheduler("t1"))
                .thenReturn(List.of(stockInfo(10L, "Paracetamol", 3, 10)));

        scheduler.verificarStockBajo();

        verify(notificacionService, atLeastOnce())
                .notificarRoles(eq("t1"), any(), eq("STOCK_BAJO"), contains("Paracetamol"),
                        any(), eq(10L), eq("PRODUCTO"));
    }

    @Test
    @DisplayName("NOT-03: Varios tenants activos → query de stock llamada por cada tenant")
    void not03_variosTenants_queriesPorCadaUno() {
        when(tenantRepository.findAllActiveTenantIds()).thenReturn(List.of("t1", "t2", "t3"));
        when(productoRepository.findProductosBajoStockParaScheduler(any())).thenReturn(List.of());

        scheduler.verificarStockBajo();

        verify(productoRepository, times(3)).findProductosBajoStockParaScheduler(any());
        verify(productoRepository).findProductosBajoStockParaScheduler("t1");
        verify(productoRepository).findProductosBajoStockParaScheduler("t2");
        verify(productoRepository).findProductosBajoStockParaScheduler("t3");
    }

    @Test
    @DisplayName("NOT-04: findAllActiveTenantIds excluye inactivos — solo activos reciben queries")
    void not04_inactivosExcluidos_noReciben() {
        // findAllActiveTenantIds ya filtra, el scheduler recibe solo activos
        when(tenantRepository.findAllActiveTenantIds()).thenReturn(List.of("activo1"));
        when(productoRepository.findProductosBajoStockParaScheduler(any())).thenReturn(List.of());

        scheduler.verificarStockBajo();

        verify(productoRepository).findProductosBajoStockParaScheduler("activo1");
        // "inactivo" nunca llegó al scheduler
        verify(productoRepository, never()).findProductosBajoStockParaScheduler("inactivo");
    }

    @Test
    @DisplayName("NOT-05: Error al consultar un tenant → el scheduler no explota")
    void not05_errorEnTenant_noExplota() {
        when(tenantRepository.findAllActiveTenantIds()).thenReturn(List.of("t1"));
        when(productoRepository.findProductosBajoStockParaScheduler("t1"))
                .thenThrow(new RuntimeException("DB timeout"));

        // No debe lanzar excepción
        scheduler.verificarStockBajo();
    }

    @Test
    @DisplayName("NOT-06: Error en t1 no interrumpe t2 — t2 recibe su notificación")
    void not06_errorEnT1_continuaConT2() {
        when(tenantRepository.findAllActiveTenantIds()).thenReturn(List.of("t1", "t2"));
        when(productoRepository.findProductosBajoStockParaScheduler("t1"))
                .thenThrow(new RuntimeException("DB timeout"));
        when(productoRepository.findProductosBajoStockParaScheduler("t2"))
                .thenReturn(List.of(stockInfo(99L, "Ibuprofeno", 1, 5)));

        scheduler.verificarStockBajo();

        verify(notificacionService, atLeastOnce()).notificarRoles(eq("t2"), any(), eq("STOCK_BAJO"),
                any(), any(), eq(99L), eq("PRODUCTO"));
    }

    @Test
    @DisplayName("NOT-07: yaExisteNoLeida devuelve true → no se duplica la notificación")
    void not07_noDuplicarNotificaciones() {
        when(tenantRepository.findAllActiveTenantIds()).thenReturn(List.of("t1"));
        when(productoRepository.findProductosBajoStockParaScheduler("t1"))
                .thenReturn(List.of(stockInfo(5L, "Amoxicilina", 2, 10)));
        // Simula que ya existe notificación no leída para este producto
        when(notificacionService.yaExisteNoLeida("t1", "ADMIN", "STOCK_BAJO", 5L)).thenReturn(true);
        when(notificacionService.yaExisteNoLeida("t1", "GESTOR_INVENTARIO", "STOCK_BAJO", 5L)).thenReturn(true);

        scheduler.verificarStockBajo();

        verify(notificacionService, never()).notificarRoles(any(), any(), eq("STOCK_BAJO"), any(), any(), any(), any());
    }

    @Test
    @DisplayName("NOT-08: Reglas de stock bajo — mensaje contiene nombre y cantidades correctas")
    void not08_reglasStockBajo_mensajeCorrecto() {
        when(tenantRepository.findAllActiveTenantIds()).thenReturn(List.of("t1"));
        when(productoRepository.findProductosBajoStockParaScheduler("t1"))
                .thenReturn(List.of(stockInfo(7L, "Vitamina C", 4, 20)));

        scheduler.verificarStockBajo();

        verify(notificacionService, atLeastOnce()).notificarRoles(
                eq("t1"), any(), eq("STOCK_BAJO"),
                contains("Vitamina C"),
                argThat(msg -> msg.contains("4") && msg.contains("Vitamina C") && msg.contains("20")),
                eq(7L), eq("PRODUCTO")
        );
    }

    @Test
    @DisplayName("NOT-09: Reglas sin movimiento — PRODUCTO_SIN_MOVIMIENTO creado para slow mover")
    void not09_productoSinMovimiento_notifica() {
        when(tenantRepository.findAllActiveTenantIds()).thenReturn(List.of("t1"));
        when(productoRepository.findSlowMovers(eq("t1"), any()))
                .thenReturn(List.of(producto(3L, "Desparasitante", 15)));

        scheduler.verificarProductosSinMovimiento();

        verify(notificacionService).notificarRoles(
                eq("t1"), any(), eq("PRODUCTO_SIN_MOVIMIENTO"),
                contains("Desparasitante"), any(), eq(3L), eq("PRODUCTO")
        );
    }

    @Test
    @DisplayName("NOT-10: Alerta de vencimiento — PRODUCTO_VENCIDO creado para lote vencido")
    void not10_loteVencido_notificaProductoVencido() {
        when(tenantRepository.findAllActiveTenantIds()).thenReturn(List.of("t1"));
        when(movimientoRepository.findProductosConLoteVencido(eq("t1"), any(LocalDate.class)))
                .thenReturn(List.of(producto(8L, "Insulina vencida", 5)));
        when(movimientoRepository.findProductosConLotePorVencer(eq("t1"), any(), any()))
                .thenReturn(List.of());

        scheduler.verificarVencimientos();

        verify(notificacionService, atLeastOnce()).notificarRoles(
                eq("t1"), any(), eq("PRODUCTO_VENCIDO"),
                contains("Insulina vencida"), any(), eq(8L), eq("PRODUCTO")
        );
    }

    @Test
    @DisplayName("NOT-11: Alerta OC pendiente — OC_SIN_RECEPCIONAR creada para OC sin recepcionar")
    void not11_ocSinRecepcionar_notifica() {
        when(tenantRepository.findAllActiveTenantIds()).thenReturn(List.of("t1"));
        when(ordenCompraRepository.findEnviadasSinRecepcionar(eq("t1"), any()))
                .thenReturn(List.of(ordenCompra(42L, "t1", "Proveedor ABC")));

        scheduler.verificarOCsSinRecepcionar();

        verify(notificacionService, atLeastOnce()).notificarRoles(
                eq("t1"), any(), eq("OC_SIN_RECEPCIONAR"),
                any(), contains("Proveedor ABC"), eq(42L), eq("ORDEN_COMPRA")
        );
    }

    @Test
    @DisplayName("NOT-12: Aislamiento multi-tenant — notificaciones de t1 no se mezclan con t2")
    void not12_aislamientoMultiTenant_notificacionesSeparadas() {
        when(tenantRepository.findAllActiveTenantIds()).thenReturn(List.of("t1", "t2"));
        when(productoRepository.findProductosBajoStockParaScheduler("t1"))
                .thenReturn(List.of(stockInfo(1L, "Producto-T1", 2, 10)));
        when(productoRepository.findProductosBajoStockParaScheduler("t2"))
                .thenReturn(List.of(stockInfo(2L, "Producto-T2", 3, 15)));

        scheduler.verificarStockBajo();

        // t1 solo recibe notificación con su tenantId y su productoId
        verify(notificacionService, atLeastOnce()).notificarRoles(
                eq("t1"), any(), eq("STOCK_BAJO"), any(), any(), eq(1L), eq("PRODUCTO"));
        // t2 solo recibe notificación con su tenantId y su productoId
        verify(notificacionService, atLeastOnce()).notificarRoles(
                eq("t2"), any(), eq("STOCK_BAJO"), any(), any(), eq(2L), eq("PRODUCTO"));
        // t1 nunca recibe notificación para el producto de t2 (id=2)
        verify(notificacionService, never()).notificarRoles(
                eq("t1"), any(), any(), any(), any(), eq(2L), any());
        // t2 nunca recibe notificación para el producto de t1 (id=1)
        verify(notificacionService, never()).notificarRoles(
                eq("t2"), any(), any(), any(), any(), eq(1L), any());
    }
}
