package com.stockflow.entity;

import jakarta.persistence.*;
import lombok.*;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;

@Entity
@Table(name = "stock_lotes")
@Data @NoArgsConstructor @AllArgsConstructor @Builder
public class StockLote {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "tenant_id", nullable = false)
    private String tenantId;

    @Column(name = "movimiento_id", nullable = false, unique = true)
    private Long movimientoId;

    @Column(name = "producto_id", nullable = false)
    private Long productoId;

    @Column(name = "sucursal_id")
    private Long sucursalId;

    @Column(name = "proveedor_id")
    private Long proveedorId;

    @Column(name = "costo_unitario", precision = 12, scale = 2)
    private BigDecimal costoUnitario;

    @Column(name = "precio_venta", precision = 12, scale = 2)
    private BigDecimal precioVenta;

    @Column(length = 100)
    private String lote;

    @Column(name = "fecha_vencimiento", nullable = false)
    private LocalDate fechaVencimiento;

    @Column(name = "stock_inicial", nullable = false)
    private Integer stockInicial;

    @Column(name = "stock_actual", nullable = false)
    private Integer stockActual;

    @Column(name = "created_at", nullable = false, updatable = false)
    private LocalDateTime createdAt;

    @Column(name = "updated_at")
    private LocalDateTime updatedAt;

    @PrePersist
    void prePersist() {
        if (createdAt == null) createdAt = LocalDateTime.now();
        updatedAt = LocalDateTime.now();
    }

    @PreUpdate
    void preUpdate() {
        updatedAt = LocalDateTime.now();
    }
}
