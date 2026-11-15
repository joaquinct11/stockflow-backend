package com.stockflow.service;

import com.stockflow.dto.ActualizarEstadoOtRequestDTO;
import com.stockflow.dto.ActualizarOtRequestDTO;
import com.stockflow.dto.AsignarTecnicoOtRequestDTO;
import com.stockflow.dto.CierreTecnicoOtRequestDTO;
import com.stockflow.dto.CrearOtRequestDTO;
import com.stockflow.dto.EntregarOtRequestDTO;
import com.stockflow.dto.FacturarOtRequestDTO;
import com.stockflow.dto.OrdenTrabajoDTO;
import com.stockflow.dto.OtPageResponseDTO;

public interface OrdenTrabajoService {

    OtPageResponseDTO listar(int page, int size, String estado, Long equipoClienteId, Long clienteId, String search);

    OrdenTrabajoDTO obtenerPorId(Long id);

    OrdenTrabajoDTO crear(CrearOtRequestDTO dto);

    OrdenTrabajoDTO actualizar(Long id, ActualizarOtRequestDTO dto);

    OrdenTrabajoDTO actualizarEstado(Long id, ActualizarEstadoOtRequestDTO dto);

    OrdenTrabajoDTO asignarTecnico(Long id, AsignarTecnicoOtRequestDTO dto);

    OrdenTrabajoDTO cierreTecnico(Long id, CierreTecnicoOtRequestDTO req);

    OrdenTrabajoDTO facturarOt(Long id, FacturarOtRequestDTO req);

    OrdenTrabajoDTO entregarOt(Long id, EntregarOtRequestDTO req);

    /**
     * Anula la facturación de una OT en estado FACTURADA.
     * Pone la venta en ANULADA (sin reponer stock) y revierte la OT a CERRADA_TECN.
     */
    OrdenTrabajoDTO anularFacturacionOt(Long id);
}
