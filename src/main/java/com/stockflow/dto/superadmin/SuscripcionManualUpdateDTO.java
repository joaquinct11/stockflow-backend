package com.stockflow.dto.superadmin;

import lombok.Data;

import java.math.BigDecimal;
import java.time.LocalDateTime;

@Data
public class SuscripcionManualUpdateDTO {
    private String        planId;
    private String        estado;
    private BigDecimal    precioMensual;
    private LocalDateTime fechaProximoCobro;
    private LocalDateTime trialEndDate;
    private LocalDateTime currentPeriodEnd;
}
