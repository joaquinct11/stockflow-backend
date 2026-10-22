package com.stockflow.service;

import com.stockflow.dto.ActualizarMascotaRequestDTO;
import com.stockflow.dto.CrearMascotaRequestDTO;
import com.stockflow.dto.MascotaDTO;
import com.stockflow.dto.MascotaPageResponseDTO;
import com.stockflow.dto.MascotaResumenDTO;
import com.stockflow.dto.MascotaStatsDTO;

import java.util.List;

public interface MascotaService {

    MascotaDTO crearMascota(CrearMascotaRequestDTO dto);

    MascotaDTO obtenerPorId(Long id);

    /**
     * Lista mascotas del tenant activo con paginación y búsqueda.
     *
     * @param page   número de página (0-based)
     * @param size   tamaño de página
     * @param estado "ACTIVAS" | "INACTIVAS" | "TODOS" (insensible a mayúsculas)
     * @param search texto de búsqueda en nombre, especie, raza o propietario
     */
    MascotaPageResponseDTO listar(int page, int size, String estado, String search);

    List<MascotaResumenDTO> listarPorCliente(Long clienteId);

    List<MascotaResumenDTO> buscarPorNombre(String nombre);

    MascotaDTO actualizar(Long id, ActualizarMascotaRequestDTO dto);

    MascotaDTO activar(Long id);

    MascotaDTO desactivar(Long id);

    MascotaStatsDTO getStats();
}
