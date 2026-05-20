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
public class ValidarNotaCreditoResponseDTO {

    private String codigo;
    private BigDecimal montoTotal;
    private String estado;
    private LocalDateTime fechaVencimiento;
    private boolean valida;
    private String mensaje;
}
