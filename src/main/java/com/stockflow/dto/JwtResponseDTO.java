package com.stockflow.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;

@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class JwtResponseDTO {

    private String accessToken;
    private String refreshToken;
    private String tipo = "Bearer";
    private Long usuarioId;
    private String email;
    private String nombre;
    private String rol;
    private String tenantId;
    private Integer expiresIn;
    private SuscripcionDTO suscripcion;
    /** Sucursal fija del usuario (null = ADMIN, puede ver todas). */
    private Long sucursalId;

    // Multi-tenant: solo presente cuando el usuario pertenece a >1 tenant (Case B).
    // Si es null el frontend continúa directo al dashboard (Case A).
    private String selectionToken;
    private List<TenantInfoDTO> tenants;
}