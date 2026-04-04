package com.stockflow.dto.reportes;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class ProductoBajoStockDTO {
    private Long productoId;
    private String nombre;
    private Integer stockActual;
    private Integer stockMinimo;
}
