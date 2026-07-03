package com.stockflow.dto;

import jakarta.validation.constraints.NotBlank;
import lombok.*;

import java.time.LocalDateTime;

@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class SucursalDTO {

    private Long id;

    @NotBlank(message = "El nombre de la sucursal es requerido")
    private String nombre;

    private String direccion;

    private String telefono;

    private String email;

    private Boolean esPrincipal;

    private Boolean activo;

    private String tenantId;

    private LocalDateTime createdAt;
}
