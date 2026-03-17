package com.stockflow.service;

import com.stockflow.dto.UnidadMedidaRequestDTO;
import com.stockflow.dto.UnidadMedidaResponseDTO;

import java.util.List;

public interface UnidadMedidaService {

    List<UnidadMedidaResponseDTO> listar();

    UnidadMedidaResponseDTO obtenerPorId(Long id);

    UnidadMedidaResponseDTO crear(UnidadMedidaRequestDTO request);

    UnidadMedidaResponseDTO actualizar(Long id, UnidadMedidaRequestDTO request);

    void eliminar(Long id);
}
