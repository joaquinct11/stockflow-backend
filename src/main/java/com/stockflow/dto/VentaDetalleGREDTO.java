package com.stockflow.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class VentaDetalleGREDTO {

    private Long ventaId;
    private String estado;

    private String clienteNombre;
    /** Código SUNAT: 1=DNI, 4=CE, 6=RUC, 7=PASAPORTE */
    private String clienteTipoDoc;
    private String clienteNumDoc;
    private String clienteDireccion;

    private List<ItemDTO> items;

    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class ItemDTO {
        private Long productoId;
        private String productoNombre;
        private Integer cantidad;
        private Integer factor;
        private Long presentacionId;
        /** Código SUNAT de unidad de medida (NIU por defecto). */
        private String unidadMedida;
    }
}
