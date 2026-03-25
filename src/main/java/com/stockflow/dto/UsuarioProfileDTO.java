package com.stockflow.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;
import java.util.List;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class UsuarioProfileDTO {
    private Long usuarioId;
    private String email;
    private String nombre;
    private String rol;
    private String tenantId;
    private LocalDateTime createdAt;
    private LocalDateTime ultimoLogin;
    private Boolean activo;
    private String nombreFarmacia;
    private List<String> permisos;
}