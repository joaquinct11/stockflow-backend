package com.stockflow.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;


@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class OtConsumoMaterialDTO {

    private Long id;
    private Long ordenTrabajoId;
    private Long productoId;
    private Long presentacionId;
    private Integer factorPresentacion;
    private Integer cantidadPresentacion;
    private Integer cantidadBase;
    private BigDecimal precioUnitario;
    private String notas;
    private Long registradoPor;
    private LocalDateTime createdAt;
    private List<LoteDTO> lotes;

    // Campos desnormalizados para la consulta GET (nullable en la respuesta POST)
    private String productoNombre;
    private String presentacionNombre;
    private String registradoPorNombre;

    // Fase 2B: tipo de producto, cantidades devueltas/clasificadas y detalle
    private String productoTipo;
    private Integer cantidadDevuelta;
    private Integer cantidadClasificada;
    private List<OtConsumoDevolucionDTO> devoluciones;
    private List<OtConsumoClasificacionDTO> clasificaciones;

    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class LoteDTO {
        private Long stockLoteId;
        private Integer cantidad;
    }
}
