package com.stockflow.dto.reportes;

import com.fasterxml.jackson.annotation.JsonFormat;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;
import java.time.LocalDate;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class LoteRiesgoDetalleDTO {
    private Long       productoId;
    private String     productoNombre;
    private String     lote;

    @JsonFormat(pattern = "yyyy-MM-dd")
    private LocalDate  fechaVencimiento;

    private Long       diasRestantes;   // negativo = ya vencido
    private Integer    cantidad;
    private BigDecimal costoUnitario;
    private BigDecimal valorRiesgo;     // cantidad × costoUnitario
}
