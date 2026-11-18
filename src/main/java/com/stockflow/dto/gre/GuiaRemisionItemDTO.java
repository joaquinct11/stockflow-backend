package com.stockflow.dto.gre;

import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import lombok.Data;

import java.math.BigDecimal;

@Data
public class GuiaRemisionItemDTO {

    @NotNull(message = "El productoId es obligatorio")
    private Long productoId;

    /** Descripción del bien; si es null se usa el nombre del producto. */
    private String descripcion;

    @NotNull(message = "La cantidad es obligatoria")
    @DecimalMin(value = "0.001", message = "La cantidad debe ser mayor a cero")
    private BigDecimal cantidad;

    /** Código SUNAT unidad de medida (Catálogo 03). Default NIU = unidad. */
    private String unidadMedida = "NIU";

    /** ID de la presentación farmacéutica (opcional). Cuando se envía, el backend
     *  usa su factor para convertir cantidad → cantidadBase en validación de stock y movimientos. */
    private Long presentacionId;
}
