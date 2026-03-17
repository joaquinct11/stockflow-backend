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
@Table(name = "unidad_medida")
@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class UnidadMedida {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(length = 100)
    private String nombre;

    @Column(length = 50)
    private String abreviatura;

    @Column(name = "tenant_id")
    private String tenantId;

    @Column(name = "es_default", nullable = false)
    private Boolean esDefault = true;

    @Column(name = "created_at", updatable = false)
    private LocalDateTime createdAt;

}
