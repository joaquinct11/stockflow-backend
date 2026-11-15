package com.stockflow.service;

import com.stockflow.dto.OtConsumoDevolucionDTO;
import com.stockflow.dto.RegistrarDevolucionOtRequestDTO;

import java.util.List;

public interface OtDevolucionService {

    OtConsumoDevolucionDTO registrarDevolucion(Long otId, Long consumoId, RegistrarDevolucionOtRequestDTO req);

    List<OtConsumoDevolucionDTO> getDevoluciones(Long otId, Long consumoId);
}
