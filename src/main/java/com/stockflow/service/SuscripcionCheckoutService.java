package com.stockflow.service;

import com.stockflow.dto.MercadoPagoWebhookRequestDTO;
import com.stockflow.dto.SuscripcionCheckoutResponseDTO;

public interface SuscripcionCheckoutService {

    SuscripcionCheckoutResponseDTO iniciarCheckout(String planId, String tenantId, Long usuarioId);

    void procesarWebhook(MercadoPagoWebhookRequestDTO webhookRequestDTO);
}
