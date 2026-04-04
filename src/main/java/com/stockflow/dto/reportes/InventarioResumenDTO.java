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
public class InventarioResumenDTO {
    private long totalProductos;
    private List<ProductoBajoStockDTO> productosBajoStock;
    /**
     * Valorización del stock: sum(stockActual * costoUnitario) por producto del tenant.
     * Se incluye siempre que los productos tengan costoUnitario definido.
     */
    private BigDecimal valorizacionStock;
}
