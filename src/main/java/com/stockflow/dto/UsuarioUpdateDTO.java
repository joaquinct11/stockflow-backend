package com.stockflow.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class UsuarioUpdateDTO {

    @NotBlank(message = "El nombre es requerido")
    @Size(min = 2, max = 80, message = "El nombre debe tener entre 2 y 80 caracteres")
    private String nombre;

    @Size(max = 150)
    private String apellido;

    @NotBlank(message = "El rol es requerido")
    private String rolNombre;

    private String tenantId;

    private Boolean activo;

    private String tipoDocumento;

    private String numeroDocumento;

    private String numeroCelular;
}