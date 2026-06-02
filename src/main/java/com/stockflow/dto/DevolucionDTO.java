package com.stockflow.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;

@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class DevolucionDTO {

    private Long id;
    private Long ventaId;
    private String tenantId;
    private String usuarioNombre;
    private String motivo;
    private String observaciones;
    private BigDecimal totalDevuelto;
    private boolean reponerStock;
    private String estado;
    private LocalDateTime fechaDevolucion;
    private List<DevolucionDetalleRespDTO> detalles;

    // Nota de Credito generada automaticamente
    private String notaCreditoCodigo;
    private BigDecimal montoNotaCredito;
    private LocalDateTime fechaVencimientoNc;
}
