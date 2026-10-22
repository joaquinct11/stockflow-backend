package com.stockflow.dto;

import com.stockflow.entity.EstadoCita;
import com.stockflow.entity.TipoCita;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

/** Respuesta completa de una cita con datos del cliente, mascota y profesional. */
@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class CitaDTO {
    private Long          id;
    private String        tenantId;
    private Long          sucursalId;

    // Cliente
    private Long          clienteId;
    private String        clienteNombre;

    // Mascota
    private Long          mascotaId;
    private String        mascotaNombre;
    private String        mascotaEspecie;

    // Profesional
    private Long          usuarioTenantId;
    private String        profesionalNombre;

    // Quién registró
    private Long          creadoPorId;

    private LocalDateTime fechaInicio;
    private LocalDateTime fechaFin;
    private TipoCita      tipo;
    private String        motivo;
    private EstadoCita    estado;
    private String        observaciones;
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;
}
