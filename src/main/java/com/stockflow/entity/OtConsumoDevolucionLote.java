package com.stockflow.entity;

import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

@Entity
@Table(name = "ot_consumo_devolucion_lote")
@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class OtConsumoDevolucionLote {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "devolucion_id", nullable = false)
    private Long devolucionId;

    @Column(name = "stock_lote_id", nullable = false)
    private Long stockLoteId;

    @Column(name = "cantidad", nullable = false)
    private Integer cantidad;

    @Column(name = "created_at", nullable = false, updatable = false)
    private LocalDateTime createdAt;

    @PrePersist
    protected void prePersist() {
        if (createdAt == null) createdAt = LocalDateTime.now();
    }
}
