package com.stockflow.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;

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
    private BigDecimal subtotal;  // base imponible (total / 1.18)
    private BigDecimal igv;       // IGV incluido (total - subtotal)
    private BigDecimal total;     // total con IGV incluido
    private String receptorDocTipo;
    private String receptorDocNumero;
    private String receptorNombre;
    private String receptorDireccion;
    private String sunatEstado;
    /** Mensaje descriptivo de SUNAT/Nubefact tras el envío. */
    private String sunatMensaje;
    private String sunatTicket;
    private String hash;
    private String qr;
    private String pdfUrl;
    private String xmlUrl;
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;

    // Detalle de productos de la venta
    private List<ItemComprobanteDTO> items;

    @Data
    @NoArgsConstructor
    @AllArgsConstructor
    @Builder
    public static class ItemComprobanteDTO {
        private Long productoId;
        private String productoNombre;
        private String codigoBarras;
        private Integer cantidad;
        private BigDecimal precioUnitario;  // precio con IGV incluido
        private BigDecimal subtotal;        // cantidad × precioUnitario
    }
}
