package com.stockflow.entity;

import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;
import java.time.LocalDateTime;

@Entity
@Table(name = "ordenes_trabajo")
@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class OrdenTrabajo {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "tenant_id", nullable = false)
    private String tenantId;

    @Column(name = "numero_ot", nullable = false, length = 50)
    private String numeroOt;

    @Column(nullable = false)
    private Short anio;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "equipo_cliente_id", nullable = false)
    private EquipoCliente equipo;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "cliente_id", nullable = false)
    private Cliente cliente;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "sucursal_id", nullable = false)
    private Sucursal sucursal;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "tecnico_id")
    private Usuario tecnico;

    @Column(nullable = false, length = 30)
    private String estado;

    @Column(nullable = false, length = 20)
    private String prioridad;

    @Column(name = "descripcion_problema", nullable = false, columnDefinition = "TEXT")
    private String descripcionProblema;

    @Column(columnDefinition = "TEXT")
    private String diagnostico;

    @Column(name = "trabajo_realizado", columnDefinition = "TEXT")
    private String trabajoRealizado;

    @Column(columnDefinition = "TEXT")
    private String observaciones;

    @Column(name = "fecha_cierre_tecnico")
    private LocalDateTime fechaCierreTecnico;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "tecnico_cierre_id")
    private Usuario tecnicoCierre;

    @Column(name = "venta_id")
    private Long ventaId;

    @Column(name = "monto_mano_obra", precision = 12, scale = 2)
    private BigDecimal montoManoObra;

    @Column(name = "total_facturado", precision = 12, scale = 2)
    private BigDecimal totalFacturado;

    // ── Datos de entrega (Fase 2E) ────────────────────────────────────────────
    @Column(name = "recibido_por", length = 200)
    private String recibidoPor;

    @Column(name = "documento_receptor", length = 50)
    private String documentoReceptor;

    @Column(name = "fecha_entrega")
    private LocalDateTime fechaEntrega;

    @Column(name = "entregado_por")
    private Long entregadoPor;

    @Column(name = "observaciones_entrega", columnDefinition = "TEXT")
    private String observacionesEntrega;

    @Column(name = "conformidad_cliente")
    private Boolean conformidadCliente;

    @Column(name = "created_at", nullable = false)
    private LocalDateTime createdAt;

    @Column(name = "updated_at")
    private LocalDateTime updatedAt;

    @Column(name = "created_by")
    private Long createdBy;

    @Column(name = "updated_by")
    private Long updatedBy;
}
