package com.stockflow.dto.superadmin;

import lombok.Builder;
import lombok.Data;

import java.math.BigDecimal;
import java.time.LocalDateTime;

@Data
@Builder
public class TenantResumenDTO {
    private String  tenantId;
    private String  nombre;
    private String  ruc;
    private String  emailContacto;
    private String  rubro;
    private Boolean activo;

    // Suscripción
    private Long          suscripcionId;
    private String        planId;
    private String        estadoSuscripcion;
    private BigDecimal    precioMensual;
    private String        preapprovalId;
    private String        ultimos4Digitos;
    private String        metodoPago;
    private LocalDateTime fechaProximoCobro;
    private LocalDateTime trialEndDate;
    private LocalDateTime currentPeriodEnd;
    private LocalDateTime suscripcionCreadaEn;

    // OSE
    private String oseUrl;
    private String oseToken;

    // Conteos
    private long totalUsuarios;
    private long totalProductos;
    private long totalVentas;
    private long totalComprobantes;
}
