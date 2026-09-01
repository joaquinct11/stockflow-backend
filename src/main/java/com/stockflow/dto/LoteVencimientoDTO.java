package com.stockflow.dto;

import com.fasterxml.jackson.annotation.JsonFormat;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDate;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class LoteVencimientoDTO {

    private Long   movimientoId;
    private Long   productoId;
    private String productoNombre;
    private String codigoBarras;
    private String lote;

    @JsonFormat(pattern = "yyyy-MM-dd")
    private LocalDate fechaVencimiento;

    private Integer cantidad;

    /** Stock actual del producto (total, no por lote) */
    private Integer stockActual;

    /** Días hasta vencimiento: negativo = ya vencido */
    private Long diasRestantes;

    private String registroSanitario;

    private String proveedorNombre;

    private java.math.BigDecimal precioVenta;
}
