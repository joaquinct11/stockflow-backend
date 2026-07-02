package com.stockflow.dto;

import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import lombok.*;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;

@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class ComisionDTO {

    private Long id;

    @NotBlank(message = "El concepto es requerido")
    private String concepto;

    @NotBlank(message = "El pagador es requerido")
    private String pagador;

    @NotNull(message = "El monto es requerido")
    @DecimalMin(value = "0.01", message = "El monto debe ser mayor a 0")
    private BigDecimal monto;

    @NotNull(message = "La fecha es requerida")
    private LocalDate fecha;

    private String metodoPago;
    private String numeroComprobante;
    private String notas;
    private String tenantId;
    private String registradoPor;
    private LocalDateTime createdAt;
    private Long sucursalId;
}
