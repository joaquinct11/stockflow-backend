package com.stockflow.service;

import com.stockflow.dto.NotaCreditoDTO;
import com.stockflow.dto.ValidarNotaCreditoResponseDTO;
import com.stockflow.entity.Devolucion;
import com.stockflow.entity.NotaCredito;
import com.stockflow.entity.Tenant;

import java.util.List;

public interface NotaCreditoService {

    NotaCredito generarParaDevolucion(Devolucion devolucion, String tenantId);

    ValidarNotaCreditoResponseDTO validar(String codigo, String tenantId);

    NotaCredito aplicar(String codigo, Long ventaId, String tenantId);

    /** Restaura la NC usada en una venta anulada a estado PENDIENTE */
    void restaurarPorVentaAnulada(Long notaCreditoId, String tenantId);

    List<NotaCreditoDTO> getAll(String tenantId, Long sucursalId);

    byte[] generarPdf(Long id, String tenantId, Tenant tenant, String formato);
}
