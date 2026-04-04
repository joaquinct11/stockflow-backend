package com.stockflow.dto.reportes;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;

@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class MovimientosResumenDTO {
    private long entradasCantidad;
    private long salidasCantidad;
    private List<TopMovimientoProductoDTO> topMovimientosProductos;
}
