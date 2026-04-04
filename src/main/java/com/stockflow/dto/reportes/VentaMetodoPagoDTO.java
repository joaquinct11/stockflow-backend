package com.stockflow.dto.reportes;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;

@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class VentaMetodoPagoDTO {
    private String metodoPago;
    private long ventasCount;
    private BigDecimal ingresosTotal;
    /** Porcentaje sobre el total de ingresos del período. */
    private BigDecimal porcentaje;
}
