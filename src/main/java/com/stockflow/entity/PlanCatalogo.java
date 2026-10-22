package com.stockflow.entity;

import jakarta.persistence.*;
import lombok.*;

import java.math.BigDecimal;
import java.time.LocalDateTime;

@Entity
@Table(name = "plan_catalogo")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class PlanCatalogo {

    @Id
    @Column(length = 50)
    private String codigo;

    @Column(nullable = false, length = 50)
    private String segmento;

    @Column(name = "rubro_operativo", nullable = false, length = 50)
    private String rubroOperativo;

    @Column(nullable = false, length = 10)
    private String nivel;

    @Column(name = "precio_mensual", nullable = false, precision = 10, scale = 2)
    private BigDecimal precioMensual;

    @Column(name = "max_usuarios", nullable = false)
    private int maxUsuarios;

    @Column(name = "max_sucursales", nullable = false)
    private int maxSucursales;

    @Column(name = "max_productos", nullable = false)
    private int maxProductos;

    @Column(name = "label_visible", length = 100)
    private String labelVisible;

    @Column(name = "culqi_plan_id", length = 100)
    private String culqiPlanId;

    @Column(name = "culqi_setup_at")
    private LocalDateTime culqiSetupAt;

    @Column(nullable = false)
    private boolean activo;

    @Column(name = "created_at", nullable = false)
    private LocalDateTime createdAt;

    @PrePersist
    public void prePersist() {
        if (createdAt == null) createdAt = LocalDateTime.now();
    }

    public boolean isBasico() {
        return "BASICO".equalsIgnoreCase(nivel);
    }

    public boolean isPro() {
        return "PRO".equalsIgnoreCase(nivel);
    }
}
