package com.stockflow.service;

import com.stockflow.dto.ProductoVarianteDTO;
import java.util.List;

public interface ProductoVarianteService {
    List<ProductoVarianteDTO> getByProducto(Long productoId, String tenantId);
    ProductoVarianteDTO create(ProductoVarianteDTO dto, String tenantId);
    ProductoVarianteDTO update(Long id, ProductoVarianteDTO dto, String tenantId);
    void delete(Long id, String tenantId);
}
