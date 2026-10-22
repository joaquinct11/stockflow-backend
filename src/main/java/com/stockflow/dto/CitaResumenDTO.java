package com.stockflow.dto;

import com.stockflow.entity.EstadoCita;
import com.stockflow.entity.TipoCita;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

/** Versión resumida de una cita para listados paginados. */
@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class CitaResumenDTO {
    private Long          id;
    private Long          sucursalId;
    private Long          clienteId;
    private String        clienteNombre;
    private Long          mascotaId;
    private String        mascotaNombre;
    private String        mascotaEspecie;
    private Long          usuarioTenantId;
    private String        profesionalNombre;
    private LocalDateTime fechaInicio;
    private LocalDateTime fechaFin;
    private TipoCita      tipo;
    private EstadoCita    estado;
    private String        motivo;
}
