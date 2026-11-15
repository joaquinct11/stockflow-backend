package com.stockflow.dto;

import lombok.AllArgsConstructor;
import lombok.Getter;

@Getter
@AllArgsConstructor
public class MascotaStatsDTO {
    private long total;
    private long activas;
    private long inactivas;
}
