package com.stockflow.dto.superadmin;

import lombok.Builder;
import lombok.Data;
import java.math.BigDecimal;
import java.time.LocalDateTime;

@Data
@Builder
public class SuscripcionHistorialDTO {
    private Long id;
    private String planId;
    private String planCodigo;
    private BigDecimal precioMensual;
    private String estado;
    private LocalDateTime fechaInicio;
    private LocalDateTime fechaProximoCobro;
    private LocalDateTime fechaCancelacion;
    private String metodoPago;
    private String ultimos4Digitos;
}
