package com.stockflow.dto.gre.lucode;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonInclude;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * Payload de respuesta de Lucode para GRE.
 * Según docs: { hash, xml (URL), cdr (URL), estado }
 *
 * @JsonIgnoreProperties es necesario en cada clase de la cadena de deserialización, no solo en
 * LucodeGREResponse. Sin esta anotación, Jackson lanza UnrecognizedPropertyException cuando Lucode
 * agrega campos nuevos como "pdf" (con sub-objeto "a4"), convirtiendo una aceptación real en HTTP 400.
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@JsonInclude(JsonInclude.Include.NON_NULL)
@JsonIgnoreProperties(ignoreUnknown = true)
public class LucodeGREPayload {
    private String hash;
    private String xml;
    private String cdr;
    private String estado;
    private PdfLinks pdf;

    @Data
    @NoArgsConstructor
    @AllArgsConstructor
    @JsonIgnoreProperties(ignoreUnknown = true)
    public static class PdfLinks {
        private String a4;
    }
}
