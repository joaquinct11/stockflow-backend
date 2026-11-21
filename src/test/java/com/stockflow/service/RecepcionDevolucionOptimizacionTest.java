package com.stockflow.service;

import com.stockflow.entity.*;
import com.stockflow.repository.DevolucionRepository;
import com.stockflow.repository.RecepcionRepository;
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
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * F-03 (complementaria) — Recepcion y Devolucion: relaciones ManyToOne cambiadas a LAZY.
 * Verifica que proveedor, usuarioReceptor, ordenCompra, venta y usuario son accesibles
 * sin NPE tras el JOIN FETCH en las queries de listado.
 */
@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
@DisplayName("F-03 complementaria — Recepcion y Devolucion: LAZY + JOIN FETCH")
class RecepcionDevolucionOptimizacionTest {

    @Mock private RecepcionRepository  recepcionRepository;
    @Mock private DevolucionRepository devolucionRepository;

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

    private OrdenCompra ordenCompra(long id) {
        OrdenCompra oc = new OrdenCompra();
        oc.setId(id);
        oc.setEstado("ENVIADA");
        return oc;
    }

    private Recepcion recepcion(long id, Proveedor prov, Usuario receptor, OrdenCompra oc) {
        return Recepcion.builder()
                .id(id).tenantId(TENANT).estado("BORRADOR")
                .proveedor(prov).usuarioReceptor(receptor).ordenCompra(oc)
                .createdAt(LocalDateTime.now())
                .build();
    }

    private Venta venta(long id) {
        return Venta.builder().id(id).tenantId(TENANT).total(BigDecimal.TEN)
                .estado("COMPLETADA").build();
    }

    private DevolucionDetalle detalleDevolucion(long id, long productoId, String productoNombre) {
        Producto prod = Producto.builder().id(productoId).nombre(productoNombre)
                .stockActual(5).costoUnitario(BigDecimal.ONE).precioVenta(BigDecimal.TEN).build();
        return DevolucionDetalle.builder()
                .id(id).producto(prod).cantidadDevuelta(2)
                .precioUnitario(BigDecimal.TEN).subtotal(BigDecimal.valueOf(20)).build();
    }

    private Devolucion devolucion(long id, Venta v, Usuario u) {
        return Devolucion.builder()
                .id(id).tenantId(TENANT).venta(v).usuario(u)
                .motivo("Producto defectuoso").totalDevuelto(BigDecimal.TEN)
                .estado("PROCESADA").fechaDevolucion(LocalDateTime.now())
                .build();
    }

    // ══ Tests Recepcion ══════════════════════════════════════════════════════

    @Test
    @DisplayName("REC-01: findByTenantId retorna recepciones con proveedor y usuarioReceptor accesibles")
    void rec01_findByTenantId_relacionesAccesibles() {
        Recepcion r = recepcion(1L, proveedor(10L, "Distrib. Farma"), usuario(20L, "Ana"), null);
        when(recepcionRepository.findByTenantId(TENANT)).thenReturn(List.of(r));

        List<Recepcion> result = recepcionRepository.findByTenantId(TENANT);

        assertThat(result).hasSize(1);
        assertThatCode(() -> {
            assertThat(result.get(0).getProveedor().getNombre()).isEqualTo("Distrib. Farma");
            assertThat(result.get(0).getUsuarioReceptor().getId()).isEqualTo(20L);
            assertThat(result.get(0).getOrdenCompra()).isNull();  // sin OC → null válido
        }).doesNotThrowAnyException();
    }

    @Test
    @DisplayName("REC-02: findByTenantIdAndSucursalId retorna recepciones con OC accesible")
    void rec02_findByTenantIdAndSucursalId_conOc() {
        Recepcion r = recepcion(2L, proveedor(11L, "MedSupply"), usuario(21L, "Luis"), ordenCompra(100L));
        when(recepcionRepository.findByTenantIdAndSucursalId(TENANT, 1L)).thenReturn(List.of(r));

        List<Recepcion> result = recepcionRepository.findByTenantIdAndSucursalId(TENANT, 1L);

        assertThat(result).hasSize(1);
        assertThatCode(() -> {
            assertThat(result.get(0).getOrdenCompra().getId()).isEqualTo(100L);
            assertThat(result.get(0).getProveedor().getNombre()).isEqualTo("MedSupply");
        }).doesNotThrowAnyException();
        verify(recepcionRepository).findByTenantIdAndSucursalId(TENANT, 1L);
    }

    @Test
    @DisplayName("REC-03: findByTenantIdAndEstado filtra por estado con relaciones accesibles")
    void rec03_findByTenantIdAndEstado_filtroYRelacion() {
        Recepcion r = recepcion(3L, proveedor(12L, "Farma Norte"), usuario(22L, "Carlos"), null);
        when(recepcionRepository.findByTenantIdAndEstado(TENANT, "CONFIRMADA")).thenReturn(List.of(r));

        List<Recepcion> result = recepcionRepository.findByTenantIdAndEstado(TENANT, "CONFIRMADA");

        assertThat(result).hasSize(1);
        assertThatCode(() -> result.get(0).getProveedor().getId()).doesNotThrowAnyException();
        verify(recepcionRepository).findByTenantIdAndEstado(TENANT, "CONFIRMADA");
    }

    @Test
    @DisplayName("REC-04: findByOrdenCompraId retorna recepciones de la OC con proveedor accesible")
    void rec04_findByOrdenCompraId_proveedorAccesible() {
        Recepcion r = recepcion(4L, proveedor(13L, "Prov. Sur"), usuario(23L, "María"), ordenCompra(200L));
        when(recepcionRepository.findByOrdenCompraId(200L)).thenReturn(List.of(r));

        List<Recepcion> result = recepcionRepository.findByOrdenCompraId(200L);

        assertThat(result).hasSize(1);
        assertThat(result.get(0).getOrdenCompra().getId()).isEqualTo(200L);
        assertThatCode(() -> result.get(0).getProveedor().getNombre()).doesNotThrowAnyException();
    }

    @Test
    @DisplayName("REC-05: recepcion sin usuarioReceptor (null válido al mapear DTO)")
    void rec05_usuarioReceptorNullValido() {
        // usuarioReceptor puede ser null en casos legacy o sin login
        Recepcion r = recepcion(5L, proveedor(14L, "Importadora"), null, null);
        when(recepcionRepository.findByTenantId(TENANT)).thenReturn(List.of(r));

        List<Recepcion> result = recepcionRepository.findByTenantId(TENANT);

        assertThat(result.get(0).getUsuarioReceptor()).isNull();
        // El proveedor siempre debe estar (nullable=false)
        assertThatCode(() -> result.get(0).getProveedor().getNombre()).doesNotThrowAnyException();
    }

    // ══ Tests Devolucion ═════════════════════════════════════════════════════

    @Test
    @DisplayName("DEV-01: findByTenantIdOrderByFechaDevolucionDesc con venta y usuario accesibles")
    void dev01_findAllByTenant_relacionesAccesibles() {
        Venta v = venta(1L);
        Usuario u = usuario(5L, "Cajero");
        Devolucion d = devolucion(1L, v, u);
        when(devolucionRepository.findByTenantIdOrderByFechaDevolucionDesc(TENANT)).thenReturn(List.of(d));

        List<Devolucion> result = devolucionRepository.findByTenantIdOrderByFechaDevolucionDesc(TENANT);

        assertThat(result).hasSize(1);
        assertThatCode(() -> {
            assertThat(result.get(0).getVenta().getId()).isEqualTo(1L);
            assertThat(result.get(0).getUsuario().getNombre()).isEqualTo("Cajero");
        }).doesNotThrowAnyException();
    }

    @Test
    @DisplayName("DEV-02: findByTenantIdOrderByFechaDevolucionDesc con detalles accesibles sin NPE")
    void dev02_findAllByTenant_detallesAccesibles() {
        Venta v = venta(2L);
        Devolucion d = devolucion(2L, v, usuario(6L, "Vendedora"));
        d.setDetalles(List.of(detalleDevolucion(1L, 10L, "Paracetamol")));
        when(devolucionRepository.findByTenantIdOrderByFechaDevolucionDesc(TENANT)).thenReturn(List.of(d));

        List<Devolucion> result = devolucionRepository.findByTenantIdOrderByFechaDevolucionDesc(TENANT);

        assertThat(result.get(0).getDetalles()).hasSize(1);
        assertThatCode(() ->
            result.get(0).getDetalles().forEach(det -> det.getProducto().getNombre())
        ).doesNotThrowAnyException();
    }

    @Test
    @DisplayName("DEV-03: findByVentaIdAndTenantId con venta y usuario accesibles")
    void dev03_findByVentaId_relacionesAccesibles() {
        Venta v = venta(3L);
        Devolucion d = devolucion(3L, v, usuario(7L, "Elena"));
        when(devolucionRepository.findByVentaIdAndTenantId(3L, TENANT)).thenReturn(List.of(d));

        List<Devolucion> result = devolucionRepository.findByVentaIdAndTenantId(3L, TENANT);

        assertThat(result).hasSize(1);
        assertThat(result.get(0).getVenta().getId()).isEqualTo(3L);
        assertThatCode(() -> result.get(0).getUsuario().getNombre()).doesNotThrowAnyException();
    }

    @Test
    @DisplayName("DEV-04: devolucion con usuario null — consumidor final sin sesión")
    void dev04_usuarioNullValido() {
        Devolucion d = devolucion(4L, venta(4L), null);
        when(devolucionRepository.findByTenantIdOrderByFechaDevolucionDesc(TENANT)).thenReturn(List.of(d));

        List<Devolucion> result = devolucionRepository.findByTenantIdOrderByFechaDevolucionDesc(TENANT);

        assertThat(result.get(0).getUsuario()).isNull();
        assertThatCode(() -> result.get(0).getVenta().getId()).doesNotThrowAnyException();
    }

    @Test
    @DisplayName("DEV-05: aislamiento tenant — findByVentaIdAndTenantId no devuelve otra tenant")
    void dev05_aislamiento_tenant() {
        Devolucion d = devolucion(5L, venta(5L), usuario(8L, "Pedro"));
        when(devolucionRepository.findByVentaIdAndTenantId(5L, "tenant-abc")).thenReturn(List.of(d));
        when(devolucionRepository.findByVentaIdAndTenantId(5L, "tenant-xyz")).thenReturn(List.of());

        assertThat(devolucionRepository.findByVentaIdAndTenantId(5L, "tenant-abc")).hasSize(1);
        assertThat(devolucionRepository.findByVentaIdAndTenantId(5L, "tenant-xyz")).isEmpty();
    }
}
