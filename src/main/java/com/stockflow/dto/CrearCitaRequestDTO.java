package com.stockflow.dto;

import com.stockflow.entity.TipoCita;
import jakarta.validation.constraints.NotNull;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class CrearCitaRequestDTO {

    @NotNull(message = "El clienteId es obligatorio")
    private Long clienteId;

    @NotNull(message = "El mascotaId es obligatorio")
    private Long mascotaId;

    /** Profesional responsable (opcional). */
    private Long usuarioTenantId;

    private Long sucursalId;

    @NotNull(message = "La fechaInicio es obligatoria")
    private LocalDateTime fechaInicio;

    @NotNull(message = "La fechaFin es obligatoria")
    private LocalDateTime fechaFin;

    @NotNull(message = "El tipo es obligatorio")
    private TipoCita tipo;

    private String motivo;
    private String observaciones;
}
