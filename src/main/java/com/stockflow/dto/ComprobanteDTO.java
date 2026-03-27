package com.stockflow.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;
import java.time.LocalDateTime;

@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class ComprobanteDTO {

    private Long id;
    private String tenantId;
    private Long ventaId;
    private String tipo;          // BOLETA | FACTURA
    private String serie;
    private Integer correlativo;
    private String numero;        // e.g. B001-00000001
    private LocalDateTime fechaEmision;
    private String estado;        // EMITIDO | ANULADO | ERROR
    private BigDecimal subtotal;
    private BigDecimal igv;
    private BigDecimal total;
    private String receptorDocTipo;
    private String receptorDocNumero;
    private String receptorNombre;
    private String receptorDireccion;
    private String sunatEstado;
    private String sunatTicket;
    private String hash;
    private String qr;
    private String pdfUrl;
    private String xmlUrl;
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;
}
