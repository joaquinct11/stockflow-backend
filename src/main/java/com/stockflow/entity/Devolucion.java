package com.stockflow.entity;

import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;

@Entity
@Table(name = "devoluciones")
@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class Devolucion {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "tenant_id", nullable = false)
    private String tenantId;

    @ManyToOne
    @JoinColumn(name = "venta_id", nullable = false)
    private Venta venta;

    @ManyToOne
    @JoinColumn(name = "usuario_id", nullable = false)
    private Usuario usuario;

    @Column(nullable = false, length = 200)
    private String motivo;

    @Column(columnDefinition = "TEXT")
    private String observaciones;

    @Column(name = "total_devuelto", nullable = false, precision = 12, scale = 2)
    private BigDecimal totalDevuelto;

    @Column(name = "reponer_stock", nullable = false)
    private boolean reponerStock = true;

    @Column(nullable = false, length = 20)
    private String estado = "PROCESADA";

    @Column(name = "sucursal_id")
    private Long sucursalId;

    @Column(name = "fecha_devolucion")
    private LocalDateTime fechaDevolucion;

    @Column(name = "created_at", updatable = false)
    private LocalDateTime createdAt;

    @OneToMany(mappedBy = "devolucion", cascade = CascadeType.ALL, orphanRemoval = true)
    @Builder.Default
    private List<DevolucionDetalle> detalles = new ArrayList<>();

    @PrePersist
    void prePersist() {
        if (createdAt == null) createdAt = LocalDateTime.now();
        if (fechaDevolucion == null) fechaDevolucion = LocalDateTime.now();
    }
}
