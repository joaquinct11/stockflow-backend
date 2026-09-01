package com.stockflow.service.impl;

import com.stockflow.entity.DetalleVenta;
import com.stockflow.entity.MovimientoInventario;
import com.stockflow.entity.Producto;
import com.stockflow.entity.ProductoStockSucursal;
import com.stockflow.entity.Usuario;
import com.stockflow.entity.Venta;
import com.stockflow.exception.BadRequestException;
import com.stockflow.exception.ResourceNotFoundException;
import com.stockflow.repository.DetalleVentaRepository;
import com.stockflow.repository.ProductoRepository;
import com.stockflow.repository.ProductoStockSucursalRepository;
import com.stockflow.repository.SucursalRepository;
import com.stockflow.repository.UsuarioRepository;
import com.stockflow.repository.VentaRepository;
import com.stockflow.service.MovimientoInventarioService;
import com.stockflow.service.NotaCreditoService;
import com.stockflow.service.StockLoteService;
import com.stockflow.service.VentaService;
import com.stockflow.util.TenantContext;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

@Slf4j
@Service
@RequiredArgsConstructor
public class VentaServiceImpl implements VentaService {

    private final VentaRepository                   ventaRepository;
    private final DetalleVentaRepository            detalleVentaRepository;
    private final ProductoRepository                productoRepository;
    private final UsuarioRepository                 usuarioRepository;
    private final MovimientoInventarioService       movimientoInventarioService;
    private final NotaCreditoService                notaCreditoService;
    private final ProductoStockSucursalRepository   stockSucursalRepository;
    private final SucursalRepository                sucursalRepository;
    private final StockLoteService                  stockLoteService;

    @Override
    @Transactional
    public Venta crearVenta(Venta venta) {
        for (DetalleVenta detalle : venta.getDetalles()) {
            detalle.setVenta(venta);
            detalle.calcularSubtotal();
        }
        return ventaRepository.save(venta);
    }

    @Override
    public Optional<Venta> obtenerVentaPorId(Long id) {
        return ventaRepository.findById(id);
    }

    @Override
    public List<Venta> obtenerVentasPorVendedorYTenant(Long vendedorId, String tenantId) {
        return ventaRepository.findByTenantIdAndVendedorId(tenantId, vendedorId);
    }

    @Override
    public List<Venta> obtenerVentasPorTenant(String tenantId) {
        return ventaRepository.findByTenantId(tenantId);
    }

    @Override
    public List<Venta> obtenerVentasPorTenantYSucursal(String tenantId, Long sucursalId) {
        return ventaRepository.findByTenantIdAndSucursalId(tenantId, sucursalId);
    }

    @Override
    public List<Venta> obtenerVentasPorPeriodo(String tenantId, LocalDateTime inicio, LocalDateTime fin) {
        return ventaRepository.findVentasPorPeriodo(tenantId, inicio, fin);
    }

    @Override
    public List<Venta> obtenerVentasPorVendedorYTenantYPeriodo(Long vendedorId, String tenantId, LocalDateTime inicio, LocalDateTime fin) {
        return ventaRepository.findByTenantIdAndVendedorIdAndPeriodo(tenantId, vendedorId, inicio, fin);
    }

    @Override
    public List<DetalleVenta> obtenerDetallesVenta(Long ventaId) {
        return detalleVentaRepository.findByVentaId(ventaId);
    }

    @Override
    @Transactional
    public void actualizarNotaCredito(Long ventaId, Long notaCreditoId) {
        ventaRepository.findById(ventaId).ifPresent(v -> {
            v.setNotaCreditoId(notaCreditoId);
            ventaRepository.save(v);
        });
    }

    @Override
    @Transactional
    public Venta anularVenta(Long id, String tenantId) {
        Venta venta = ventaRepository.findById(id)
                .filter(v -> tenantId.equals(v.getTenantId()))
                .orElseThrow(() -> new ResourceNotFoundException("Venta no encontrada: " + id));

        if ("ANULADA".equals(venta.getEstado())) {
            throw new BadRequestException("La venta ya está anulada");
        }
        if ("DEVUELTA".equals(venta.getEstado()) || "DEVUELTA_PARCIAL".equals(venta.getEstado())) {
            throw new BadRequestException("No se puede anular una venta que ya tiene devoluciones registradas");
        }

        Long usuarioId = TenantContext.getCurrentUserId();
        Usuario usuario = usuarioId != null
                ? usuarioRepository.findById(usuarioId).orElse(null)
                : null;

        // Reponer stock y registrar movimiento por cada producto
        for (DetalleVenta detalle : venta.getDetalles()) {
            Producto producto = detalle.getProducto();
            producto.setStockActual(producto.getStockActual() + detalle.getCantidad());
            productoRepository.save(producto);

            // Restaurar lote específico si la venta lo usó
            if (detalle.getStockLoteId() != null) {
                stockLoteService.restaurarLoteEspecifico(detalle.getStockLoteId(), detalle.getCantidad());
            }

            // Reponer también en producto_stock_sucursal si la venta tiene sucursalId
            if (venta.getSucursalId() != null) {
                sucursalRepository.findById(venta.getSucursalId()).ifPresent(sucursal -> {
                    ProductoStockSucursal entry = stockSucursalRepository
                            .findByProductoIdAndSucursalId(producto.getId(), sucursal.getId())
                            .orElseGet(() -> ProductoStockSucursal.builder()
                                    .producto(producto)
                                    .sucursal(sucursal)
                                    .tenantId(tenantId)
                                    .stockActual(0)
                                    .build());
                    entry.setStockActual((entry.getStockActual() != null ? entry.getStockActual() : 0) + detalle.getCantidad());
                    stockSucursalRepository.save(entry);
                });
            }

            MovimientoInventario mov = MovimientoInventario.builder()
                    .producto(producto)
                    .usuario(usuario)
                    .tipo("AJUSTE")
                    .cantidad(detalle.getCantidad())
                    .descripcion("Anulación venta #" + venta.getId())
                    .referencia("ANUL-" + venta.getId())
                    .tenantId(tenantId)
                    .sucursalId(venta.getSucursalId())
                    .build();
            movimientoInventarioService.crearMovimiento(mov);

            log.info("📦 Stock repuesto: +{} de '{}' por anulación venta {}",
                    detalle.getCantidad(), producto.getNombre(), venta.getId());
        }

        // Si la venta usó una nota de crédito, restaurarla a PENDIENTE
        if (venta.getNotaCreditoId() != null) {
            try {
                notaCreditoService.restaurarPorVentaAnulada(venta.getNotaCreditoId(), tenantId);
            } catch (Exception e) {
                log.warn("No se pudo restaurar nota de crédito {} al anular venta {}: {}",
                        venta.getNotaCreditoId(), venta.getId(), e.getMessage());
            }
            venta.setNotaCreditoId(null);
            venta.setDescuentoNotaCredito(null);
        }

        venta.setEstado("ANULADA");
        return ventaRepository.save(venta);
    }
}
