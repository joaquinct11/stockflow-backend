package com.stockflow.entity;

import jakarta.persistence.*;
import lombok.*;

import java.time.LocalDateTime;

@Entity
@Table(name = "oppf_exportaciones")
@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class OppfExportacion {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "tenant_id", nullable = false)
    private String tenantId;

    @Column(name = "ruc", nullable = false, length = 20)
    private String ruc;

    @Column(name = "cod_establecimiento", nullable = false, length = 50)
    private String codEstablecimiento;

    @Column(name = "mes", nullable = false, length = 2)
    private String mes;

    @Column(name = "ano", nullable = false, length = 4)
    private String ano;

    @Column(name = "tipo", nullable = false, length = 50)
    private String tipo;

    @Column(name = "total_productos", nullable = false)
    private Integer totalProductos;

    @Column(name = "nombre_archivo", nullable = false, length = 200)
    private String nombreArchivo;

    @Column(name = "created_at", nullable = false, updatable = false)
    private LocalDateTime createdAt;

    @PrePersist
    void prePersist() {
        if (createdAt == null) createdAt = LocalDateTime.now();
    }
}
