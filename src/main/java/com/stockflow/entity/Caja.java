package com.stockflow.entity;

import jakarta.persistence.*;
import lombok.*;
import java.math.BigDecimal;
import java.time.LocalDateTime;

@Entity
@Table(name = "cajas")
@Data @NoArgsConstructor @AllArgsConstructor @Builder
public class Caja {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "tenant_id", nullable = false)
    private String tenantId;

    @Column(name = "usuario_id", nullable = false)
    private Long usuarioId;

    @Column(name = "usuario_nombre", nullable = false, length = 200)
    private String usuarioNombre;

    @Column(name = "monto_apertura", nullable = false, precision = 12, scale = 2)
    private BigDecimal montoApertura;

    @Column(name = "total_efectivo", precision = 12, scale = 2)
    private BigDecimal totalEfectivo;

    @Column(name = "total_tarjeta", precision = 12, scale = 2)
    private BigDecimal totalTarjeta;

    @Column(name = "total_yape_plin", precision = 12, scale = 2)
    private BigDecimal totalYapePlin;

    @Column(name = "total_ingresos", precision = 12, scale = 2)
    private BigDecimal totalIngresos;

    @Column(name = "cantidad_ventas")
    private Integer cantidadVentas;

    @Column(name = "monto_contado", precision = 12, scale = 2)
    private BigDecimal montoContado;

    @Column(precision = 12, scale = 2)
    private BigDecimal diferencia;

    @Column(nullable = false, length = 20)
    private String estado; // ABIERTA | CERRADA

    @Column(columnDefinition = "TEXT")
    private String observaciones;

    @Column(name = "fecha_apertura", nullable = false)
    private LocalDateTime fechaApertura;

    @Column(name = "fecha_cierre")
    private LocalDateTime fechaCierre;
}
