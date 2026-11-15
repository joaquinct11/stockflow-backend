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

    // Contacto y facturación
    private String ruc;
    private String emailContacto;
    private String oseUrl;
    private String oseToken;

    // Suscripción extendida
    private Long suscripcionId;
    private String preapprovalId;
    private String ultimos4Digitos;
    private String metodoPago;
    private LocalDateTime currentPeriodEnd;
    private LocalDateTime suscripcionCreadaEn;

    // Métricas de uso
    private long totalUsuarios;
    private long totalProductos;
    private long totalVentas;
    private long totalComprobantes;
}
