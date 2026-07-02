package com.stockflow.entity;

import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import java.math.BigDecimal;
import java.time.LocalDateTime;

@Entity
@Table(name = "productos")
@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class Producto {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false)
    private String nombre;

    @Column(unique = true, length = 100)
    private String codigoBarras;

    @Column(nullable = false)
    private Integer stockActual = 0;

    @Column(nullable = false)
    private Integer stockMinimo = 10;

    @Column(nullable = false)
    private Integer stockMaximo = 500;

    @Column(nullable = false, precision = 10, scale = 2)
    private BigDecimal costoUnitario;

    @Column(nullable = false, precision = 10, scale = 2)
    private BigDecimal precioVenta;

    @ManyToOne
    @JoinColumn(name = "categoria_id")
    private Categoria categoriaRef;

    @ManyToOne
    @JoinColumn(name = "unidad_medida_id", nullable = false)
    private UnidadMedida unidadMedida;

    @Column(nullable = false)
    private Boolean activo = true;

    @Column(name = "imagen_url", columnDefinition = "TEXT")
    private String imagenUrl;

    @Column(columnDefinition = "TEXT")
    private String componentes;

    @Column(name = "es_generico", nullable = false)
    private Boolean esGenerico = false;

    @Column(name = "unidades_por_caja")
    private Integer unidadesPorCaja;

    @Column(name = "talla", length = 50)
    private String talla;

    @Column(name = "color", length = 100)
    private String color;

    @Column(name = "tenant_id")
    private String tenantId;

    /**
     * PRODUCTO (default) = tiene stock, se descuenta al vender.
     * SERVICIO = sin stock, no genera movimiento de inventario.
     */
    @Column(nullable = false, length = 20)
    private String tipo = "PRODUCTO";

    @Column(name = "created_at", updatable = false)
    private LocalDateTime createdAt;

}