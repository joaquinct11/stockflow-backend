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
public class ProductoAbcDTO {
    private Long productoId;
    private String nombre;
    /** Ingresos generados por el producto en el período. */
    private BigDecimal ingresos;
    /** Porcentaje individual sobre el total de ingresos. */
    private BigDecimal porcentaje;
    /** Porcentaje acumulado (orden desc por ingresos). */
    private BigDecimal porcentajeAcumulado;
    /** Clasificación ABC: A (0-80%), B (80-95%), C (95-100%). */
    private String clasificacion;
}
