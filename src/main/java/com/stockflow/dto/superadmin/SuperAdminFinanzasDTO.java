package com.stockflow.dto.superadmin;

import lombok.Builder;
import lombok.Data;
import java.math.BigDecimal;

@Data
@Builder
public class SuperAdminFinanzasDTO {
    private BigDecimal mrr;
    private long cantBasico;
    private long cantPro;
    private long activos;
    private long conOse;
}
