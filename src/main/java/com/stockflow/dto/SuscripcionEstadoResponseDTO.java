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
public class SuscripcionEstadoResponseDTO {
    private String estado;
    private String planId;
    private String preapprovalId;
    private LocalDateTime fechaProximoCobro;
    /** Precio mensual real del plan (viene de BD, no hardcodeado en frontend). */
    private BigDecimal precioMensual;
    /** Fin del período pagado actual. Usado para mostrar fecha de corte en CANCELACION_PENDIENTE. */
    private LocalDateTime currentPeriodEnd;
}
