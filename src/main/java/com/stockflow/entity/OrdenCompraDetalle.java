package com.stockflow.entity;

import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;

@Entity
@Table(name = "orden_compra_detalle")
@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class OrdenCompraDetalle {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne
    @JoinColumn(name = "orden_compra_id", nullable = false)
    private OrdenCompra ordenCompra;

    @ManyToOne
    @JoinColumn(name = "producto_id", nullable = false)
    private Producto producto;

    @Column(name = "cantidad_solicitada", nullable = false)
    private Integer cantidadSolicitada;

    @Column(name = "precio_unitario", precision = 10, scale = 2)
    private BigDecimal precioUnitario;
}
