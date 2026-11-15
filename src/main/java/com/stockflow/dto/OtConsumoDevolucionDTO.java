package com.stockflow.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;
import java.util.List;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class OtConsumoDevolucionDTO {

    private Long id;
    private Long consumoId;
    private Long ordenTrabajoId;
    private Integer cantidadBase;
    private String motivo;
    private Long registradoPor;
    private String registradoPorNombre;
    private LocalDateTime createdAt;
    private List<LoteDTO> lotes;

    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class LoteDTO {
        private Long stockLoteId;
        private Integer cantidad;
    }
}
