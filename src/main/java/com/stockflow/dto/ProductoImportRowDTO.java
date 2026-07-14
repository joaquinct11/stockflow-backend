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

    /** Número de lote — FARMACIA/BOTICA: múltiples filas por lote */
    private String lote;

    /** Fecha de vencimiento del lote — FARMACIA/BOTICA */
    private LocalDate fechaVencimiento;

    /** Registro sanitario — FARMACIA/BOTICA */
    private String registroSanitario;

    /** Talla de la variante — TIENDA */
    private String talla;

    /** Color de la variante — TIENDA */
    private String color;

    /** SKU de la variante — TIENDA */
    private String skuVariante;

    /** Stock inicial de la variante — TIENDA */
    private Integer stockVariante;

    /** Stock mínimo de la variante — TIENDA */
    private Integer stockMinimoVariante;
}
