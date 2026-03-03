package com.stockflow.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

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
}