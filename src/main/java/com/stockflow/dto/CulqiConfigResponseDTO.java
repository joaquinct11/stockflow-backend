package com.stockflow.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;

@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class CulqiConfigResponseDTO {

    /** Public key de Culqi para inicializar Culqi.js en el frontend */
    private String publicKey;

    /** ID del plan Culqi (pln_live_xxx) configurado en el servidor */
    private String planId;

    /** Precio mensual en soles (ej: 150.00) para mostrarlo en UI */
    private BigDecimal precioMensual;

    /** Nombre descriptivo del plan (ej: "Plan Básico") */
    private String nombrePlan;
}
