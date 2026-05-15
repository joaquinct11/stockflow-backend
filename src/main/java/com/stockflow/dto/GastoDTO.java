package com.stockflow.dto;

import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;

@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class GastoDTO {

    private Long id;

    @NotBlank(message = "El concepto es obligatorio")
    private String concepto;

    @NotBlank(message = "La categoría es obligatoria")
    private String categoria;

    @NotNull(message = "El monto es obligatorio")
    @DecimalMin(value = "0.01", message = "El monto debe ser mayor a 0")
    private BigDecimal monto;

    @NotNull(message = "La fecha del gasto es obligatoria")
    private LocalDate fechaGasto;

    private String metodoPago;

    private String numeroComprobante;

    private String notas;

    private Boolean activo;

    private String tenantId;

    private String registradoPor;

    private LocalDateTime createdAt;
}
