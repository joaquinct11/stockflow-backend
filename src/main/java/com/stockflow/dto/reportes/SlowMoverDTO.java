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
public class SlowMoverDTO {
    private Long productoId;
    private String nombre;
    private Integer stockActual;
    /**
     * Costo unitario actual del producto (no histórico de compra).
     * Úsese como estimado.
     */
    private BigDecimal costoUnitario;
    /** costoUnitario * stockActual (estimado con costo actual). */
    private BigDecimal costoTotal;
    /** Días sin movimiento de SALIDA configurados en el request. */
    private int diasSinSalida;
}
