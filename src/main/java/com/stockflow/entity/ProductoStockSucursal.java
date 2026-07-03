package com.stockflow.entity;

import jakarta.persistence.*;
import lombok.*;

@Entity
@Table(name = "producto_stock_sucursal")
@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class ProductoStockSucursal {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "producto_id", nullable = false)
    private Producto producto;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "sucursal_id", nullable = false)
    private Sucursal sucursal;

    @Column(name = "tenant_id", nullable = false)
    private String tenantId;

    @Column(name = "stock_actual", nullable = false)
    private Integer stockActual = 0;
}
