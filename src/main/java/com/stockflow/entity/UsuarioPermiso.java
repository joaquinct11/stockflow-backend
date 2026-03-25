package com.stockflow.entity;

import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

@Entity
@Table(name = "usuario_permisos",
        uniqueConstraints = @UniqueConstraint(
                name = "uq_usuario_permiso_tenant",
                columnNames = {"usuario_id", "permiso_id", "tenant_id"}
        ))
@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class UsuarioPermiso {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "usuario_id", nullable = false)
    private Usuario usuario;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "permiso_id", nullable = false)
    private Permiso permiso;

    @Column(name = "tenant_id", nullable = false, length = 100)
    private String tenantId;

    @Column(name = "created_at")
    private LocalDateTime createdAt = LocalDateTime.now();
}
