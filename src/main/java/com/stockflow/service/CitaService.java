package com.stockflow.service;

import com.stockflow.dto.*;
import com.stockflow.entity.EstadoCita;
import com.stockflow.entity.TipoCita;

import java.time.LocalDateTime;
import java.util.List;

/** Contrato del servicio de Citas veterinarias. */
public interface CitaService {

    CitaDTO crear(CrearCitaRequestDTO dto);

    CitaDTO obtenerPorId(Long id);

    CitaPageResponseDTO listar(
            int page,
            int size,
            EstadoCita estado,
            LocalDateTime fechaDesde,
            LocalDateTime fechaHasta,
            Long clienteId,
            Long mascotaId,
            Long usuarioTenantId,
            Long sucursalId,
            String q
    );

    CitaDTO actualizar(Long id, ActualizarCitaRequestDTO dto);

    CitaDTO cambiarEstado(Long id, CambiarEstadoCitaRequestDTO dto);

    List<ProfesionalElegibleDTO> listarProfesionalesElegibles(Long sucursalId, TipoCita tipo);
}
