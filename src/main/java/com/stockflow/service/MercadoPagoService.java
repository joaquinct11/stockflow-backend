package com.stockflow.service;

import com.stockflow.service.model.MercadoPagoPaymentInfo;
import com.stockflow.service.model.MercadoPagoPreapprovalInfo;
import com.stockflow.service.model.MercadoPagoPreferenceResponse;

import java.math.BigDecimal;

public interface MercadoPagoService {

    MercadoPagoPreferenceResponse crearPreferencia(String planId, BigDecimal precioMensual, String externalReference);

    MercadoPagoPreapprovalInfo crearPreapproval(String planId, BigDecimal precioMensual, String externalReference, String payerEmail);

    MercadoPagoPreapprovalInfo obtenerPreapproval(String preapprovalId);

    MercadoPagoPaymentInfo obtenerPago(String paymentId);
}
