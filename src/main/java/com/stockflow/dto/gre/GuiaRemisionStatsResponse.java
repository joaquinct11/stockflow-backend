package com.stockflow.dto.gre;

import lombok.Builder;
import lombok.Data;

@Data
@Builder
public class GuiaRemisionStatsResponse {
    private long total;
    private long emitidas;
    private long pendientes;
    private long conError;
}
