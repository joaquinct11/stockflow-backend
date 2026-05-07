package com.stockflow.service;

import com.stockflow.dto.ProductoImportResultDTO;
import com.stockflow.dto.ProductoImportRowDTO;
import com.stockflow.entity.Producto;
import java.util.List;
import java.util.Optional;

public interface ProductoService {

    Producto crearProducto(Producto producto);

    Optional<Producto> obtenerProductoPorId(Long id);

    Optional<Producto> obtenerProductoPorCodigoBarras(String codigoBarras);

    List<Producto> buscarProductosPorNombre(String nombre);

    List<Producto> obtenerProductosPorTenant(String tenantId);

    List<Producto> obtenerProductosActivos();

    List<Producto> obtenerProductosBajoStock(String tenantId);

    Producto actualizarProducto(Long id, Producto productoActualizado);

    void eliminarProducto(Long id);

    /** Importación masiva desde Excel/CSV. Crea o actualiza según codigoBarras. */
    ProductoImportResultDTO importar(List<ProductoImportRowDTO> filas, String tenantId);
}