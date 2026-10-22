package com.stockflow.service;

import java.math.BigDecimal;
import java.util.Map;

public interface CulqiService {

    /** Crea un cliente en Culqi y devuelve su ID (cus_live_xxx) */
    String crearCliente(String email, String firstName, String lastName, String phoneNumber);

    /**
     * Registra la tarjeta tokenizada en Culqi.
     * Devuelve la respuesta completa de Culqi (incluye id, source.last_four, source.iin.card_brand, etc.)
     */
    java.util.Map<String, Object> crearTarjeta(String customerId, String tokenId);

    /**
     * Crea la suscripción recurrente en Culqi y devuelve el subscription ID (sxn_xxx).
     * @param metadata pares clave-valor adicionales (ej: tenant_id, usuario_id); puede ser null o vacío.
     */
    String crearSuscripcion(String cardId, String planId, Map<String, String> metadata);

    /** Cancela una suscripción en Culqi */
    void cancelarSuscripcion(String subscriptionId);

    /** Actualiza la tarjeta de una suscripción existente en Culqi */
    void actualizarTarjetaSuscripcion(String subscriptionId, String cardId);

    /**
     * Crea un plan en Culqi (operación de setup, llamar una sola vez).
     * @param nombre     nombre descriptivo del plan
     * @param montoCentavos monto en centavos (ej: 12900 = S/129.00)
     * @return ID del plan creado (pln_live_xxx)
     */
    String crearPlan(String nombre, long montoCentavos);

    /**
     * Obtiene un cargo de Culqi por su ID (GET /charges/{chargeId}).
     * Útil para el webhook charge.creation.failed, donde el payload solo incluye
     * el chargeId y no el subscription_id ni el email directamente.
     * Devuelve null si el cargo no existe o hay un error de red.
     */
    Map<String, Object> obtenerCargo(String chargeId);

    /**
     * Obtiene una suscripción recurrente de Culqi por su ID (GET /recurrent/subscriptions/{subscriptionId}).
     * Usado en la verificación previa a procesar cancel.succeeded: si Culqi reporta la suscripción
     * como "active", el evento es sospechoso y se marca PENDIENTE_REVISION.
     * Devuelve null si la suscripción no existe en Culqi o hay un error de red.
     */
    Map<String, Object> obtenerSuscripcion(String subscriptionId);
}
