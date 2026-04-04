package com.stockflow.dto.reportes;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;
import java.util.List;

@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class VentasResumenDTO {
    private long ventasCount;
    private BigDecimal ingresosTotal;
    private BigDecimal ticketPromedio;
    private List<TopProductoVendidoDTO> topProductosVendidos;
    /**
     * Margen estimado: ingresos - costo (sum(cantidad * costoUnitario del producto)).
     * Puede ser null si no hay datos de costo suficientes.
     */
    private BigDecimal margenEstimado;
}
