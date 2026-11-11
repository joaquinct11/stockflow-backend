package com.stockflow.entity;

import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;
import java.time.LocalDateTime;

@Entity
@Table(name = "ot_consumo_material")
@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class OtConsumoMaterial {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "tenant_id", nullable = false, length = 100)
    private String tenantId;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "orden_trabajo_id", nullable = false)
    private OrdenTrabajo ordenTrabajo;

    @Column(name = "producto_id", nullable = false)
    private Long productoId;

    @Column(name = "presentacion_id")
    private Long presentacionId;

    @Column(name = "factor_presentacion", nullable = false)
    private Integer factorPresentacion;

    @Column(name = "cantidad_presentacion", nullable = false)
    private Integer cantidadPresentacion;

    @Column(name = "cantidad_base", nullable = false)
    private Integer cantidadBase;

    @Column(name = "precio_unitario", precision = 12, scale = 2)
    private BigDecimal precioUnitario;

    @Column(name = "notas", columnDefinition = "TEXT")
    private String notas;

    @Column(name = "registrado_por")
    private Long registradoPor;

    @Column(name = "created_at", nullable = false, updatable = false)
    private LocalDateTime createdAt;

    @PrePersist
    protected void prePersist() {
        if (createdAt == null) createdAt = LocalDateTime.now();
        if (factorPresentacion == null) factorPresentacion = 1;
    }
}
