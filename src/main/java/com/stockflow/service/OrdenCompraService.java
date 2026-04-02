package com.stockflow.service;

import com.stockflow.dto.OrdenCompraItemDTO;
import com.stockflow.dto.OrdenCompraRequestDTO;
import com.stockflow.dto.OrdenCompraResponseDTO;

import java.util.List;
import java.util.Optional;

public interface OrdenCompraService {

    OrdenCompraResponseDTO crearOrdenCompra(OrdenCompraRequestDTO request, Long usuarioCreadorId, String tenantId);

    Optional<OrdenCompraResponseDTO> obtenerPorId(Long id);

    List<OrdenCompraResponseDTO> listar(String tenantId, String estado, Long proveedorId);

    /** Returns the pending items (with received/pending quantities) for a given OC. */
    List<OrdenCompraItemDTO> obtenerItemsConPendientes(Long ocId);
}
