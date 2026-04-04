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
public class VentaTendenciaDTO {
    /** Periodo formateado: YYYY-MM-DD para DIA/SEMANA, YYYY-MM para MES. */
    private String periodo;
    private long ventasCount;
    private BigDecimal ingresosTotal;
}
