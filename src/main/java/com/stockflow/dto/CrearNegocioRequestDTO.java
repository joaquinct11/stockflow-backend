package com.stockflow.dto;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class CrearNegocioRequestDTO {

    @NotBlank(message = "El nombre del negocio es obligatorio")
    private String nombreNegocio;

    private String rubro;

    @Pattern(
        regexp = "^\\d{11}$|^$",
        message = "El RUC debe tener exactamente 11 dígitos"
    )
    private String ruc;

    @Pattern(
        regexp = "^[9]\\d{8}$|^$",
        message = "El celular debe tener 9 dígitos y comenzar con 9 (ej: 987654321)"
    )
    private String telefono;

    @Email(message = "Ingresa un correo electrónico válido (ej: juan@empresa.com)")
    private String emailContacto;

    @NotBlank(message = "El plan es obligatorio")
    private String planId;
}
