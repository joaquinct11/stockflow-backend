package com.stockflow.entity;

import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

@Entity
@Table(name = "ot_consumo_devolucion")
@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class OtConsumoDevolucion {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "tenant_id", nullable = false, length = 100)
    private String tenantId;

    @Column(name = "consumo_id", nullable = false)
    private Long consumoId;

    @Column(name = "orden_trabajo_id", nullable = false)
    private Long ordenTrabajoId;

    @Column(name = "cantidad_base", nullable = false)
    private Integer cantidadBase;

    @Column(name = "motivo", columnDefinition = "TEXT")
    private String motivo;

    @Column(name = "registrado_por")
    private Long registradoPor;

    @Column(name = "created_at", nullable = false, updatable = false)
    private LocalDateTime createdAt;

    @PrePersist
    protected void prePersist() {
        if (createdAt == null) createdAt = LocalDateTime.now();
    }
}
