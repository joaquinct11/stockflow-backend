package com.stockflow.dto;

/** Proyección liviana para los schedulers de notificaciones.
 *  Solo los campos que necesitan los jobs diarios; evita cargar
 *  relaciones EAGER (categoría, unidad de medida) innecesarias.
 */
public interface ProductoStockInfo {
    Long    getId();
    String  getNombre();
    Integer getStockActual();
    Integer getStockMinimo();
}
