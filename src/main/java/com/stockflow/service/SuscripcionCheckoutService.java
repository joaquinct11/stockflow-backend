package com.stockflow.service;

import com.stockflow.dto.MercadoPagoWebhookRequestDTO;
import com.stockflow.dto.SuscripcionCheckoutResponseDTO;
import com.stockflow.dto.SuscripcionEstadoResponseDTO;

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

    /**
     * Retorna el estado actual de suscripción para un tenant/usuario.
     * Nunca lanza excepción por ausencia de suscripción: en ese caso retorna estado "SIN_SUSCRIPCION".
     */
    SuscripcionEstadoResponseDTO obtenerEstadoSuscripcion(String tenantId, Long usuarioId);

    /**
     * Cancela una suscripción localmente y en Mercado Pago si tiene preapproval_id.
     * Si la cancelación en MP falla, se registra el error pero la cancelación local se aplica igual.
     */
    void cancelarSuscripcion(Long suscripcionId);

    /**
     * Consulta el estado actual del preapproval directamente en Mercado Pago
     * y sincroniza el estado local. Útil en la pantalla de retorno tras el pago.
     */
    SuscripcionEstadoResponseDTO sincronizarDesdeMP(String tenantId, Long usuarioId);
}
