package com.stockflow.entity;

import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import org.hibernate.annotations.UpdateTimestamp;

import java.math.BigDecimal;
import java.time.LocalDateTime;

@Entity
@Table(
    name = "comprobantes",
    uniqueConstraints = @UniqueConstraint(
        name = "uq_comprobante_numero",
        columnNames = {"tenant_id", "tipo", "serie", "correlativo"}
    )
)
@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class Comprobante {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "tenant_id", nullable = false)
    private String tenantId;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "venta_id", nullable = false)
    private Venta venta;

    @Column(nullable = false, length = 20)
    private String tipo;  // BOLETA | FACTURA

    @Column(nullable = false, length = 10)
    private String serie;  // e.g. B001

    @Column(nullable = false)
    private Integer correlativo;

    @Column(nullable = false, length = 30)
    private String numero;  // e.g. B001-00000001

    @Column(name = "fecha_emision", nullable = false)
    private LocalDateTime fechaEmision;

    @Column(nullable = false, length = 20)
    private String estado = "EMITIDO";  // EMITIDO | ANULADO | ERROR

    @Column(nullable = false, precision = 10, scale = 2)
    private BigDecimal subtotal;

    @Column(nullable = false, precision = 10, scale = 2)
    private BigDecimal igv;

    @Column(nullable = false, precision = 10, scale = 2)
    private BigDecimal total;

    // Receptor
    @Column(name = "receptor_doc_tipo", length = 10)
    private String receptorDocTipo;  // DNI | RUC

    @Column(name = "receptor_doc_numero", length = 20)
    private String receptorDocNumero;

    @Column(name = "receptor_nombre", length = 200)
    private String receptorNombre;

    @Column(name = "receptor_direccion", length = 300)
    private String receptorDireccion;

    // SUNAT placeholders
    @Column(name = "sunat_estado", length = 20)
    private String sunatEstado;  // ACEPTADO | RECHAZADO | PENDIENTE

    @Column(name = "sunat_ticket", length = 100)
    private String sunatTicket;

    @Column(length = 200)
    private String hash;

    @Column(columnDefinition = "TEXT")
    private String qr;

    @Column(name = "pdf_url", length = 500)
    private String pdfUrl;

    @Column(name = "xml_url", length = 500)
    private String xmlUrl;

    @Column(name = "created_at", updatable = false)
    private LocalDateTime createdAt;

    @UpdateTimestamp
    @Column(name = "updated_at")
    private LocalDateTime updatedAt;
}
