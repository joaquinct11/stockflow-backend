package com.stockflow.entity;

import jakarta.persistence.*;
import lombok.*;

import java.time.LocalDate;
import java.time.LocalDateTime;

@Entity
@Table(name = "certificados_establecimiento")
@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class CertificadoEstablecimiento {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "tenant_id", nullable = false)
    private String tenantId;

    /** CARNET_SANIDAD | FUMIGACION | EXTINTOR | INSTALACION_ELECTRICA | OTRO */
    @Column(nullable = false, length = 50)
    private String tipo;

    /** Nombre del trabajador (carnets) o descripción del documento */
    @Column(nullable = false, length = 255)
    private String descripcion;

    /** Solo para CARNET_SANIDAD — FK al usuario del sistema */
    @Column(name = "usuario_id")
    private Long usuarioId;

    @Column(name = "fecha_vencimiento", nullable = false)
    private LocalDate fechaVencimiento;

    /** Días de anticipación para alertar antes del vencimiento */
    @Column(name = "dias_alerta", nullable = false)
    private Integer diasAlerta = 30;

    @Column(columnDefinition = "TEXT")
    private String observaciones;

    @Column(nullable = false)
    private Boolean activo = true;

    @Column(name = "sucursal_id")
    private Long sucursalId;

    @Column(name = "created_at", nullable = false)
    private LocalDateTime createdAt;

    @Column(name = "updated_at")
    private LocalDateTime updatedAt;

    @PrePersist
    public void prePersist() {
        createdAt = LocalDateTime.now();
        updatedAt = LocalDateTime.now();
    }

    @PreUpdate
    public void preUpdate() {
        updatedAt = LocalDateTime.now();
    }
}
