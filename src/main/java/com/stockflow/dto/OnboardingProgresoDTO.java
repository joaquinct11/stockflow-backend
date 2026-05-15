package com.stockflow.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class OnboardingProgresoDTO {

    private List<PasoOnboardingDTO> pasos;
    private int porcentaje;
    private boolean completado;
}
