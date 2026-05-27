package com.stockflow.service;

import java.math.BigDecimal;

public interface CulqiService {

    /** Crea un cliente en Culqi y devuelve su ID (cus_live_xxx) */
    String crearCliente(String email, String firstName, String lastName, String phoneNumber);

    /** Registra la tarjeta tokenizada en Culqi y devuelve el card ID (crd_live_xxx) */
    String crearTarjeta(String customerId, String tokenId);

    /** Crea la suscripción recurrente en Culqi y devuelve el subscription ID (sub_live_xxx) */
    String crearSuscripcion(String cardId, String planId);

    /** Cancela una suscripción en Culqi */
    void cancelarSuscripcion(String subscriptionId);

    /**
     * Crea un plan en Culqi (operación de setup, llamar una sola vez).
     * @param nombre     nombre descriptivo del plan
     * @param montoCentavos monto en centavos (ej: 12900 = S/129.00)
     * @return ID del plan creado (pln_live_xxx)
     */
    String crearPlan(String nombre, long montoCentavos);
}
