package com.stockflow.dto;

import lombok.Data;
import java.math.BigDecimal;

@Data
public class AbrirCajaRequestDTO {
    private BigDecimal montoApertura = BigDecimal.ZERO;
    private String observaciones;
    private Long sucursalId;
}
