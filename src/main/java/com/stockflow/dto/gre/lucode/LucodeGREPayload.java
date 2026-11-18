package com.stockflow.dto.gre.lucode;

import com.fasterxml.jackson.annotation.JsonInclude;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;

/**
 * Payload de respuesta de Lucode para GRE.
 * Según docs: { hash, xml (URL), cdr (URL) }
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@JsonInclude(JsonInclude.Include.NON_NULL)
public class LucodeGREPayload {
    private String hash;
    private String xml;
    private String cdr;
    private String estado;
}
