package com.stockflow.dto.reportes;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;
import java.util.List;

/**
 * Capital en riesgo de vencimiento, agrupado por buckets de urgencia.
 * Calculado a partir de los movimientos de ENTRADA con fechaVencimiento registrada.
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class VencimientosRiesgoDTO {

    /** Capital cuyo lote ya está vencido hoy. */
    private BigDecimal capitalVencido;
    private Long       lotesVencidos;

    /** Capital que vence en los próximos 7 días (sin contar vencidos). */
    private BigDecimal capitalRiesgo7d;
    private Long       lotesRiesgo7d;

    /** Capital que vence entre 8 y 30 días. */
    private BigDecimal capitalRiesgo30d;
    private Long       lotesRiesgo30d;

    /** Capital que vence entre 31 y 90 días. */
    private BigDecimal capitalRiesgo90d;
    private Long       lotesRiesgo90d;

    /** Total en riesgo real: vencido + próximos 30 días. */
    private BigDecimal totalCapitalCritico;

    /** Los 15 lotes más urgentes (vencidos primero, luego próximos a vencer). */
    private List<LoteRiesgoDetalleDTO> lotesUrgentes;
}
