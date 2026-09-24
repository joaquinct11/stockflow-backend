package com.stockflow.entity;

import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import java.math.BigDecimal;

@Entity
@Table(name = "detalles_venta")
@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class DetalleVenta {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne
    @JoinColumn(name = "venta_id", nullable = false)
    private Venta venta;

    @ManyToOne
    @JoinColumn(name = "producto_id", nullable = false)
    private Producto producto;

    @Column(nullable = false)
    private Integer cantidad;

    @Column(name = "precio_unitario", nullable = false, precision = 10, scale = 2)
    private BigDecimal precioUnitario;

    @Column(name = "subtotal", nullable = false, precision = 10, scale = 2)
    private BigDecimal subtotal;

    @Column(name = "variante_id")
    private Long varianteId;

    @Column(name = "variante_descripcion")
    private String varianteDescripcion;

    @Column(name = "stock_lote_id")
    private Long stockLoteId;

    /** ID de la presentación usada al vender (null = unidad principal del producto). */
    @Column(name = "presentacion_id")
    private Long presentacionId;

    /**
     * Multiplicador de unidades base: vender 1 CAJA con factor=20 descuenta 20 tabletas.
     * Siempre >= 1. Se persiste para que la anulación reponga correctamente.
     */
    @Builder.Default
    @Column(name = "factor", nullable = false)
    private Integer factor = 1;

    /**
     * JSON con los lotes exactos consumidos al vender: [[loteId1, cantidad1], [loteId2, cantidad2], ...]
     * Persiste el consumo real (incluye desborde FEFO) para restaurar exactamente al anular/devolver.
     */
    @Column(name = "lotes_consumidos_json", columnDefinition = "TEXT")
    private String lotesConsumidosJson;

    @PrePersist
    @PreUpdate
    public void calcularSubtotal() {
        if (this.cantidad != null && this.precioUnitario != null) {
            this.subtotal = this.precioUnitario.multiply(new BigDecimal(this.cantidad));
        }
    }
}