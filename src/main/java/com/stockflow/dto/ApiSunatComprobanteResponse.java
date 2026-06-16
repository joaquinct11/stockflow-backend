package com.stockflow.dto;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * Respuesta de ApiSunat (lucode.pe) al emitir un comprobante.
 *
 * Formato real:
 * {
 *   "success": true,
 *   "message": "El documento fue enviado correctamente y aceptado por SUNAT.",
 *   "payload": {
 *     "estado": "ACEPTADO",
 *     "hash": "...",
 *     "xml":  "https://sandbox.apisunat.pe/xml/...",
 *     "cdr":  "https://sandbox.apisunat.pe/xml/R-...",
 *     "pdf":  { "ticket": "https://...", "a4": "https://..." }
 *   }
 * }
 */
@Data
@NoArgsConstructor
@JsonIgnoreProperties(ignoreUnknown = true)
public class ApiSunatComprobanteResponse {

    @JsonProperty("success")
    private Boolean success;

    @JsonProperty("message")
    private String message;

    @JsonProperty("payload")
    private Payload payload;

    @Data
    @NoArgsConstructor
    @JsonIgnoreProperties(ignoreUnknown = true)
    public static class Payload {

        @JsonProperty("estado")
        private String estado;

        @JsonProperty("hash")
        private String hash;

        @JsonProperty("xml")
        private String xml;

        @JsonProperty("cdr")
        private String cdr;

        @JsonProperty("pdf")
        private Pdf pdf;

        @Data
        @NoArgsConstructor
        @JsonIgnoreProperties(ignoreUnknown = true)
        public static class Pdf {
            @JsonProperty("ticket")
            private String ticket;

            @JsonProperty("a4")
            private String a4;
        }
    }

    /** true si SUNAT aceptó el comprobante */
    public boolean fueAceptado() {
        return Boolean.TRUE.equals(success)
                && payload != null
                && "ACEPTADO".equalsIgnoreCase(payload.getEstado());
    }

    /** true si ApiSunat lo procesó aunque el estado no sea ACEPTADO aún */
    public boolean fueRegistrado() {
        return Boolean.TRUE.equals(success) && payload != null;
    }

    public String getPdfUrl() {
        if (payload == null || payload.getPdf() == null) return null;
        return payload.getPdf().getA4();
    }

    public String getPdfTicketUrl() {
        if (payload == null || payload.getPdf() == null) return null;
        return payload.getPdf().getTicket();
    }

    public String getXmlUrl() {
        return payload != null ? payload.getXml() : null;
    }

    public String resumen() {
        if (message != null && !message.isBlank()) return message;
        if (payload != null && payload.getEstado() != null) return payload.getEstado();
        return "Procesado por ApiSunat";
    }
}
