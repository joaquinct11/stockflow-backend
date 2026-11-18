package com.stockflow.entity.gre;

import jakarta.persistence.*;
import lombok.*;

import java.time.OffsetDateTime;

@Entity
@Table(name = "guia_remision_eventos")
@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class GuiaRemisionEvento {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "guia_remision_id", nullable = false)
    private GuiaRemision guiaRemision;

    @Column(name = "tenant_id", nullable = false)
    private String tenantId;

    @Enumerated(EnumType.STRING)
    @Column(name = "tipo_evento", nullable = false, length = 50)
    private TipoEventoGRE tipoEvento;

    @Column(columnDefinition = "TEXT")
    private String descripcion;

    @Column(name = "datos_adicionales", columnDefinition = "TEXT")
    private String datosAdicionales;

    @Column(name = "usuario")
    private String usuario;

    @Column(name = "created_at", updatable = false)
    @Builder.Default
    private OffsetDateTime createdAt = OffsetDateTime.now();
}
