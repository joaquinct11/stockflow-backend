package com.stockflow.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class CrearEquipoRequestDTO {

    @NotNull(message = "El clienteId es obligatorio")
    private Long clienteId;

    @NotBlank(message = "El nombre es obligatorio")
    @Size(max = 255, message = "El nombre no puede superar los 255 caracteres")
    private String nombre;

    @Size(max = 100)
    private String tipo;

    @Size(max = 100)
    private String marca;

    @Size(max = 100)
    private String modelo;

    @Size(max = 100)
    private String numeroSerie;

    @Size(max = 255)
    private String ubicacion;

    private String observaciones;
}
