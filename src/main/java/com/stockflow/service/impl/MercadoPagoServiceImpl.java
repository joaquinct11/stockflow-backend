package com.stockflow.service.impl;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.stockflow.config.properties.MercadoPagoProperties;
import com.stockflow.exception.BadRequestException;
import com.stockflow.service.MercadoPagoService;
import com.stockflow.service.model.MercadoPagoPaymentInfo;
import com.stockflow.service.model.MercadoPagoPreferenceResponse;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

@Slf4j
@Service
@RequiredArgsConstructor
public class MercadoPagoServiceImpl implements MercadoPagoService {

    private final MercadoPagoProperties mercadoPagoProperties;
    private final ObjectMapper objectMapper;
    private final HttpClient httpClient = HttpClient.newHttpClient();

    @Override
    public MercadoPagoPreferenceResponse crearPreferencia(String planId, BigDecimal precioMensual, String externalReference) {
        validarConfiguracion();

        try {
            Map<String, Object> payload = new HashMap<>();
            payload.put("items", List.of(
                    Map.of(
                            "title", "StockFlow Plan " + planId,
                            "quantity", 1,
                            "currency_id", mercadoPagoProperties.getCurrencyId(),
                            "unit_price", precioMensual
                    )
            ));
            payload.put("external_reference", externalReference);
            payload.put("auto_return", "approved");

            if (mercadoPagoProperties.getNotificationUrl() != null && !mercadoPagoProperties.getNotificationUrl().isBlank()) {
                payload.put("notification_url", mercadoPagoProperties.getNotificationUrl());
            }

            if (hasAnyBackUrl()) {
                Map<String, String> backUrls = new HashMap<>();
                if (mercadoPagoProperties.getSuccessUrl() != null && !mercadoPagoProperties.getSuccessUrl().isBlank()) {
                    backUrls.put("success", mercadoPagoProperties.getSuccessUrl());
                }
                if (mercadoPagoProperties.getFailureUrl() != null && !mercadoPagoProperties.getFailureUrl().isBlank()) {
                    backUrls.put("failure", mercadoPagoProperties.getFailureUrl());
                }
                if (mercadoPagoProperties.getPendingUrl() != null && !mercadoPagoProperties.getPendingUrl().isBlank()) {
                    backUrls.put("pending", mercadoPagoProperties.getPendingUrl());
                }
                payload.put("back_urls", backUrls);
            }

            HttpRequest request = HttpRequest.newBuilder()
                    .uri(URI.create(mercadoPagoProperties.getCheckoutBaseUrl() + "/checkout/preferences"))
                    .header("Authorization", "Bearer " + mercadoPagoProperties.getAccessToken())
                    .header("Content-Type", "application/json")
                    .POST(HttpRequest.BodyPublishers.ofString(objectMapper.writeValueAsString(payload)))
                    .build();

            HttpResponse<String> response = httpClient.send(request, HttpResponse.BodyHandlers.ofString());
            if (response.statusCode() >= 400) {
                throw new BadRequestException("Error creando preferencia en Mercado Pago. status=" + response.statusCode());
            }

            Map<String, Object> responseBody = objectMapper.readValue(response.body(), new TypeReference<>() {});

            return MercadoPagoPreferenceResponse.builder()
                    .preferenceId((String) responseBody.get("id"))
                    .initPoint((String) responseBody.get("init_point"))
                    .build();

        } catch (BadRequestException ex) {
            throw ex;
        } catch (Exception ex) {
            log.error("❌ Error creando preferencia de Mercado Pago", ex);
            throw new BadRequestException("No se pudo crear checkout en Mercado Pago", ex);
        }
    }

    @Override
    public MercadoPagoPaymentInfo obtenerPago(String paymentId) {
        validarConfiguracion();

        try {
            HttpRequest request = HttpRequest.newBuilder()
                    .uri(URI.create(mercadoPagoProperties.getCheckoutBaseUrl() + "/v1/payments/" + paymentId))
                    .header("Authorization", "Bearer " + mercadoPagoProperties.getAccessToken())
                    .GET()
                    .build();

            HttpResponse<String> response = httpClient.send(request, HttpResponse.BodyHandlers.ofString());
            if (response.statusCode() >= 400) {
                throw new BadRequestException("No se pudo obtener pago de Mercado Pago. status=" + response.statusCode());
            }

            Map<String, Object> body = objectMapper.readValue(response.body(), new TypeReference<>() {});

            String preferenceId = null;
            Object orderObj = body.get("order");
            if (orderObj instanceof Map<?, ?> orderMap && orderMap.get("id") != null) {
                preferenceId = String.valueOf(orderMap.get("id"));
            }

            String lastFourDigits = null;
            Object cardObj = body.get("card");
            if (cardObj instanceof Map<?, ?> cardMap && cardMap.get("last_four_digits") != null) {
                lastFourDigits = String.valueOf(cardMap.get("last_four_digits"));
            }

            return MercadoPagoPaymentInfo.builder()
                    .paymentId(String.valueOf(body.get("id")))
                    .status(String.valueOf(body.get("status")))
                    .preferenceId(preferenceId)
                    .externalReference(body.get("external_reference") != null ? String.valueOf(body.get("external_reference")) : null)
                    .lastFourDigits(lastFourDigits)
                    .build();
        } catch (BadRequestException ex) {
            throw ex;
        } catch (Exception ex) {
            log.error("❌ Error consultando pago {} en Mercado Pago", paymentId, ex);
            throw new BadRequestException("No se pudo consultar el pago en Mercado Pago", ex);
        }
    }

    private void validarConfiguracion() {
        if (mercadoPagoProperties.getAccessToken() == null || mercadoPagoProperties.getAccessToken().isBlank()) {
            throw new BadRequestException("Configuración inválida: mercadopago.access-token es requerido");
        }
    }

    private boolean hasAnyBackUrl() {
        return (mercadoPagoProperties.getSuccessUrl() != null && !mercadoPagoProperties.getSuccessUrl().isBlank())
                || (mercadoPagoProperties.getFailureUrl() != null && !mercadoPagoProperties.getFailureUrl().isBlank())
                || (mercadoPagoProperties.getPendingUrl() != null && !mercadoPagoProperties.getPendingUrl().isBlank());
    }
}
