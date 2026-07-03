package com.stockflow.entity;

import jakarta.persistence.*;
import lombok.*;

@Entity
@Table(name = "producto_variante_stock_sucursal")
@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class ProductoVarianteStockSucursal {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "variante_id", nullable = false)
    private Long varianteId;

    @Column(name = "sucursal_id", nullable = false)
    private Long sucursalId;

    @Column(name = "tenant_id", nullable = false)
    private String tenantId;

    @Column(name = "stock_actual", nullable = false)
    private Integer stockActual = 0;

    @Column(name = "stock_minimo", nullable = false)
    private Integer stockMinimo = 0;
}
