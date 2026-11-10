package com.stockflow.dto;

import jakarta.validation.constraints.NotBlank;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class ActualizarEstadoOtRequestDTO {

    @NotBlank(message = "El nuevo estado es requerido")
    private String estado;

    private String observacion;
}
