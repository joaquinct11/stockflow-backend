package com.stockflow.dto;

import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;
import lombok.Data;

@Data
public class RegistrarDevolucionOtRequestDTO {

    @NotNull(message = "La cantidad a devolver es obligatoria")
    @Min(value = 1, message = "La cantidad a devolver debe ser al menos 1")
    private Integer cantidadBase;

    private String motivo;
}
