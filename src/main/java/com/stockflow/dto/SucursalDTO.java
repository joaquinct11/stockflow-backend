package com.stockflow.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
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

    private Boolean bloqueadaPorPlan;

    private String tenantId;

    private LocalDateTime createdAt;

    /** Ubigeo SUNAT (6 dígitos). Requerido para usar la sucursal como punto GRE. */
    @Pattern(regexp = "^(\\d{6})?$", message = "El ubigeo SUNAT debe tener exactamente 6 dígitos o estar vacío")
    private String ubigeo;

    /** Dirección formateada para documentos tributarios (GRE). */
    private String direccionCompleta;
}
