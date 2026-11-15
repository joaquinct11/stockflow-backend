package com.stockflow.service;

import com.stockflow.dto.OtConsumoMaterialDTO;
import com.stockflow.dto.RegistrarConsumoOtRequestDTO;

import java.util.List;

public interface OtConsumoService {

    OtConsumoMaterialDTO registrarConsumo(Long otId, RegistrarConsumoOtRequestDTO dto);

    List<OtConsumoMaterialDTO> getConsumos(Long otId);
}
