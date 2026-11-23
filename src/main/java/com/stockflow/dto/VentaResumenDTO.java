package com.stockflow.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;
import java.time.LocalDateTime;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class VentaResumenDTO {
    private Long id;
    private LocalDateTime createdAt;
    private String clienteNombre;
    private String clienteDocumento;
    private BigDecimal total;
    private String estado;
}
