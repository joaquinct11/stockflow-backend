package com.stockflow.service;

import com.stockflow.dto.MercadoPagoWebhookRequestDTO;
import com.stockflow.dto.SuscripcionCheckoutResponseDTO;

public interface SuscripcionCheckoutService {

    /**
     * Inicia el checkout de Mercado Pago Suscripciones (preapproval).
     *
     * @param planId                   identificador del plan (BASICO, PRO)
     * @param tenantId                 tenant del usuario autenticado
     * @param usuarioId                ID del usuario autenticado
     * @param payerIdentificationType  tipo de documento del pagador (DNI, CE, RUC, PASAPORTE)
     *                                 puede ser null; en ese caso se usa el valor guardado en el usuario.
     * @param payerIdentificationNumber número de documento del pagador; puede ser null.
     */
    SuscripcionCheckoutResponseDTO iniciarCheckout(String planId, String tenantId, Long usuarioId,
                                                   String payerIdentificationType, String payerIdentificationNumber);

    void procesarWebhook(MercadoPagoWebhookRequestDTO webhookRequestDTO);
}
