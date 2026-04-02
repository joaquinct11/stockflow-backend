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
public class OrdenCompraItemDTO {

    private Long id;

    @NotNull(message = "El producto es requerido")
    private Long productoId;

    private String productoNombre;

    @NotNull(message = "La cantidad solicitada es requerida")
    @Min(value = 1, message = "La cantidad debe ser mayor a 0")
    private Integer cantidadSolicitada;

    private BigDecimal precioUnitario;

    /** Cantidad total ya recibida en recepciones CONFIRMADAS para este ítem. */
    private Integer cantidadRecibida;

    /** Cantidad aún pendiente de recibir (cantidadSolicitada - cantidadRecibida). */
    private Integer cantidadPendiente;
}
