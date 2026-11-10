package com.stockflow.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/** Representación mínima de un profesional elegible para asignar a una cita. */
@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class ProfesionalElegibleDTO {

    /** ID de la membresía UsuarioTenant (es el valor que va en Cita.usuarioTenantId). */
    private Long   usuarioTenantId;
    private String nombre;
    private String rolNombre;
    private Long   sucursalId;
}
