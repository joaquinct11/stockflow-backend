package com.stockflow.dto;

import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;
import lombok.*;

@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class ProductoVarianteDTO {

    private Long id;

    @NotNull(message = "El producto es requerido")
    private Long productoId;

    private String productoNombre;

    private String talla;
    private String color;

    @Min(value = 0, message = "El stock no puede ser negativo")
    private Integer stockActual = 0;

    @Min(value = 0, message = "El stock mínimo no puede ser negativo")
    private Integer stockMinimo = 0;

    private String sku;
    private Boolean activo = true;
    private String tenantId;
}
