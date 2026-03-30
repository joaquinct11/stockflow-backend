package com.stockflow.service.impl;

import com.stockflow.entity.MovimientoInventario;
import com.stockflow.entity.Producto;
import com.stockflow.entity.Usuario;
import com.stockflow.repository.MovimientoInventarioRepository;
import com.stockflow.repository.ProductoRepository;
import com.stockflow.repository.UsuarioRepository;
import com.stockflow.service.ProductoService;
import com.stockflow.util.TenantContext;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import java.util.List;
import java.util.Optional;

@Slf4j
@Service
@RequiredArgsConstructor
public class ProductoServiceImpl implements ProductoService {

    private final ProductoRepository productoRepository;
    private final MovimientoInventarioRepository movimientoInventarioRepository;
    private final UsuarioRepository usuarioRepository;

    @Override
    public Producto crearProducto(Producto producto) {
        Producto productoCreado = productoRepository.save(producto);

        Long userId = TenantContext.getCurrentUserId(); // viene del token
        Usuario usuario = usuarioRepository.findById(userId)
                .orElseThrow(() -> new RuntimeException("Usuario no encontrado: " + userId));

        MovimientoInventario mov = MovimientoInventario.builder()
                .producto(productoCreado)
                .usuario(usuario) // ✅ esto evita el NOT NULL
                .tipo("SALDO_INICIAL")
                .cantidad(0) // o productoCreado.getStockActual()
                .descripcion("Saldo inicial al crear producto")
                .referencia("CREACION_PRODUCTO")
                .tenantId(productoCreado.getTenantId())
                .costoUnitario(productoCreado.getCostoUnitario())
                .build();

        movimientoInventarioRepository.save(mov);

        return productoCreado;
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
//                    producto.setFechaVencimiento(productoActualizado.getFechaVencimiento());
//                    producto.setLote(productoActualizado.getLote());
                    producto.setUnidadMedida(productoActualizado.getUnidadMedida());
//                    producto.setProveedorId(productoActualizado.getProveedorId());
                    producto.setActivo(productoActualizado.getActivo());
                    producto.setStockActual(productoActualizado.getStockActual());
                    producto.setStockMinimo(productoActualizado.getStockMinimo());
                    producto.setStockMaximo(productoActualizado.getStockMaximo());
                    return productoRepository.save(producto);
                })
                .orElseThrow(() -> new RuntimeException("Producto no encontrado"));
    }

    @Override
    public void eliminarProducto(Long id) {
        productoRepository.deleteById(id);
    }
}