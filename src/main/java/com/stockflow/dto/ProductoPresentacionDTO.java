package com.stockflow.dto;

import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;
import lombok.*;

import java.math.BigDecimal;

@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class ProductoPresentacionDTO {

    private Long id;

    @NotNull(message = "El producto es requerido")
    private Long productoId;

    @NotNull(message = "La unidad de medida es requerida")
    private Long unidadMedidaId;

    /** Solo lectura — nombre de la unidad para mostrar en frontend. */
    private String unidadMedidaNombre;

    /** Solo lectura — abreviatura de la unidad para mostrar en el POS. */
    private String unidadMedidaAbreviatura;

    @NotNull(message = "El precio de venta es requerido")
    @DecimalMin(value = "0.01", message = "El precio debe ser mayor a 0")
    private BigDecimal precioVenta;

    /** Cuántas unidades base = 1 de esta presentación. */
    @Min(value = 1, message = "El factor debe ser al menos 1")
    private Integer factor = 1;

    private Boolean esPrincipal = false;

    private String tenantId;
}
