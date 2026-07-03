package com.stockflow.dto;

import com.fasterxml.jackson.annotation.JsonProperty;
import jakarta.validation.constraints.Email;
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
public class UsuarioDTO {

    private Long id;

    @NotBlank(message = "El email es requerido")
    @Email(message = "El email debe ser válido")
    private String email;

    /** No requerida al crear desde el admin — el sistema genera contraseña temporal y envía link de activación. */
    @JsonProperty(access = JsonProperty.Access.WRITE_ONLY)
    private String contraseña;

    @NotBlank(message = "El nombre es requerido")
    @Size(min = 2, max = 80, message = "El nombre debe tener entre 2 y 80 caracteres")
    private String nombre;

    @Size(max = 150, message = "El apellido no puede superar 150 caracteres")
    private String apellido;

    @NotBlank(message = "El rol es requerido")
    private String rolNombre;

    private Boolean activo;

    private String tenantId;

    private String tipoDocumento;

    private String numeroDocumento;

    private String numeroCelular;

    /** ID de la sucursal asignada. NULL = ADMIN o plan Básico. */
    private Long sucursalId;
}