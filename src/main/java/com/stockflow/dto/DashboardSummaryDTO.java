package com.stockflow.dto;

import lombok.Builder;
import lombok.Data;
import java.util.List;

@Data
@Builder
public class DashboardSummaryDTO {

    private int totalProductos;
    /** Total de productos activos con stockActual < stockMinimo (sin stock + bajo mínimo). */
    private int bajoStockCount;
    /** Productos activos con stockActual <= 0. */
    private int sinStockCount;
    /** Productos activos con stockActual > 0 y stockActual < stockMinimo (y stockMinimo > 0). */
    private int stockBajoMinimoCount;
    private List<BajoStockItemDTO> bajoStockItems;
    private List<ProximoVencerItemDTO> productosProximosAVencer;

    @Data
    @Builder
    public static class BajoStockItemDTO {
        private Long id;
        private String nombre;
        private String codigoBarras;
        private Integer stockActual;
        private Integer stockMinimo;
    }

    @Data
    @Builder
    public static class ProximoVencerItemDTO {
        private Long id;
        private String nombre;
        private String codigoBarras;
        private Integer stockActual;
        private String fechaVencimiento;
    }
}
