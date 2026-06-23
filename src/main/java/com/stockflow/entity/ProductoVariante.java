package com.stockflow.entity;

import jakarta.persistence.*;
import lombok.*;
import java.time.LocalDateTime;

@Entity
@Table(name = "producto_variantes")
@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class ProductoVariante {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "producto_id", nullable = false)
    private Long productoId;

    @Column(name = "talla", length = 50)
    private String talla;

    @Column(name = "color", length = 100)
    private String color;

    @Column(name = "stock_actual", nullable = false)
    private Integer stockActual = 0;

    @Column(name = "stock_minimo", nullable = false)
    private Integer stockMinimo = 0;

    @Column(name = "sku", length = 100)
    private String sku;

    @Column(name = "activo", nullable = false)
    private Boolean activo = true;

    @Column(name = "tenant_id")
    private String tenantId;

    @Column(name = "created_at", updatable = false)
    private LocalDateTime createdAt;
}
