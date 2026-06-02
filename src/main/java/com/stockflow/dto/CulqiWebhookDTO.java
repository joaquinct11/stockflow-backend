package com.stockflow.dto;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import lombok.Data;

/**
 * Payload que envía Culqi a nuestro webhook.
 *
 * Culqi serializa el campo "data" como un String JSON (no como objeto anidado),
 * por eso se recibe como String y se parsea manualmente en el controller.
 *
 * Ejemplo real recibido:
 * <pre>
 * {
 *   "id":   "evt_test_xxx",
 *   "type": "charge.creation.succeeded",
 *   "data": "{\"object\":\"charge\",\"id\":\"chr_test_xxx\",\"subscription_id\":\"sxn_test_xxx\",...}"
 * }
 * </pre>
 */
@Data
@JsonIgnoreProperties(ignoreUnknown = true)
public class CulqiWebhookDTO {

    /** ID del evento, ej: "evt_test_AbCdEf123" */
    private String id;

    /**
     * Tipo de evento. Ejemplos:
     * <ul>
     *   <li>charge.creation.succeeded</li>
     *   <li>charge.creation.failed</li>
     *   <li>subscription.cancel.succeeded</li>
     * </ul>
     */
    private String type;

    /**
     * Culqi envía este campo como un String JSON serializado, no como objeto.
     * Se parsea con ObjectMapper dentro del controller.
     */
    private String data;
}
