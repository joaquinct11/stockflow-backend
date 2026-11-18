package com.stockflow.dto.gre.lucode;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import lombok.Data;

/**
 * Respuesta de Lucode para POST /api/v3/dispatches.
 * Éxito: { "success": true, "message": "Guía...", "payload": { "hash", "xml", "cdr" } }
 * Error: { "success": false, "message": "...", "payload": { "<field>": ["error"] } }
 */
@Data
@JsonIgnoreProperties(ignoreUnknown = true)
public class LucodeGREResponse {
    private Boolean success;
    private String message;
    private LucodeGREPayload payload;

    public boolean isSuccess() {
        return Boolean.TRUE.equals(success);
    }
}
