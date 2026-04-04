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
public class ProductoVentaDTO {
    private Long productoId;
    private String nombre;
    private long cantidad;
    private BigDecimal ingresos;
}
