package com.stockflow.entity.gre;

import com.stockflow.entity.Producto;
import jakarta.persistence.*;
import lombok.*;

import java.math.BigDecimal;

@Entity
@Table(name = "guia_remision_items")
@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class GuiaRemisionItem {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "guia_remision_id", nullable = false)
    private GuiaRemision guiaRemision;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "producto_id", nullable = false)
    private Producto producto;

    @Column(nullable = false)
    private String descripcion;

    @Column(nullable = false, precision = 12, scale = 3)
    private BigDecimal cantidad;

    @Column(name = "unidad_medida", nullable = false, length = 10)
    @Builder.Default
    private String unidadMedida = "NIU";

    @Column(name = "codigo_producto", length = 100)
    private String codigoProducto;

    @Column(name = "tenant_id", nullable = false)
    private String tenantId;

    /** ID de la presentación farmacéutica seleccionada (nullable). Usado para recalcular cantidadBase. */
    @Column(name = "presentacion_id")
    private Long presentacionId;

    /** F-23: variante seleccionada (talla/color). Nullable para compatibilidad con GRE históricas. */
    @Column(name = "variante_id")
    private Long varianteId;
}
