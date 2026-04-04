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
public class VentaCategoriaDTO {
    /** Nombre de la categoría; null/blank se normaliza a "Sin categoría". */
    private String categoria;
    private long unidades;
    private BigDecimal ingresosTotal;
    private long ventasCount;
}
