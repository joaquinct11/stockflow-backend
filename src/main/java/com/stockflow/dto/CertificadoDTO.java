package com.stockflow.dto;

import lombok.*;

import java.time.LocalDate;

@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class CertificadoDTO {

    private Long id;

    /** CARNET_SANIDAD | FUMIGACION | EXTINTOR | INSTALACION_ELECTRICA | OTRO */
    private String tipo;

    /** Nombre del trabajador o descripción del documento */
    private String descripcion;

    /** Solo para CARNET_SANIDAD */
    private Long   usuarioId;
    private String usuarioNombre;

    private LocalDate fechaVencimiento;
    private Integer   diasAlerta;
    private String    observaciones;

    private Long sucursalId;

    /** VIGENTE | POR_VENCER | VENCIDO — calculado, no almacenado */
    private String estado;

    /** Días hasta el vencimiento (negativo si ya venció) */
    private long diasRestantes;
}
