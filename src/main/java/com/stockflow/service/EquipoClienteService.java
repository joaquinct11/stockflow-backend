package com.stockflow.service;

import com.stockflow.dto.ActualizarEquipoRequestDTO;
import com.stockflow.dto.CrearEquipoRequestDTO;
import com.stockflow.dto.EquipoClienteDTO;
import com.stockflow.dto.EquipoPageResponseDTO;
import com.stockflow.dto.EquipoStatsDTO;

public interface EquipoClienteService {

    EquipoStatsDTO getStats();

    EquipoPageResponseDTO listar(int page, int size, String estado, String search, Long clienteId);

    EquipoClienteDTO obtenerPorId(Long id);

    EquipoClienteDTO crear(CrearEquipoRequestDTO dto);

    EquipoClienteDTO actualizar(Long id, ActualizarEquipoRequestDTO dto);

    void activar(Long id);

    void desactivar(Long id);
}
