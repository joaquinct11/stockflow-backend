package com.stockflow.dto;

import jakarta.validation.constraints.Min;
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
public class DevolucionDetalleItemDTO {

    @NotNull(message = "El productoId es requerido")
    private Long productoId;

    @NotNull(message = "La cantidadDevuelta es requerida")
    @Min(value = 1, message = "La cantidad a devolver debe ser mayor a 0")
    private Integer cantidadDevuelta;

    @NotNull(message = "El precioUnitario es requerido")
    private BigDecimal precioUnitario;
}
