package com.stockflow.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;
import java.time.LocalDateTime;

@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class NotaCreditoDTO {

    private Long id;
    private String codigo;
    private Long devolucionId;
    private Long ventaOrigenId;
    private BigDecimal montoTotal;
    private String estado;
    private LocalDateTime fechaEmision;
    private LocalDateTime fechaVencimiento;
    private LocalDateTime fechaUso;
    private Long ventaUsoId;
    private String tenantId;

    // Datos del cliente enriquecidos desde la venta origen
    private String clienteNombre;
    private String clienteDocTipo;
    private String clienteDocNumero;
}
