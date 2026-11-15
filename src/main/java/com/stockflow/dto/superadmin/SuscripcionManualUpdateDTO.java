package com.stockflow.dto.superadmin;

import lombok.Data;
import java.math.BigDecimal;
import java.time.LocalDateTime;

@Data
public class SuscripcionManualUpdateDTO {
    private String planId;
    private String planCodigo;
    private BigDecimal precioMensual;
    private String estado;
    private LocalDateTime fechaProximoCobro;
}
