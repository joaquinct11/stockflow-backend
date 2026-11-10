package com.stockflow.dto.superadmin;

import lombok.Builder;
import lombok.Data;
import java.math.BigDecimal;
import java.time.LocalDateTime;

@Data
@Builder
public class TenantResumenDTO {
    private String tenantId;
    private String nombre;
    private Boolean activo;
    private String rubro;
    private String planId;
    private String planCodigo;
    private BigDecimal precioMensual;
    private String estadoSuscripcion;
    private LocalDateTime fechaProximoCobro;
    private LocalDateTime trialEndDate;
    private boolean tieneOse;
}
