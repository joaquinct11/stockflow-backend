package com.stockflow.entity;

import jakarta.persistence.*;
import lombok.*;

import java.math.BigDecimal;

/**
 * Representa una unidad de venta adicional para un producto farmacéutico.
 * Ej: una caja de Paracetamol 500mg puede venderse como CAJA (S/8.00),
 * BLISTER (S/2.50) o TABLETA (S/0.30).
 */
@Entity
@Table(name = "producto_presentaciones",
       uniqueConstraints = @UniqueConstraint(
               name = "uq_presentacion_producto_unidad",
               columnNames = {"producto_id", "unidad_medida_id"}
       ))
@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class ProductoPresentacion {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "producto_id", nullable = false)
    private Producto producto;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "unidad_medida_id", nullable = false)
    private UnidadMedida unidadMedida;

    /** Precio de venta para esta unidad. */
    @Column(nullable = false, precision = 10, scale = 2)
    private BigDecimal precioVenta;

    /**
     * Cuántas unidades base contiene esta presentación.
     * Ej: CAJA = 20 tabletas → factor = 20; BLISTER = 10 → factor = 10; TABLETA = 1.
     * Sirve para descontar stock correctamente al vender.
     */
    @Builder.Default
    @Column(nullable = false)
    private Integer factor = 1;

    /** Si true, es la presentación por defecto del producto. */
    @Builder.Default
    @Column(name = "es_principal", nullable = false)
    private Boolean esPrincipal = false;

    @Column(name = "tenant_id")
    private String tenantId;
}
