package com.stockflow.dto.superadmin;

import lombok.Builder;
import lombok.Data;

import java.math.BigDecimal;

@Data
@Builder
public class SuperAdminFinanzasDTO {
    private BigDecimal mrrActual;
    private BigDecimal mrrAnualProyectado;
    private long       cantBasico;
    private long       cantPro;
    private long       tenantsActivos;
    private long       tenantsConOse;
}
