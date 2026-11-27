package com.stockflow.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/** F-23: Variante de producto con su stock en la sucursal solicitada (o global si no hay sucursalId). */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class VarianteConStockDTO {

    private Long id;
    private String talla;
    private String color;
    private String sku;
    private Integer stockEnSucursal;
}
