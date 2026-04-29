package com.stockflow.entity;

import jakarta.persistence.*;
import lombok.*;

import java.time.LocalDateTime;

@Entity
@Table(name = "webhook_logs",
        indexes = @Index(name = "idx_webhook_id_tipo", columnList = "webhook_id,tipo"))
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class WebhookLog {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "webhook_id", nullable = false, length = 100)
    private String webhookId;

    @Column(nullable = false, length = 50)
    private String tipo; // preapproval, payment, etc.

    @Column(nullable = false, length = 20)
    private String estado; // PROCESANDO, PROCESADO, ERROR, IGNORADO

    @Column(name = "fecha_procesamiento")
    private LocalDateTime fechaProcesamiento;

    @Column(columnDefinition = "TEXT")
    private String detalles; // Para guardar el payload completo si es necesario
}