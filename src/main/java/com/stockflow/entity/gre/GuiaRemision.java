package com.stockflow.entity.gre;

import com.stockflow.entity.Sucursal;
import com.stockflow.entity.Venta;
import jakarta.persistence.*;
import lombok.*;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.util.ArrayList;
import java.util.List;

@Entity
@Table(name = "guias_remision")
@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class GuiaRemision {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "tenant_id", nullable = false)
    private String tenantId;

    @Column(nullable = false, length = 10)
    private String serie;

    @Column(nullable = false)
    private Integer correlativo;

    @Enumerated(EnumType.STRING)
    @Column(name = "tipo_traslado", nullable = false, length = 20)
    private TipoTrasladoGRE tipoTraslado;

    @Column(name = "motivo_traslado_codigo", nullable = false, length = 10)
    private String motivoTrasladoCodigo;

    @Column(name = "motivo_traslado_descripcion")
    private String motivoTrasladoDescripcion;

    @Enumerated(EnumType.STRING)
    @Column(name = "modalidad_transporte", nullable = false, length = 10)
    private ModalidadTransporteGRE modalidadTransporte;

    @Column(name = "fecha_traslado", nullable = false)
    private LocalDate fechaTraslado;

    @Column(name = "peso_bruto_total", precision = 12, scale = 3)
    private BigDecimal pesoBrutoTotal;

    @Column(name = "unidad_medida_peso", length = 10)
    @Builder.Default
    private String unidadMedidaPeso = "KGM";

    @Column(name = "numero_bultos")
    private Integer numeroBultos;

    @Column(name = "observaciones", length = 500)
    private String observaciones;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "sucursal_origen_id")
    private Sucursal sucursalOrigen;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "sucursal_destino_id")
    private Sucursal sucursalDestino;

    @Column(name = "destinatario_tipo_doc", length = 5)
    private String destinatarioTipoDoc;

    @Column(name = "destinatario_num_doc", length = 20)
    private String destinatarioNumDoc;

    @Column(name = "destinatario_razon_social")
    private String destinatarioRazonSocial;

    @Column(name = "destinatario_direccion")
    private String destinatarioDireccion;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "venta_id")
    private Venta venta;

    @Enumerated(EnumType.STRING)
    @Column(name = "estado_tributario", nullable = false, length = 20)
    @Builder.Default
    private EstadoTributarioGRE estadoTributario = EstadoTributarioGRE.BORRADOR;

    @Enumerated(EnumType.STRING)
    @Column(name = "estado_logistico", nullable = false, length = 20)
    @Builder.Default
    private EstadoLogisticoGRE estadoLogistico = EstadoLogisticoGRE.PENDIENTE;

    @Column(name = "lucode_id")
    private String lucodeId;

    @Column(name = "lucode_hash")
    private String lucodeHash;

    @Column(name = "xml_url", length = 1000)
    private String xmlUrl;

    @Column(name = "cdr_url", length = 1000)
    private String cdrUrl;

    @Column(name = "pdf_url", length = 1000)
    private String pdfUrl;

    @Column(name = "lucode_respuesta_raw", columnDefinition = "TEXT")
    private String lucodeRespuestaRaw;

    @Column(name = "lucode_error_raw", columnDefinition = "TEXT")
    private String lucodeErrorRaw;

    @Column(name = "idempotency_key", unique = true)
    private String idempotencyKey;

    @Column(name = "created_by")
    private String createdBy;

    @Column(name = "created_at", updatable = false)
    @Builder.Default
    private OffsetDateTime createdAt = OffsetDateTime.now();

    @Column(name = "updated_at")
    @Builder.Default
    private OffsetDateTime updatedAt = OffsetDateTime.now();

    @OneToMany(mappedBy = "guiaRemision", cascade = CascadeType.ALL, orphanRemoval = true, fetch = FetchType.LAZY)
    @Builder.Default
    private List<GuiaRemisionItem> items = new ArrayList<>();

    @OneToOne(mappedBy = "guiaRemision", cascade = CascadeType.ALL, orphanRemoval = true, fetch = FetchType.LAZY)
    private GuiaRemisionTransporte transporte;

    @OneToMany(mappedBy = "guiaRemision", cascade = CascadeType.ALL, fetch = FetchType.LAZY)
    @Builder.Default
    private List<GuiaRemisionEvento> eventos = new ArrayList<>();

    @PreUpdate
    protected void onUpdate() {
        updatedAt = OffsetDateTime.now();
    }

    public String getNumeroCompleto() {
        return serie + "-" + correlativo;
    }
}
