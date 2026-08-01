package com.stockflow.dto.superadmin;

import lombok.Builder;
import lombok.Data;

import java.math.BigDecimal;
import java.time.LocalDateTime;

@Data
@Builder
public class SuscripcionHistorialDTO {
    private Long          id;
    private String        planId;
    private String        estado;
    private BigDecimal    precioMensual;
    private String        metodoPago;
    private String        ultimos4Digitos;
    private String        preapprovalId;
    private LocalDateTime fechaInicio;
    private LocalDateTime fechaProximoCobro;
    private LocalDateTime trialEndDate;
    private LocalDateTime currentPeriodEnd;
    private LocalDateTime createdAt;
}
