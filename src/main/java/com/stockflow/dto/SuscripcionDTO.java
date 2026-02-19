package com.stockflow.dto;

import jakarta.validation.constraints.DecimalMin;
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
public class SuscripcionDTO {

    private Long id;

    @NotNull(message = "El usuario principal es requerido")
    private Long usuarioPrincipalId;

    @NotBlank(message = "El plan es requerido")
    private String planId;

    @NotNull(message = "El precio mensual es requerido")
//    @DecimalMin(value = "0.01", message = "El precio debe ser mayor a 0")
    private BigDecimal precioMensual;

    private String preapprovalId;

    private String estado;

    private String metodoPago;

    private String ultimos4Digitos;
}