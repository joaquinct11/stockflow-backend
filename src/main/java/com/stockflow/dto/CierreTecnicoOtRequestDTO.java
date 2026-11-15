package com.stockflow.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class CierreTecnicoOtRequestDTO {

    @NotBlank(message = "El diagnóstico final es obligatorio")
    @Size(max = 5000)
    private String diagnosticoFinal;

    @NotBlank(message = "La solución aplicada es obligatoria")
    @Size(max = 5000)
    private String solucionAplicada;

    @Size(max = 2000)
    private String observaciones;
}
