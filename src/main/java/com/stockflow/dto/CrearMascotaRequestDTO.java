package com.stockflow.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;
import java.time.LocalDate;

@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class CrearMascotaRequestDTO {

    @NotNull(message = "El cliente es requerido")
    private Long clienteId;

    @NotBlank(message = "El nombre de la mascota es requerido")
    private String nombre;

    @NotBlank(message = "La especie es requerida")
    private String especie;

    private String raza;
    private String sexo;
    private LocalDate fechaNacimiento;
    private String color;
    private BigDecimal pesoKg;
    private String microchip;
    private Boolean esterilizado;
}
