package com.stockflow.entity;

import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import java.math.BigDecimal;
import java.time.LocalDateTime;

@Entity
@Table(name = "suscripciones")
@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class Suscripcion {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne
    @JoinColumn(name = "usuario_principal_id", nullable = false)
    private Usuario usuarioPrincipal;

    @Column(name = "plan_id", nullable = false, length = 50)
    private String planId;

    @Column(name = "precio_mensual", nullable = false, precision = 10, scale = 2)
    private BigDecimal precioMensual;

    @Column(name = "preapproval_id", length = 255)
    private String preapprovalId;

    @Column(nullable = false, length = 50)
    private String estado = "ACTIVA";

    @Column(name = "fecha_inicio")
    private LocalDateTime fechaInicio = LocalDateTime.now();

    @Column(name = "fecha_proximo_cobro")
    private LocalDateTime fechaProximoCobro;

    @Column(name = "intentos_fallidos")
    private Integer intentosFallidos = 0;

    @Column(name = "fecha_cancelacion")
    private LocalDateTime fechaCancelacion;

    @Column(name = "metodo_pago", length = 50)
    private String metodoPago;

    @Column(name = "ultimos_4_digitos", length = 4)
    private String ultimos4Digitos;

    @Column(name = "tenant_id")
    private String tenantId;

    @Column(name = "deleted_at")
    private LocalDateTime deletedAt;
}