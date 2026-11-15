package com.stockflow.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;

@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class MascotaDTO {

    private Long id;
    private Long clienteId;
    private String nombre;
    private String especie;
    private String raza;
    private String sexo;
    private LocalDate fechaNacimiento;
    private String color;
    private BigDecimal pesoKg;
    private String microchip;
    private Boolean esterilizado;
    private Boolean activo;
    private String tenantId;
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;
}
