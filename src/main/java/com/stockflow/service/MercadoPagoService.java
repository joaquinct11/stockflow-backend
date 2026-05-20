package com.stockflow.service;

import com.stockflow.service.model.MercadoPagoAuthorizedPaymentInfo;
import com.stockflow.service.model.MercadoPagoPaymentInfo;
import com.stockflow.service.model.MercadoPagoPreapprovalInfo;
import com.stockflow.service.model.MercadoPagoPreferenceResponse;

import java.math.BigDecimal;

public interface MercadoPagoService {

    MercadoPagoPreferenceResponse crearPreferencia(String planId, BigDecimal precioMensual, String externalReference);

    MercadoPagoPreapprovalInfo crearPreapproval(String planId, BigDecimal precioMensual, String externalReference,
                                                String payerEmail, String payerIdentificationType, String payerIdentificationNumber);

    MercadoPagoPreapprovalInfo obtenerPreapproval(String preapprovalId);

    MercadoPagoPaymentInfo obtenerPago(String paymentId);

    void cancelarPreapproval(String preapprovalId);

    MercadoPagoAuthorizedPaymentInfo obtenerAuthorizedPayment(String authorizedPaymentId);

    /**
     * Busca el pago aprobado más reciente asociado a una preferencia (checkout básico/upgrade).
     * Devuelve null si no hay ningún pago aprobado.
     */
    MercadoPagoPaymentInfo buscarPagoPorPreferencia(String preferenceId);

    /**
     * Busca el pago aprobado más reciente con el external_reference dado.
     * Útil como fallback cuando no se guardó el preferenceId.
     * Devuelve null si no hay ningún pago aprobado.
     */
    MercadoPagoPaymentInfo buscarPagoAprobadoPorExternalReference(String externalReference);
}
