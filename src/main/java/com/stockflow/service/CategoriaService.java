package com.stockflow.service;

import com.stockflow.dto.CategoriaResponseDTO;

import java.util.List;

public interface CategoriaService {

    List<CategoriaResponseDTO> listar(String tenantId);

    CategoriaResponseDTO crear(String nombre, String tenantId);
}
