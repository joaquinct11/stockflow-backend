package com.stockflow.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;
import java.time.LocalDateTime;

@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class CulqiSuscribirResponseDTO {

    /** ID interno de la suscripción local (tabla suscripciones) */
    private Long suscripcionId;

    /** Estado resultante: "ACTIVA" */
    private String estado;

    /** ID del plan: "BASICO" */
    private String planId;

    /** Precio mensual en soles */
    private BigDecimal precioMensual;

    /** Fecha en que se activa */
    private LocalDateTime fechaInicio;

    /** Próxima fecha de cobro (1 mes después) */
    private LocalDateTime fechaProximoCobro;

    /** ID de suscripción en Culqi (sub_live_xxx) — para referencia */
    private String culqiSubscriptionId;

    /** Mensaje amigable para mostrar al usuario */
    private String mensaje;
}
