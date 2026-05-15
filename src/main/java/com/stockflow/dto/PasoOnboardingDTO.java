package com.stockflow.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class PasoOnboardingDTO {

    private String id;
    private String titulo;
    private String descripcion;
    private boolean completado;
    private String url;
}
