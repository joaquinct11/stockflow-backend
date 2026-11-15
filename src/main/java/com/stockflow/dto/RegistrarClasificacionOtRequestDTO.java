package com.stockflow.dto;

import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import lombok.Data;

@Data
public class RegistrarClasificacionOtRequestDTO {

    @NotBlank(message = "La clasificación es obligatoria")
    @Pattern(
        regexp = "INSTALADO|RECUPERABLE|DAÑADO|PERDIDO",
        message = "La clasificación debe ser: INSTALADO, RECUPERABLE, DAÑADO o PERDIDO"
    )
    private String clasificacion;

    @NotNull(message = "La cantidad a clasificar es obligatoria")
    @Min(value = 1, message = "La cantidad a clasificar debe ser al menos 1")
    private Integer cantidad;

    private String notas;
}
