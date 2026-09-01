package com.stockflow.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;
import java.time.LocalDate;

/** Lote disponible para seleccionar en el POS, con info de proveedor. */
@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class StockLoteDisponibleDTO {

    private Long id;
    private String lote;
    private LocalDate fechaVencimiento;
    private Integer stockActual;
    private Long proveedorId;
    private String proveedorNombre;
    private Integer diasParaVencer;
    private BigDecimal precioVenta;
    private BigDecimal costoUnitario;
}
