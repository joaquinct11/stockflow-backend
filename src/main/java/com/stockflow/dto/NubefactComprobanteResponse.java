package com.stockflow.dto;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * Respuesta de la API de Nubefact tras emitir un comprobante.
 * Se ignoran campos desconocidos para tolerar cambios futuros en la API.
 */
@Data
@NoArgsConstructor
@JsonIgnoreProperties(ignoreUnknown = true)
public class NubefactComprobanteResponse {

    /** true si SUNAT aceptó el comprobante. */
    @JsonProperty("aceptada_por_sunat")
    private Boolean aceptadaPorSunat;

    /** Código de respuesta SUNAT ("0" = OK, otros = error). */
    @JsonProperty("sunat_responsecode")
    private String codigoSunat;

    /** Descripción legible de SUNAT ("La Factura FFF1-1 ha sido ACEPTADA..."). */
    @JsonProperty("sunat_description")
    private String mensajeSunat;

    /** Número de ticket (boletas asíncronas). */
    @JsonProperty("numero_ticket")
    private String numeroTicket;

    /** URL del PDF generado por Nubefact. */
    @JsonProperty("enlace_del_pdf")
    private String enlaceDelPdf;

    /** URL del XML UBL 2.1 firmado. */
    @JsonProperty("enlace_del_xml")
    private String enlaceDelXml;

    /** URL del CDR (Constancia de Recepción de SUNAT). */
    @JsonProperty("enlace_del_cdr")
    private String enlaceDelCdr;

    /** Cadena para generar el código QR del comprobante. */
    @JsonProperty("cadena_para_codigo_qr")
    private String cadenaParaCodigoQr;

    /** Código/número del comprobante en Nubefact (ej: "B001-1"). */
    @JsonProperty("codigo")
    private String codigo;

    /** Mensaje general de Nubefact. */
    @JsonProperty("mensaje")
    private String mensaje;

    /**
     * Flag interno (no viene del JSON): indica que Nubefact devolvió código 23
     * "documento ya existe", es decir el comprobante ya fue registrado previamente.
     */
    private boolean yaExistia = false;

    // ── Helpers ───────────────────────────────────────────────────────────────

    /**
     * Devuelve true si SUNAT ya confirmó la aceptación del comprobante.
     */
    public boolean fueAceptado() {
        if (Boolean.TRUE.equals(aceptadaPorSunat)) return true;
        return "0".equals(codigoSunat);
    }

    /**
     * Devuelve true si Nubefact ya tiene el documento (registrado o en proceso),
     * aunque SUNAT todavía no haya respondido.
     * Casos: respuesta con PDF/XML links, o error 23 (ya existía).
     */
    public boolean fueRegistrado() {
        return yaExistia || enlaceDelPdf != null || enlaceDelXml != null || numeroTicket != null;
    }

    /** Texto legible del resultado para logear/mostrar. */
    public String resumen() {
        if (yaExistia) return "Documento ya registrado en Nubefact (código 23)";
        return mensajeSunat != null ? mensajeSunat : (mensaje != null ? mensaje : "Sin mensaje");
    }
}
