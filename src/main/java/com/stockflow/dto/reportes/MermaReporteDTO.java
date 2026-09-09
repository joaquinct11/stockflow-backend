package com.stockflow.dto.reportes;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import java.time.LocalDateTime;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class MermaReporteDTO {
    private Long   id;
    private String productoNombre;
    private Long   productoId;
    private Integer cantidad;
    private String lote;
    private String motivo;
    private String observaciones;
    private String referencia;
    private LocalDateTime fecha;
}
