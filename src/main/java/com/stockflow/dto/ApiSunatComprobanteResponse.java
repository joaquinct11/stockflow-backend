package com.stockflow.dto;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@NoArgsConstructor
@JsonIgnoreProperties(ignoreUnknown = true)
public class ApiSunatComprobanteResponse {

    @JsonProperty("aceptada_por_sunat")
    private Boolean aceptadaPorSunat;

    @JsonProperty("sunat_codigo_respuesta")
    private String sunatCodigoRespuesta;

    @JsonProperty("sunat_description")
    private String sunatDescription;

    @JsonProperty("enlace_del_pdf")
    private String enlaceDelPdf;

    @JsonProperty("enlace_del_xml")
    private String enlaceDelXml;

    @JsonProperty("cadena_para_codigo_qr")
    private String cadenaParaCodigoQr;

    @JsonProperty("numero_ticket")
    private String numeroTicket;

    /** true si SUNAT aceptó el comprobante */
    public boolean fueAceptado() {
        return Boolean.TRUE.equals(aceptadaPorSunat) || "0".equals(sunatCodigoRespuesta);
    }

    /** true si ApiSunat lo registró aunque SUNAT aún no respondió */
    public boolean fueRegistrado() {
        return enlaceDelPdf != null || enlaceDelXml != null || numeroTicket != null;
    }

    public String resumen() {
        return sunatDescription != null ? sunatDescription : "Procesado por ApiSunat";
    }
}
