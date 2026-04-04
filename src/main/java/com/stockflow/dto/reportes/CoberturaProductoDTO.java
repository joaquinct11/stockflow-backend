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
public class CoberturaProductoDTO {
    private Long productoId;
    private String nombre;
    private Integer stockActual;
    /** Promedio de unidades de SALIDA por día en el período analizado. */
    private BigDecimal promedioSalidasDiarias;
    /**
     * stockActual / promedioSalidasDiarias.
     * Null si el promedio es 0 (sin salidas en el período).
     */
    private BigDecimal diasCobertura;
}
