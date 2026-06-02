package com.stockflow.dto;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class ProveedorDTO {

    private Long id;

    @NotBlank(message = "El nombre del proveedor es requerido")
    private String nombre;

    private String ruc;

    private String contacto;

    private String telefono;

    @Email(message = "Email inválido")
    private String email;

    private String direccion;

    private Boolean activo;

    private String tenantId;

    private LocalDateTime createdAt;
}