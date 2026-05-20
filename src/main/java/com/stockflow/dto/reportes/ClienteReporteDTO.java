package com.stockflow.dto.reportes;

import com.fasterxml.jackson.annotation.JsonFormat;
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
public class ClienteReporteDTO {
    private Long          clienteId;
    private String        clienteNombre;
    private Long          ventasCount;
    private BigDecimal    totalComprado;
    private BigDecimal    ticketPromedio;

    @JsonFormat(pattern = "yyyy-MM-dd'T'HH:mm:ss")
    private LocalDateTime ultimaCompra;
}
