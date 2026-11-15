package com.stockflow.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class ActualizarOtRequestDTO {

    private String prioridad;
    private String descripcionProblema;
    private String diagnostico;
    private String trabajoRealizado;
    private String observaciones;
}
