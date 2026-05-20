package com.stockflow.entity;

import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;
import java.time.LocalDateTime;

@Entity
@Table(name = "notas_credito")
@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class NotaCredito {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "tenant_id", nullable = false, length = 100)
    private String tenantId;

    @Column(nullable = false, length = 20)
    private String codigo;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "devolucion_id", nullable = false)
    private Devolucion devolucion;

    @Column(name = "monto_total", nullable = false, precision = 12, scale = 2)
    private BigDecimal montoTotal;

    @Column(nullable = false, length = 20)
    @Builder.Default
    private String estado = "PENDIENTE";

    @Column(name = "fecha_emision", nullable = false)
    private LocalDateTime fechaEmision;

    @Column(name = "fecha_vencimiento", nullable = false)
    private LocalDateTime fechaVencimiento;

    @Column(name = "fecha_uso")
    private LocalDateTime fechaUso;

    @Column(name = "venta_uso_id")
    private Long ventaUsoId;

    @Column(name = "created_at", updatable = false)
    private LocalDateTime createdAt;

    @PrePersist
    void prePersist() {
        if (createdAt == null) createdAt = LocalDateTime.now();
        if (fechaEmision == null) fechaEmision = LocalDateTime.now();
        if (fechaVencimiento == null) fechaVencimiento = fechaEmision.plusDays(90);
    }
}
