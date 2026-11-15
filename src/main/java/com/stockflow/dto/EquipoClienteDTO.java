package com.stockflow.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class EquipoClienteDTO {

    private Long id;
    private String tenantId;
    private Long clienteId;
    private String clienteNombre;
    private String nombre;
    private String tipo;
    private String marca;
    private String modelo;
    private String numeroSerie;
    private String ubicacion;
    private String observaciones;
    private Boolean activo;
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;
}
