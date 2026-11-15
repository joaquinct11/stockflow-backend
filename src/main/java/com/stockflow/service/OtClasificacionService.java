package com.stockflow.service;

import com.stockflow.dto.OtConsumoClasificacionDTO;
import com.stockflow.dto.RegistrarClasificacionOtRequestDTO;

import java.util.List;

public interface OtClasificacionService {

    OtConsumoClasificacionDTO registrarClasificacion(Long otId, Long consumoId, RegistrarClasificacionOtRequestDTO req);

    List<OtConsumoClasificacionDTO> getClasificaciones(Long otId, Long consumoId);
}
