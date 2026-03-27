package com.stockflow.entity;

import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Entity
@Table(
    name = "comprobante_series",
    uniqueConstraints = @UniqueConstraint(
        name = "uq_comprobante_series",
        columnNames = {"tenant_id", "tipo", "serie"}
    )
)
@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class ComprobanteSerie {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "tenant_id", nullable = false)
    private String tenantId;

    @Column(nullable = false, length = 20)
    private String tipo;  // BOLETA | FACTURA

    @Column(nullable = false, length = 10)
    private String serie;  // e.g. B001, F001

    @Column(name = "ultimo_correlativo", nullable = false)
    private Integer ultimoCorrelativo = 0;
}
