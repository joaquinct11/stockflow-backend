package com.stockflow.dto;

import lombok.Data;
import java.math.BigDecimal;
import java.time.LocalDate;

@Data
public class ActualizarLoteProveedorDTO {
    private Long proveedorId;
    private BigDecimal precioVenta;
    private String lote;
    private LocalDate fechaVencimiento;
}
