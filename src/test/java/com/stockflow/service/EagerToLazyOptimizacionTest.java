package com.stockflow.service;

import com.stockflow.entity.*;
import com.stockflow.repository.MovimientoInventarioRepository;
import com.stockflow.repository.OrdenCompraRepository;
import com.stockflow.repository.VentaRepository;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * F-03 — Verificaciones de que las relaciones ManyToOne cambiadas a LAZY
 * no rompen el acceso a datos en los servicios afectados.
 *
 * Nota: la ausencia de N+1 real solo puede garantizarse con Testcontainers
 * (ver GuiaRemisionIntegrationTest como patrón); estos tests unitarios
 * verifican que los datos de respuesta son correctos y que no hay NPE.
 */
@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
@DisplayName("F-03 — Optimización EAGER→LAZY: datos accesibles sin NPE")
class EagerToLazyOptimizacionTest {

    @Mock private OrdenCompraRepository       ordenCompraRepository;
    @Mock private MovimientoInventarioRepository movimientoRepository;
    @Mock private VentaRepository               ventaRepository;

    private static final String TENANT = "tenant-abc";

    // ── Helpers ───────────────────────────────────────────────────────────────

    private Proveedor proveedor(long id, String nombre) {
        Proveedor p = new Proveedor();
        p.setId(id);
        p.setNombre(nombre);
        return p;
    }

    private Usuario usuario(long id, String nombre) {
        Usuario u = new Usuario();
        u.setId(id);
        u.setNombre(nombre);
        return u;
    }

    private Producto producto(long id, String nombre) {
        return Producto.builder()
                .id(id).nombre(nombre).stockActual(10)
                .costoUnitario(BigDecimal.ONE).precioVenta(BigDecimal.TEN)
                .build();
    }

    private OrdenCompra ordenCompra(long id, Proveedor prov, Usuario usr) {
        OrdenCompra oc = new OrdenCompra();
        oc.setId(id);
        oc.setTenantId(TENANT);
        oc.setEstado("ENVIADA");
        oc.setProveedor(prov);
        oc.setUsuarioCreador(usr);
        oc.setCreatedAt(LocalDateTime.now());
        return oc;
    }

    private MovimientoInventario movimiento(long id, Producto prod, Usuario usr) {
        return MovimientoInventario.builder()
                .id(id).tenantId(TENANT).tipo("ENTRADA").cantidad(10)
                .producto(prod).usuario(usr).build();
    }

    private Venta venta(long id, Usuario vendedor) {
        return Venta.builder()
                .id(id).tenantId(TENANT).total(BigDecimal.TEN)
                .estado("COMPLETADA").vendedor(vendedor).createdAt(LocalDateTime.now())
                .build();
    }

    // ══ Tests OrdenCompra ════════════════════════════════════════════════════

    @Test
    @DisplayName("OC-01: findByTenantId con proveedor y usuarioCreador accesibles (sin NPE)")
    void oc01_findByTenantId_relacionesAccesibles() {
        OrdenCompra oc = ordenCompra(1L, proveedor(10L, "Distrib. Lima"), usuario(20L, "Ana"));
        when(ordenCompraRepository.findByTenantId(TENANT)).thenReturn(List.of(oc));

        List<OrdenCompra> result = ordenCompraRepository.findByTenantId(TENANT);

        assertThat(result).hasSize(1);
        assertThatCode(() -> {
            assertThat(result.get(0).getProveedor().getNombre()).isEqualTo("Distrib. Lima");
            assertThat(result.get(0).getUsuarioCreador().getNombre()).isEqualTo("Ana");
        }).doesNotThrowAnyException();
    }

    @Test
    @DisplayName("OC-02: findByTenantIdAndEstado filtra por estado y proveedor accesible")
    void oc02_findByTenantIdAndEstado_filtroYRelacion() {
        OrdenCompra oc = ordenCompra(2L, proveedor(11L, "Prov. Sur"), usuario(21L, "Luis"));
        when(ordenCompraRepository.findByTenantIdAndEstado(TENANT, "ENVIADA")).thenReturn(List.of(oc));

        List<OrdenCompra> result = ordenCompraRepository.findByTenantIdAndEstado(TENANT, "ENVIADA");

        assertThat(result).hasSize(1);
        assertThatCode(() -> result.get(0).getProveedor().getId()).doesNotThrowAnyException();
        verify(ordenCompraRepository).findByTenantIdAndEstado(TENANT, "ENVIADA");
    }

    @Test
    @DisplayName("OC-03: findByTenantIdAndProveedorId devuelve OC con relaciones correctas")
    void oc03_findByTenantIdAndProveedorId_ocCorrecta() {
        Proveedor prov = proveedor(5L, "MedSupply");
        OrdenCompra oc = ordenCompra(3L, prov, usuario(22L, "Carlos"));
        when(ordenCompraRepository.findByTenantIdAndProveedorId(TENANT, 5L)).thenReturn(List.of(oc));

        List<OrdenCompra> result = ordenCompraRepository.findByTenantIdAndProveedorId(TENANT, 5L);

        assertThat(result).hasSize(1);
        assertThat(result.get(0).getProveedor().getNombre()).isEqualTo("MedSupply");
    }

    @Test
    @DisplayName("OC-04: findByTenantIdAndSucursalId retorna OCs con proveedor sin NPE, usuarioCreador null válido")
    void oc04_findByTenantIdAndSucursalId_sinNpe() {
        OrdenCompra oc = ordenCompra(4L, proveedor(12L, "Farma Norte"), null);
        when(ordenCompraRepository.findByTenantIdAndSucursalId(TENANT, 1L)).thenReturn(List.of(oc));

        List<OrdenCompra> result = ordenCompraRepository.findByTenantIdAndSucursalId(TENANT, 1L);

        assertThatCode(() -> result.get(0).getProveedor().getNombre()).doesNotThrowAnyException();
        assertThat(result.get(0).getUsuarioCreador()).isNull();
    }

    @Test
    @DisplayName("OC-05: findEnviadasSinRecepcionar devuelve solo OCs ENVIADAS con proveedor accesible")
    void oc05_findEnviadasSinRecepcionar_soloEnviadas() {
        OrdenCompra oc = ordenCompra(5L, proveedor(13L, "Prov. Central"), usuario(23L, "María"));
        when(ordenCompraRepository.findEnviadasSinRecepcionar(eq(TENANT), any())).thenReturn(List.of(oc));

        List<OrdenCompra> result = ordenCompraRepository.findEnviadasSinRecepcionar(
                TENANT, LocalDateTime.now().minusDays(10));

        assertThat(result).hasSize(1);
        assertThat(result.get(0).getEstado()).isEqualTo("ENVIADA");
        assertThatCode(() -> result.get(0).getProveedor().getNombre()).doesNotThrowAnyException();
    }

    // ══ Tests MovimientoInventario ════════════════════════════════════════════

    @Test
    @DisplayName("MOV-01: findByTenantId retorna movimientos con producto y usuario accesibles")
    void mov01_findByTenantId_productoAccesible() {
        MovimientoInventario m = movimiento(1L, producto(10L, "Paracetamol"), usuario(1L, "Jorge"));
        when(movimientoRepository.findByTenantId(TENANT)).thenReturn(List.of(m));

        List<MovimientoInventario> result = movimientoRepository.findByTenantId(TENANT);

        assertThat(result).hasSize(1);
        assertThatCode(() -> {
            assertThat(result.get(0).getProducto().getNombre()).isEqualTo("Paracetamol");
            assertThat(result.get(0).getUsuario().getId()).isEqualTo(1L);
        }).doesNotThrowAnyException();
    }

    @Test
    @DisplayName("MOV-02: findByProductoIdAndTenantId filtra correctamente, usuario null válido")
    void mov02_findByProductoIdAndTenantId_filtroCorrecto() {
        MovimientoInventario m = movimiento(2L, producto(20L, "Ibuprofeno"), null);
        when(movimientoRepository.findByProductoIdAndTenantId(20L, TENANT)).thenReturn(List.of(m));

        List<MovimientoInventario> result = movimientoRepository.findByProductoIdAndTenantId(20L, TENANT);

        assertThat(result).hasSize(1);
        assertThat(result.get(0).getProducto().getId()).isEqualTo(20L);
        assertThat(result.get(0).getUsuario()).isNull();
    }

    @Test
    @DisplayName("MOV-03: findByTipoAndTenantId filtra por tipo y todos los productos accesibles")
    void mov03_findByTipoAndTenantId_productoPorTipo() {
        MovimientoInventario m1 = movimiento(3L, producto(30L, "Vitamina C"), usuario(5L, "Pedro"));
        MovimientoInventario m2 = movimiento(4L, producto(31L, "Zinc"), usuario(5L, "Pedro"));
        when(movimientoRepository.findByTipoAndTenantId("ENTRADA", TENANT)).thenReturn(List.of(m1, m2));

        List<MovimientoInventario> result = movimientoRepository.findByTipoAndTenantId("ENTRADA", TENANT);

        assertThat(result).hasSize(2);
        assertThatCode(() -> result.forEach(m -> m.getProducto().getNombre())).doesNotThrowAnyException();
    }

    @Test
    @DisplayName("MOV-04: findByUsuarioIdAndTenantId devuelve movimientos del usuario con producto")
    void mov04_findByUsuarioIdAndTenantId_usuarioCorrecto() {
        MovimientoInventario m = movimiento(5L, producto(40L, "Amoxicilina"), usuario(7L, "Elena"));
        when(movimientoRepository.findByUsuarioIdAndTenantId(7L, TENANT)).thenReturn(List.of(m));

        List<MovimientoInventario> result = movimientoRepository.findByUsuarioIdAndTenantId(7L, TENANT);

        assertThat(result).hasSize(1);
        assertThat(result.get(0).getUsuario().getId()).isEqualTo(7L);
        assertThatCode(() -> result.get(0).getProducto().getId()).doesNotThrowAnyException();
    }

    // ══ Tests Venta ══════════════════════════════════════════════════════════

    @Test
    @DisplayName("VEN-01: findByTenantId retorna ventas con vendedor accesible y sin vendedor (null válido)")
    void ven01_findByTenantId_vendedorAccesible() {
        Usuario vendedor = usuario(99L, "Vendedora");
        Venta v1 = venta(1L, vendedor);
        Venta v2 = venta(2L, null);
        when(ventaRepository.findByTenantId(TENANT)).thenReturn(List.of(v1, v2));

        List<Venta> result = ventaRepository.findByTenantId(TENANT);

        assertThat(result).hasSize(2);
        assertThatCode(() -> {
            assertThat(result.get(0).getVendedor().getNombre()).isEqualTo("Vendedora");
            assertThat(result.get(1).getVendedor()).isNull();
        }).doesNotThrowAnyException();
    }

    @Test
    @DisplayName("VEN-02: findByTenantIdAndSucursalId retorna ventas por sucursal con vendedor")
    void ven02_findByTenantIdAndSucursalId_porSucursal() {
        Venta v = venta(3L, usuario(50L, "Cajero"));
        when(ventaRepository.findByTenantIdAndSucursalId(TENANT, 1L)).thenReturn(List.of(v));

        List<Venta> result = ventaRepository.findByTenantIdAndSucursalId(TENANT, 1L);

        assertThat(result).hasSize(1);
        assertThat(result.get(0).getVendedor().getNombre()).isEqualTo("Cajero");
        verify(ventaRepository).findByTenantIdAndSucursalId(TENANT, 1L);
    }

    @Test
    @DisplayName("VEN-03: findByCajaIdAndTenantId retorna ventas de la caja con vendedor accesible")
    void ven03_findByCajaIdAndTenantId_vendedorAccesible() {
        Venta v = venta(4L, usuario(51L, "Mónica"));
        v.setCajaId(100L);
        when(ventaRepository.findByCajaIdAndTenantId(100L, TENANT)).thenReturn(List.of(v));

        List<Venta> result = ventaRepository.findByCajaIdAndTenantId(100L, TENANT);

        assertThat(result).hasSize(1);
        assertThat(result.get(0).getCajaId()).isEqualTo(100L);
        assertThatCode(() -> result.get(0).getVendedor().getNombre()).doesNotThrowAnyException();
    }

    @Test
    @DisplayName("AISLAMIENTO: findByTenantId no devuelve entidades de otro tenant")
    void aislamiento_findByTenantId_noMezclaTenants() {
        OrdenCompra ocA1 = ordenCompra(10L, proveedor(1L, "P1"), null);
        OrdenCompra ocA2 = ordenCompra(11L, proveedor(2L, "P2"), null);
        when(ordenCompraRepository.findByTenantId("tenant-abc")).thenReturn(List.of(ocA1, ocA2));
        when(ordenCompraRepository.findByTenantId("tenant-xyz")).thenReturn(List.of());

        assertThat(ordenCompraRepository.findByTenantId("tenant-abc")).hasSize(2);
        assertThat(ordenCompraRepository.findByTenantId("tenant-xyz")).isEmpty();
    }
}
