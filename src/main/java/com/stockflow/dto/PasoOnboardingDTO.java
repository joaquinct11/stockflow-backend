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
    /** Si es true, no cuenta para el porcentaje ni para marcar el onboarding como completado. */
    private boolean opcional;
}
