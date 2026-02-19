package com.stockflow.service.impl;

import com.stockflow.entity.Producto;
import com.stockflow.repository.ProductoRepository;
import com.stockflow.service.ProductoService;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import java.util.List;
import java.util.Optional;

@Service
@RequiredArgsConstructor
public class ProductoServiceImpl implements ProductoService {

    private final ProductoRepository productoRepository;

    @Override
    public Producto crearProducto(Producto producto) {
        return productoRepository.save(producto);
    }

    @Override
    public Optional<Producto> obtenerProductoPorId(Long id) {
        return productoRepository.findById(id);
    }

    @Override
    public Optional<Producto> obtenerProductoPorCodigoBarras(String codigoBarras) {
        return productoRepository.findByCodigoBarras(codigoBarras);
    }

    @Override
    public List<Producto> buscarProductosPorNombre(String nombre) {
        return productoRepository.findByNombreContainingIgnoreCase(nombre);
    }

    @Override
    public List<Producto> obtenerProductosPorTenant(String tenantId) {
        return productoRepository.findByTenantId(tenantId);
    }

    @Override
    public List<Producto> obtenerProductosActivos() {
        return productoRepository.findByActivoTrue();
    }

    @Override
    public List<Producto> obtenerProductosBajoStock(String tenantId) {
        return productoRepository.findProductosBajoStock(tenantId);
    }

    @Override
    public Producto actualizarProducto(Long id, Producto productoActualizado) {
        return productoRepository.findById(id)
                .map(producto -> {
                    producto.setNombre(productoActualizado.getNombre());
                    producto.setCodigoBarras(productoActualizado.getCodigoBarras());
                    producto.setCategoria(productoActualizado.getCategoria());
                    producto.setCostoUnitario(productoActualizado.getCostoUnitario());
                    producto.setPrecioVenta(productoActualizado.getPrecioVenta());
                    producto.setFechaVencimiento(productoActualizado.getFechaVencimiento());
                    producto.setLote(productoActualizado.getLote());
                    producto.setProveedorId(productoActualizado.getProveedorId());
                    producto.setActivo(productoActualizado.getActivo());
                    return productoRepository.save(producto);
                })
                .orElseThrow(() -> new RuntimeException("Producto no encontrado"));
    }

    @Override
    public void eliminarProducto(Long id) {
        productoRepository.deleteById(id);
    }
}