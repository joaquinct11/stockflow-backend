package com.stockflow.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;

@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class FacturarOtRequestDTO {

    @NotNull(message = "El vendedor es obligatorio")
    private Long vendedorId;

    @NotBlank(message = "El método de pago es obligatorio")
    private String metodoPago;

    private Long cajaId;

    /** Monto de mano de obra. null se trata como 0. No puede ser negativo. */
    private BigDecimal montoManoObra;
}
