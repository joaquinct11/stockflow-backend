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
public class CompraProveedorDTO {
    private Long proveedorId;
    private String proveedorNombre;
    private long recepcionesCount;
    private long unidadesRecibidas;
    /**
     * Monto estimado: SUM(cantidadRecibida * costoUnitario actual del producto).
     * No refleja el costo histórico de compra; úsese como estimado.
     */
    private BigDecimal montoEstimado;
}
