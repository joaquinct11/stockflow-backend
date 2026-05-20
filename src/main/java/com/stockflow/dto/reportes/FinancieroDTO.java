package com.stockflow.dto.reportes;

import com.fasterxml.jackson.annotation.JsonFormat;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;

/**
 * Estado de resultados simplificado para el período indicado.
 * Ingresos → Costo de Ventas → Utilidad Bruta → Gastos Operativos → Utilidad Neta.
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class FinancieroDTO {

    @JsonFormat(pattern = "yyyy-MM-dd")
    private LocalDate desde;

    @JsonFormat(pattern = "yyyy-MM-dd")
    private LocalDate hasta;

    // ── Ingresos ──────────────────────────────────────────
    private BigDecimal ingresosVentas;
    private Long       ventasCount;

    // ── Costo de ventas ────────────────────────────────────
    /** Suma de (cantidad × costoUnitario) de cada línea de venta. */
    private BigDecimal costoVentas;

    // ── Utilidad bruta ─────────────────────────────────────
    private BigDecimal utilidadBruta;
    /** Porcentaje sobre ingresosVentas. Null si ingresosVentas = 0. */
    private BigDecimal margenBruto;

    // ── Gastos operativos (módulo Gastos) ──────────────────
    private BigDecimal         gastosTotales;
    private Long               gastosCount;
    private List<GastoCategoriaDTO> gastosPorCategoria;

    // ── Utilidad neta ──────────────────────────────────────
    private BigDecimal utilidadNeta;
    /** Porcentaje sobre ingresosVentas. Null si ingresosVentas = 0. */
    private BigDecimal margenNeto;
}
