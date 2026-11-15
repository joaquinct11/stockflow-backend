package com.stockflow.dto;

import com.stockflow.entity.EstadoCita;
import jakarta.validation.constraints.NotNull;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class CambiarEstadoCitaRequestDTO {

    @NotNull(message = "El nuevo estado es obligatorio")
    private EstadoCita nuevoEstado;

    /** Observaciones opcionales al cambiar el estado. */
    private String observaciones;
}
