package com.stockflow.entity;

import jakarta.persistence.*;
import lombok.*;
import java.time.LocalDateTime;

@Entity
@Table(name = "notificaciones")
@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class Notificacion {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "tenant_id", nullable = false)
    private String tenantId;

    /** Notificación dirigida a un usuario específico (ej: el vendedor de una devolución). */
    @Column(name = "usuario_id")
    private Long usuarioId;

    /** Notificación dirigida a todos los usuarios de un rol en el tenant. */
    @Column(name = "rol_destino")
    private String rolDestino;

    /** Tipo semántico: STOCK_BAJO, PRODUCTO_VENCIDO, PRODUCTO_POR_VENCER,
     *  SUSCRIPCION_POR_VENCER, VENTA_ANULADA, DEVOLUCION_REGISTRADA,
     *  DESCUADRE_CAJA, RECEPCION_CONFIRMADA, OC_ENVIADA, USUARIO_ACTIVADO */
    @Column(nullable = false)
    private String tipo;

    @Column(nullable = false)
    private String titulo;

    @Column(columnDefinition = "TEXT", nullable = false)
    private String mensaje;

    @Column(nullable = false)
    private boolean leida = false;

    /** ID del recurso relacionado (producto, venta, OC, etc.). */
    @Column(name = "referencia_id")
    private Long referenciaId;

    /** Tipo del recurso: PRODUCTO, VENTA, OC, CAJA, SUSCRIPCION, USUARIO. */
    @Column(name = "referencia_tipo")
    private String referenciaTipo;

    @Column(name = "created_at", nullable = false)
    @Builder.Default
    private LocalDateTime createdAt = LocalDateTime.now();
}
