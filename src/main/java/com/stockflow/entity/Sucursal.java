package com.stockflow.entity;

import jakarta.persistence.*;
import lombok.*;

import java.time.LocalDateTime;

@Entity
@Table(name = "sucursales")
@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class Sucursal {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "tenant_id", nullable = false)
    private String tenantId;

    @Column(nullable = false)
    private String nombre;

    private String direccion;

    private String telefono;

    private String email;

    @Column(name = "es_principal", nullable = false)
    @Builder.Default
    private Boolean esPrincipal = false;

    @Column(nullable = false)
    @Builder.Default
    private Boolean activo = true;

    @Column(name = "bloqueada_por_plan", nullable = false)
    @Builder.Default
    private Boolean bloqueadaPorPlan = false;

    /** Código UBIGEO SUNAT (6 dígitos). Requerido para GRE como punto de partida/llegada. */
    @Column(name = "ubigeo", length = 10)
    private String ubigeo;

    /** Dirección formateada para documentos tributarios. */
    @Column(name = "direccion_completa", length = 500)
    private String direccionCompleta;

    @Column(name = "deleted_at")
    private LocalDateTime deletedAt;

    @PrePersist
    public void aplicarDefaults() {
        if (bloqueadaPorPlan == null) bloqueadaPorPlan = false;
        if (activo == null) activo = true;
        if (esPrincipal == null) esPrincipal = false;
        if (createdAt == null) createdAt = LocalDateTime.now();
        if (updatedAt == null) updatedAt = LocalDateTime.now();
    }

    @Column(name = "created_at", nullable = false)
    @Builder.Default
    private LocalDateTime createdAt = LocalDateTime.now();

    @Column(name = "updated_at", nullable = false)
    @Builder.Default
    private LocalDateTime updatedAt = LocalDateTime.now();
}
