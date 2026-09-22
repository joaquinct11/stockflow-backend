package com.stockflow.service;

import com.stockflow.dto.ProductoPresentacionDTO;

import java.util.List;

public interface ProductoPresentacionService {

    List<ProductoPresentacionDTO> listarPorProducto(Long productoId, String tenantId);

    ProductoPresentacionDTO crear(Long productoId, ProductoPresentacionDTO dto, String tenantId);

    ProductoPresentacionDTO actualizar(Long id, ProductoPresentacionDTO dto, String tenantId);

    void eliminar(Long id, String tenantId);
}
