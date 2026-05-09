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

    /** Edita cabecera (observaciones) de una OC en BORRADOR. */
    OrdenCompraResponseDTO editarCabecera(Long id, String observaciones, String tenantId);

    /** Agrega o actualiza un ítem en una OC en BORRADOR (upsert por productoId). */
    OrdenCompraResponseDTO upsertItem(Long id, OrdenCompraItemDTO item, String tenantId);

    /** Elimina un ítem de una OC en BORRADOR. */
    void removeItem(Long id, Long itemId, String tenantId);

    OrdenCompraResponseDTO enviar(Long id, String tenantId);
    OrdenCompraResponseDTO cancelar(Long id, String tenantId);

    /** Genera y devuelve el PDF de la OC como bytes. */
    byte[] generarPdf(Long id, String tenantId);
}
