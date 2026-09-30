package com.stockflow.dto.reportes;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;

@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class HorasPicoItemDTO {
    /** Día de la semana: 0=Domingo, 1=Lunes, ..., 6=Sábado (PostgreSQL DOW). */
    private int diaSemana;
    private int hora;
    private long cantidad;
    private BigDecimal total;
}
