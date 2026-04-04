package com.stockflow.dto.reportes;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class ReportesResumenDTO {
    private RangoDTO rango;
    private InventarioResumenDTO inventario;
    private MovimientosResumenDTO movimientos;
    private ComprasRecepcionesResumenDTO comprasRecepciones;
    /**
     * Sección de ventas. Null si no hay datos de ventas disponibles en el rango.
     */
    private VentasResumenDTO ventas;
}
