package com.stockflow.entity;

import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;

@Entity
@Table(name = "gastos")
@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class Gasto {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    /** Descripción libre del gasto: "Pago de luz enero", "Sueldo vendedor", etc. */
    @Column(nullable = false)
    private String concepto;

    /**
     * Categoría del gasto.
     * ALQUILER, SERVICIOS, SUELDOS, MANTENIMIENTO, PUBLICIDAD,
     * TRANSPORTE, IMPUESTOS, COMPRAS_INTERNAS, OTROS
     */
    @Column(nullable = false, length = 30)
    private String categoria;

    @Column(nullable = false, precision = 12, scale = 2)
    private BigDecimal monto;

    /** Fecha en la que se realizó / se reconoce el gasto (no necesariamente hoy). */
    @Column(name = "fecha_gasto", nullable = false)
    private LocalDate fechaGasto;

    /**
     * Método de pago usado: EFECTIVO, TRANSFERENCIA, TARJETA, YAPE, PLIN, CHEQUE, OTRO.
     */
    @Column(name = "metodo_pago", length = 20)
    private String metodoPago;

    /** Número de boleta, factura, RHE u otro comprobante del proveedor del servicio. */
    @Column(name = "numero_comprobante", length = 50)
    private String numeroComprobante;

    /** Notas internas, observaciones. */
    @Column(columnDefinition = "TEXT")
    private String notas;

    @Column(nullable = false)
    private Boolean activo = true;

    @Column(name = "tenant_id", nullable = false)
    private String tenantId;

    /** Usuario que registró el gasto (nombre, no FK, para simplicidad). */
    @Column(name = "registrado_por", length = 150)
    private String registradoPor;

    @Column(name = "created_at")
    private LocalDateTime createdAt = LocalDateTime.now();

    @Column(name = "deleted_at")
    private LocalDateTime deletedAt;

    @Column(name = "sucursal_id")
    private Long sucursalId;
}
