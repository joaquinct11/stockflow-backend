package com.stockflow.entity;

import jakarta.persistence.*;
import lombok.*;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;

@Entity
@Table(name = "comisiones")
@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class Comision {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "tenant_id", nullable = false)
    private String tenantId;

    @Column(nullable = false)
    private String concepto;

    @Column(nullable = false)
    private String pagador;

    @Column(nullable = false, precision = 12, scale = 2)
    private BigDecimal monto;

    @Column(nullable = false)
    private LocalDate fecha;

    @Column(name = "metodo_pago", length = 20)
    private String metodoPago;

    @Column(name = "numero_comprobante", length = 100)
    private String numeroComprobante;

    @Column(columnDefinition = "TEXT")
    private String notas;

    @Column(name = "registrado_por", length = 150)
    private String registradoPor;

    @Column(name = "created_at", nullable = false)
    @Builder.Default
    private LocalDateTime createdAt = LocalDateTime.now();

    @Column(name = "deleted_at")
    private LocalDateTime deletedAt;

    @Column(name = "sucursal_id")
    private Long sucursalId;
}
