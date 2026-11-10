package com.stockflow.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class CrearOtRequestDTO {

    @NotNull(message = "El ID del equipo es requerido")
    private Long equipoClienteId;

    @NotNull(message = "El ID del cliente es requerido")
    private Long clienteId;

    @NotNull(message = "El ID de la sucursal es requerido")
    private Long sucursalId;

    private Long tecnicoId;

    @NotBlank(message = "La descripción del problema es requerida")
    private String descripcionProblema;

    private String prioridad;

    private String observaciones;
}
