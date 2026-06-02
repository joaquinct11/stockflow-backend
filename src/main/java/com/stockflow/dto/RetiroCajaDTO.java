package com.stockflow.dto;

import lombok.*;
import java.math.BigDecimal;
import java.time.LocalDateTime;

@Data @NoArgsConstructor @AllArgsConstructor @Builder
public class RetiroCajaDTO {
    private Long id;
    private Long cajaId;
    private Long usuarioId;
    private String usuarioNombre;
    private BigDecimal monto;
    private String motivo;
    private LocalDateTime fecha;
}
