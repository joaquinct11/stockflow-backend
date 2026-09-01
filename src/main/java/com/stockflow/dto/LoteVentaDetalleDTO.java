package com.stockflow.dto;

import lombok.Builder;
import lombok.Data;
import java.math.BigDecimal;

@Data
@Builder
public class LoteVentaDetalleDTO {
    private String lote;
    private String proveedorNombre;
    private Integer cantidadDescontada;
    private BigDecimal precioVenta;
}
