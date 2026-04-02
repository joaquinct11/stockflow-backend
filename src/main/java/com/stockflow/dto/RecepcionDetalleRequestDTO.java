package com.stockflow.dto;

import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDate;

@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class RecepcionDetalleRequestDTO {

    @NotNull(message = "El producto es requerido")
    private Long productoId;

    @NotNull(message = "La cantidad recibida es requerida")
    @Min(value = 1, message = "La cantidad debe ser mayor a 0")
    private Integer cantidadRecibida;

    private LocalDate fechaVencimiento;
}
