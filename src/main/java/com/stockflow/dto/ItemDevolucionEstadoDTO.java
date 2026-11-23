package com.stockflow.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class ItemDevolucionEstadoDTO {
    private Long detalleVentaId;
    private Long productoId;
    private String productoNombre;
    private Long varianteId;
    private String varianteDescripcion;
    private Long stockLoteId;
    private Long presentacionId;
    private Integer factor;
    private Integer cantidadVendida;
    private Integer cantidadDevuelta;
    private Integer cantidadPendiente;
}
