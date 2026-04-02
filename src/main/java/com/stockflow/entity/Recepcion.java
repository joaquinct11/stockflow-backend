package com.stockflow.entity;

import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;

@Entity
@Table(name = "recepcion")
@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class Recepcion {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "tenant_id", nullable = false)
    private String tenantId;

    @ManyToOne
    @JoinColumn(name = "oc_id")
    private OrdenCompra ordenCompra;

    @ManyToOne
    @JoinColumn(name = "proveedor_id", nullable = false)
    private Proveedor proveedor;

    @ManyToOne
    @JoinColumn(name = "usuario_receptor_id", nullable = false)
    private Usuario usuarioReceptor;

    @Column(nullable = false, length = 20)
    private String estado = "BORRADOR";

    // Comprobante del proveedor (factura/boleta física)
    @Column(name = "tipo_comprobante", length = 10)
    private String tipoComprobante;

    @Column(length = 20)
    private String serie;

    @Column(length = 30)
    private String numero;

    @Column(name = "url_adjunto", length = 500)
    private String urlAdjunto;

    @Column(columnDefinition = "TEXT")
    private String observaciones;

    @Column(name = "created_at", updatable = false)
    private LocalDateTime createdAt;

    @Column(name = "fecha_confirmacion")
    private LocalDateTime fechaConfirmacion;

    @OneToMany(mappedBy = "recepcion", cascade = CascadeType.ALL, orphanRemoval = true)
    @Builder.Default
    private List<RecepcionDetalle> detalles = new ArrayList<>();

    @PrePersist
    protected void prePersist() {
        if (createdAt == null) createdAt = LocalDateTime.now();
    }
}
