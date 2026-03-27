package com.stockflow.service;

import com.stockflow.dto.ComprobanteDTO;
import com.stockflow.dto.EmitirComprobanteRequest;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

public interface ComprobanteService {

    ComprobanteDTO emitir(EmitirComprobanteRequest request, String tenantId);

    Optional<ComprobanteDTO> obtenerPorId(Long id, String tenantId);

    Optional<ComprobanteDTO> obtenerPorVenta(Long ventaId, String tenantId);

    List<ComprobanteDTO> listar(
            String tenantId,
            String tipo,
            String estado,
            LocalDateTime from,
            LocalDateTime to,
            Long ventaId,
            String search
    );

    ComprobanteDTO anular(Long id, String tenantId);
}
