package com.stockflow.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;

@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class DevolucionDetalleRespDTO {

    private Long productoId;
    private String productoNombre;
    private Integer cantidadDevuelta;
    private BigDecimal precioUnitario;
    private BigDecimal subtotal;
}
