package com.stockflow.dto;

import jakarta.validation.constraints.NotNull;
import lombok.Data;
import java.math.BigDecimal;

@Data
public class CorregirCierreRequestDTO {

    @NotNull(message = "El monto contado es requerido")
    private BigDecimal montoContado;

    private String observaciones;
}
