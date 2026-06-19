package com.stockflow.dto;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;
import java.time.LocalDate;

/** Una fila parseada del Excel/CSV enviada desde el frontend. */
@Data
@NoArgsConstructor
@AllArgsConstructor
public class ProductoImportRowDTO {

    /** Requerido */
    private String nombre;

    /** Opcional — si existe en BD se actualiza; si no, se crea */
    private String codigoBarras;

    /** Requerido */
    private BigDecimal precioVenta;

    /** Opcional — mapeado desde costoUnitario */
    private BigDecimal costoUnitario;

    /** Opcional — default 0 */
    private Integer stockActual;

    /** Opcional — default 10 */
    private Integer stockMinimo;

    /** Opcional — default 500 */
    private Integer stockMaximo;

    /** Nombre de la unidad de medida (ej. "Unidad", "Caja", "Kg") */
    private String unidadMedida;

    /** Nombre de la categoría (ej. "Medicamentos", "Limpieza") */
    private String categoria;

    /** Número de lote — permite múltiples filas del mismo producto con distinto lote */
    private String lote;

    /** Fecha de vencimiento del lote (YYYY-MM-DD) */
    private LocalDate fechaVencimiento;

    /** Registro sanitario del lote */
    private String registroSanitario;
}
