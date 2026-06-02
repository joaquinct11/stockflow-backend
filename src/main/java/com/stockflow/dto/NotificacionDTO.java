package com.stockflow.dto;

import lombok.*;
import java.time.LocalDateTime;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class NotificacionDTO {
    private Long          id;
    private String        tipo;
    private String        titulo;
    private String        mensaje;
    private boolean       leida;
    private Long          referenciaId;
    private String        referenciaTipo;
    private LocalDateTime createdAt;
}
