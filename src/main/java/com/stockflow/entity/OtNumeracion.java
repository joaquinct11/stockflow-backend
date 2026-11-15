package com.stockflow.entity;

import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Entity
@Table(name = "ot_numeracion")
@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class OtNumeracion {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "tenant_id", nullable = false)
    private String tenantId;

    @Column(nullable = false)
    private Short anio;

    @Column(nullable = false)
    private Integer ultimo;
}
