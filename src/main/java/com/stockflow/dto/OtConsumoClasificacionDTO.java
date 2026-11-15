package com.stockflow.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class OtConsumoClasificacionDTO {

    private Long id;
    private Long consumoId;
    private Long ordenTrabajoId;
    private String clasificacion;
    private Integer cantidad;
    private String notas;
    private Long registradoPor;
    private String registradoPorNombre;
    private LocalDateTime createdAt;
}
