package com.stockflow.service;

import com.stockflow.dto.ComprobanteProveedorDTO;
import com.stockflow.dto.RecepcionDetalleRequestDTO;
import com.stockflow.dto.RecepcionDetalleResponseDTO;
import com.stockflow.dto.RecepcionRequestDTO;
import com.stockflow.dto.RecepcionResponseDTO;

import java.util.List;
import java.util.Optional;

public interface RecepcionService {

    RecepcionResponseDTO crearRecepcion(RecepcionRequestDTO request, Long usuarioReceptorId, String tenantId);

    Optional<RecepcionResponseDTO> obtenerPorId(Long id);

    List<RecepcionResponseDTO> listar(String tenantId);

    /** Upsert: creates or updates the detail row for the given producto in the recepcion. */
    RecepcionDetalleResponseDTO upsertItem(Long recepcionId, RecepcionDetalleRequestDTO request);

    /** Saves supplier voucher data (tipo, serie, numero, url_adjunto) on the recepcion. */
    RecepcionResponseDTO guardarComprobante(Long recepcionId, ComprobanteProveedorDTO dto);

    /**
     * Confirms the recepcion: validates, generates inventory movements (ENTRADA/COMPRA),
     * and marks the recepcion as CONFIRMADA.  Idempotent: throws if already confirmed.
     */
    RecepcionResponseDTO confirmar(Long recepcionId, Long usuarioId);
}
