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
public class ComprasRecepcionesResumenDTO {
    private long recepcionesConfirmadasCount;
    private long unidadesRecibidas;
    /**
     * Monto estimado de compras: sum(cantidadRecibida * costoUnitario del producto) para
     * recepciones confirmadas en el rango. Puede ser null si no hay costos registrados.
     */
    private BigDecimal montoComprasEstimado;
}
