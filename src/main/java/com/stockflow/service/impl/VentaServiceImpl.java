package com.stockflow.service.impl;

import com.stockflow.entity.DetalleVenta;
import com.stockflow.entity.Venta;
import com.stockflow.repository.VentaRepository;
import com.stockflow.repository.DetalleVentaRepository;
import com.stockflow.repository.ProductoRepository;
import com.stockflow.service.VentaService;
import com.stockflow.service.MovimientoInventarioService;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

@Service
@RequiredArgsConstructor
public class VentaServiceImpl implements VentaService {

    private final VentaRepository ventaRepository;
    private final DetalleVentaRepository detalleVentaRepository;
    private final ProductoRepository productoRepository;
    private final MovimientoInventarioService movimientoInventarioService;

    @Override
    @Transactional
    public Venta crearVenta(Venta venta) {
        BigDecimal total = BigDecimal.ZERO;

        for (DetalleVenta detalle : venta.getDetalles()) {
            detalle.setVenta(venta);
            detalle.calcularSubtotal();
            total = total.add(detalle.getSubtotal());
        }

        venta.setTotal(total);
        return ventaRepository.save(venta);
    }

    @Override
    public Optional<Venta> obtenerVentaPorId(Long id) {
        return ventaRepository.findById(id);
    }

    @Override
    public List<Venta> obtenerVentasPorVendedor(Long vendedorId) {
        return ventaRepository.findByVendedorId(vendedorId);
    }

    @Override
    public List<Venta> obtenerVentasPorTenant(String tenantId) {
        return ventaRepository.findByTenantId(tenantId);
    }

    @Override
    public List<Venta> obtenerVentasPorPeriodo(String tenantId, LocalDateTime inicio, LocalDateTime fin) {
        return ventaRepository.findVentasPorPeriodo(tenantId, inicio, fin);
    }

    @Override
    public List<DetalleVenta> obtenerDetallesVenta(Long ventaId) {
        return detalleVentaRepository.findByVentaId(ventaId);
    }

    @Override
    public void eliminarVenta(Long id) {
        ventaRepository.deleteById(id);
    }
}