package com.stockflow.service;

import com.stockflow.dto.CrearDevolucionDTO;
import com.stockflow.dto.DevolucionDTO;

import java.util.List;

public interface DevolucionService {

    DevolucionDTO crear(CrearDevolucionDTO dto, Long usuarioId, String tenantId);

    List<DevolucionDTO> getByVenta(Long ventaId, String tenantId);

    List<DevolucionDTO> getAll(String tenantId);
}
