package com.stockflow.dto;

import jakarta.validation.constraints.*;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class RegistrationRequestDTO {

    @NotBlank(message = "Email es requerido")
    @Email(message = "Email debe ser válido")
    private String email;

    @NotBlank(message = "Contraseña es requerida")
    @Size(min = 8, message = "Contraseña debe tener mínimo 8 caracteres")
    private String contraseña;

    @NotBlank(message = "Nombre del usuario es requerido")
    @Size(min = 3, max = 150, message = "Nombre debe tener entre 3 y 150 caracteres")
    private String nombre;

    @NotBlank(message = "Nombre de la farmacia es requerido")
    @Size(min = 3, max = 255, message = "Nombre de farmacia debe tener entre 3 y 255 caracteres")
    private String nombreFarmacia;

    @NotBlank(message = "Plan es requerido")
    @Pattern(regexp = "FREE|BASICO|PRO", message = "Plan debe ser FREE, BASICO o PRO")
    private String planId;
}