package com.stockflow.dto.superadmin;

import lombok.Builder;
import lombok.Data;

import java.math.BigDecimal;

@Data
@Builder
public class SuperAdminStatsDTO {
    private long       totalTenants;
    private long       activos;
    private long       enTrial;
    private long       vencidos;
    private long       cancelados;
    private long       pastDue;
    private BigDecimal mrrEstimado;
}
