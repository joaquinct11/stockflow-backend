package com.stockflow.dto;

import lombok.Data;
import java.math.BigDecimal;

@Data
public class ActualizarLoteProveedorDTO {
    private Long proveedorId;
    private BigDecimal precioVenta;
}
