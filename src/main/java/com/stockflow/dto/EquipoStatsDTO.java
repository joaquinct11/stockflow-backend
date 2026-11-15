package com.stockflow.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class EquipoStatsDTO {

    private long total;
    private long activos;
    private long inactivos;
    private long clientesConEquipos;
}
