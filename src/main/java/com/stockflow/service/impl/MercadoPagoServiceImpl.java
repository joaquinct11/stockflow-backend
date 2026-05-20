package com.stockflow.service.impl;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.stockflow.config.properties.MercadoPagoProperties;
import com.stockflow.exception.BadRequestException;
import com.stockflow.service.MercadoPagoService;
import com.stockflow.service.model.MercadoPagoAuthorizedPaymentInfo;
import com.stockflow.service.model.MercadoPagoPaymentInfo;
import com.stockflow.service.model.MercadoPagoPreapprovalInfo;
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
                            "title", "Fluxus Plan " + planId,
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
                log.error("❌ Error creando preferencia en Mercado Pago. status={}, body={}", response.statusCode(), response.body());
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
    public MercadoPagoPreapprovalInfo crearPreapproval(String planId, BigDecimal precioMensual,
                                                       String externalReference, String payerEmail,
                                                       String payerIdentificationType, String payerIdentificationNumber) {
        validarConfiguracion();
        validarNotificationUrl();
        validarBackUrl();

        log.info("MP successUrl={}", mercadoPagoProperties.getSuccessUrl());
        log.info("MP notificationUrl={}", mercadoPagoProperties.getNotificationUrl());

        try {
            String accessToken = mercadoPagoProperties.getAccessToken();
            boolean isTestToken = accessToken != null && accessToken.startsWith("TEST-");

            Map<String, Object> payload = buildPreapprovalPayload(
                    planId, precioMensual, externalReference, payerEmail,
                    payerIdentificationType, payerIdentificationNumber, isTestToken);

            String payloadJson = objectMapper.writeValueAsString(payload);

            String tokenPrefix = accessToken != null
                    ? accessToken.substring(0, Math.min(4, accessToken.length())) + "..."
                    : "null";
            log.info("🔑 MP token prefix={}, isTestToken={}", tokenPrefix, isTestToken);
            log.info("📋 MP external_reference={}, back_url={}", externalReference, mercadoPagoProperties.getSuccessUrl());
            log.debug("📤 Payload enviado a MP /preapproval: {}", payloadJson);

            log.info("🔄 Creando preapproval MP para plan={}, externalRef={}", planId, externalReference);

            HttpRequest request = HttpRequest.newBuilder()
                    .uri(URI.create(mercadoPagoProperties.getCheckoutBaseUrl() + "/preapproval"))
                    .header("Authorization", "Bearer " + accessToken)
                    .header("Content-Type", "application/json")
                    .POST(HttpRequest.BodyPublishers.ofString(payloadJson))
                    .build();

            HttpResponse<String> response = httpClient.send(request, HttpResponse.BodyHandlers.ofString());
            log.info("📥 Respuesta MP /preapproval: status={}, body={}", response.statusCode(), response.body());

            if (response.statusCode() >= 400) {
                String mpMessage = extractMpErrorMessage(response.body());
                log.error("❌ Error creando preapproval en Mercado Pago. status={}, body={}", response.statusCode(), response.body());
                throw new BadRequestException("Error creando suscripción en Mercado Pago: " + mpMessage);
            }

            Map<String, Object> responseBody = objectMapper.readValue(response.body(), new TypeReference<>() {});

            Object applicationId = responseBody.get("application_id");
            Object collectorId = responseBody.get("collector_id");
            log.info("🏷️ MP preapproval creado: application_id={}, collector_id={}", applicationId, collectorId);

            return MercadoPagoPreapprovalInfo.builder()
                    .preapprovalId((String) responseBody.get("id"))
                    .initPoint((String) responseBody.get("init_point"))
                    .status((String) responseBody.get("status"))
                    .externalReference((String) responseBody.get("external_reference"))
                    .build();

        } catch (BadRequestException ex) {
            throw ex;
        } catch (Exception ex) {
            log.error("❌ Error creando preapproval de Mercado Pago", ex);
            throw new BadRequestException("No se pudo crear la suscripción en Mercado Pago", ex);
        }
    }

    @Override
    public MercadoPagoPreapprovalInfo obtenerPreapproval(String preapprovalId) {
        validarConfiguracion();

        try {
            HttpRequest request = HttpRequest.newBuilder()
                    .uri(URI.create(mercadoPagoProperties.getCheckoutBaseUrl() + "/preapproval/" + preapprovalId))
                    .header("Authorization", "Bearer " + mercadoPagoProperties.getAccessToken())
                    .GET()
                    .build();

            HttpResponse<String> response = httpClient.send(request, HttpResponse.BodyHandlers.ofString());
            log.info("📥 Respuesta MP GET /preapproval/{}: status={}, body={}", preapprovalId, response.statusCode(), response.body());
            if (response.statusCode() >= 400) {
                log.error("❌ Error consultando preapproval {}. status={}, body={}", preapprovalId, response.statusCode(), response.body());
                throw new BadRequestException("No se pudo consultar la suscripción en Mercado Pago. status=" + response.statusCode());
            }

            Map<String, Object> body = objectMapper.readValue(response.body(), new TypeReference<>() {});

            return MercadoPagoPreapprovalInfo.builder()
                    .preapprovalId((String) body.get("id"))
                    .status((String) body.get("status"))
                    .initPoint((String) body.get("init_point"))
                    .externalReference(body.get("external_reference") != null ? String.valueOf(body.get("external_reference")) : null)
                    .build();

        } catch (BadRequestException ex) {
            throw ex;
        } catch (Exception ex) {
            log.error("❌ Error consultando preapproval {} en Mercado Pago", preapprovalId, ex);
            throw new BadRequestException("No se pudo consultar la suscripción en Mercado Pago", ex);
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
                log.error("❌ Error consultando pago {}. status={}, body={}", paymentId, response.statusCode(), response.body());
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

    Map<String, Object> buildPreapprovalPayload(String planId, BigDecimal precioMensual,
                                                String externalReference, String payerEmail,
                                                String payerIdentificationType, String payerIdentificationNumber,
                                                boolean isTestToken) {
        Map<String, Object> autoRecurring = new HashMap<>();
        autoRecurring.put("frequency", 1);
        autoRecurring.put("frequency_type", "months");
        autoRecurring.put("transaction_amount", precioMensual);
        autoRecurring.put("currency_id", mercadoPagoProperties.getCurrencyId());

        Map<String, Object> payload = new HashMap<>();
        payload.put("reason", "Fluxus Plan " + planId);
        payload.put("auto_recurring", autoRecurring);
        payload.put("external_reference", externalReference);
        payload.put("notification_url", mercadoPagoProperties.getNotificationUrl());
        payload.put("back_url", mercadoPagoProperties.getSuccessUrl());
        // status "pending" es requerido para crear la suscripción en estado
        // "esperando autorización del pagador" y obtener el initPoint correcto.
        payload.put("status", "pending");

        String effectivePayerEmail;
        if (isTestToken) {
            // En ambiente TEST usar el email de prueba configurable.
            // MP requiere payer_email incluso en sandbox.
            String testPayerEmail = mercadoPagoProperties.getTestPayerEmail();
            if (testPayerEmail == null || testPayerEmail.isBlank()) {
                throw new BadRequestException(
                        "Configuración inválida: mercadopago.test-payer-email (MERCADOPAGO_TEST_PAYER_EMAIL) "
                        + "es requerido cuando se usa un token TEST de Mercado Pago");
            }
            if (!testPayerEmail.contains("@")) {
                throw new BadRequestException(
                        "Configuración inválida: mercadopago.test-payer-email (MERCADOPAGO_TEST_PAYER_EMAIL) "
                        + "debe ser un email válido (contener '@')");
            }
            effectivePayerEmail = testPayerEmail;
            String domain = effectivePayerEmail.substring(effectivePayerEmail.indexOf('@'));
            log.info("🔑 payerEmailMode=CONFIG_TEST_PAYER_EMAIL domain={}", domain);
        } else {
            // En PROD, incluir el email del usuario autenticado.
            effectivePayerEmail = payerEmail;
            log.info("🔑 payerEmailMode=AUTH_USER_FOR_PROD");
        }

        payload.put("payer_email", effectivePayerEmail);

        // Incluir objeto payer con identificación si está disponible.
        // Esto pre-rellena el formulario de MP y evita que el botón
        // "Confirmar" quede deshabilitado por datos faltantes del pagador.
        if (payerIdentificationType != null && !payerIdentificationType.isBlank()
                && payerIdentificationNumber != null && !payerIdentificationNumber.isBlank()) {
            Map<String, Object> identification = new HashMap<>();
            identification.put("type", payerIdentificationType);
            identification.put("number", payerIdentificationNumber);

            Map<String, Object> payer = new HashMap<>();
            payer.put("email", effectivePayerEmail);
            payer.put("identification", identification);

            payload.put("payer", payer);
            log.info("🪪 Enviando identificación del pagador a MP: tipo={}, numero=****{}",
                    payerIdentificationType,
                    payerIdentificationNumber.length() > 4
                            ? payerIdentificationNumber.substring(payerIdentificationNumber.length() - 4)
                            : "****");
        } else {
            log.warn("⚠️ No se envía identificación del pagador a MP (tipoDocumento/numeroDocumento no disponibles). "
                    + "El botón 'Confirmar' puede quedar deshabilitado si el perfil del pagador en MP no está verificado.");
        }

        return payload;
    }

    private void validarConfiguracion() {
        if (mercadoPagoProperties.getAccessToken() == null || mercadoPagoProperties.getAccessToken().isBlank()) {
            throw new BadRequestException("Configuración inválida: MERCADOPAGO_ACCESS_TOKEN no está configurado");
        }
        String baseUrl = mercadoPagoProperties.getCheckoutBaseUrl();
        if (baseUrl == null || baseUrl.isBlank()) {
            throw new BadRequestException("Configuración inválida: MERCADOPAGO_BASE_URL no está configurado (debe ser https://api.mercadopago.com)");
        }
        if (!baseUrl.startsWith("http://") && !baseUrl.startsWith("https://")) {
            throw new BadRequestException("Configuración inválida: MERCADOPAGO_BASE_URL debe comenzar con https:// — valor actual: '" + baseUrl + "'");
        }
    }

    private void validarNotificationUrl() {
        if (mercadoPagoProperties.getNotificationUrl() == null || mercadoPagoProperties.getNotificationUrl().isBlank()) {
            throw new BadRequestException("Configuración inválida: mercadopago.notification-url es requerido para suscripciones");
        }
    }

    void validarBackUrl() {
        String backUrl = mercadoPagoProperties.getSuccessUrl();
        if (backUrl == null || backUrl.isBlank()) {
            throw new BadRequestException("Configuración inválida: mercadopago.success-url (back_url) es requerido para suscripciones");
        }
        if (!backUrl.startsWith("http://") && !backUrl.startsWith("https://")) {
            throw new BadRequestException("Configuración inválida: mercadopago.success-url debe comenzar con http:// o https://");
        }
    }

    private String extractMpErrorMessage(String responseBody) {
        try {
            Map<String, Object> body = objectMapper.readValue(responseBody, new TypeReference<>() {});
            if (body.get("message") != null) {
                return String.valueOf(body.get("message"));
            }
        } catch (Exception ignored) {
            // ignore JSON parse errors; fall through to return raw body
        }
        return responseBody;
    }

    @Override
    public MercadoPagoAuthorizedPaymentInfo obtenerAuthorizedPayment(String authorizedPaymentId) {
        validarConfiguracion();

        try {
            HttpRequest request = HttpRequest.newBuilder()
                    .uri(URI.create(mercadoPagoProperties.getCheckoutBaseUrl() + "/authorized_payments/" + authorizedPaymentId))
                    .header("Authorization", "Bearer " + mercadoPagoProperties.getAccessToken())
                    .GET()
                    .build();

            HttpResponse<String> response = httpClient.send(request, HttpResponse.BodyHandlers.ofString());
            log.info("📥 Respuesta MP GET /authorized_payments/{}: status={}, body={}", authorizedPaymentId, response.statusCode(), response.body());

            if (response.statusCode() >= 400) {
                log.error("❌ Error consultando authorized_payment {}. status={}, body={}", authorizedPaymentId, response.statusCode(), response.body());
                throw new BadRequestException("No se pudo consultar el authorized_payment en Mercado Pago. status=" + response.statusCode());
            }

            Map<String, Object> body = objectMapper.readValue(response.body(), new TypeReference<>() {});

            String preapprovalId = body.get("preapproval_id") != null ? String.valueOf(body.get("preapproval_id")) : null;
            String status = body.get("status") != null ? String.valueOf(body.get("status")) : null;

            String paymentId = null;
            String paymentStatus = null;
            Object paymentObj = body.get("payment");
            if (paymentObj instanceof Map<?, ?> paymentMap) {
                if (paymentMap.get("id") != null) paymentId = String.valueOf(paymentMap.get("id"));
                if (paymentMap.get("status") != null) paymentStatus = String.valueOf(paymentMap.get("status"));
            }

            return MercadoPagoAuthorizedPaymentInfo.builder()
                    .authorizedPaymentId(String.valueOf(body.get("id")))
                    .preapprovalId(preapprovalId)
                    .status(status)
                    .paymentId(paymentId)
                    .paymentStatus(paymentStatus)
                    .build();

        } catch (BadRequestException ex) {
            throw ex;
        } catch (Exception ex) {
            log.error("❌ Error consultando authorized_payment {} en Mercado Pago", authorizedPaymentId, ex);
            throw new BadRequestException("No se pudo consultar el authorized_payment en Mercado Pago", ex);
        }
    }

    @Override
    public void cancelarPreapproval(String preapprovalId) {
        validarConfiguracion();

        try {
            Map<String, Object> payload = Map.of("status", "cancelled");

            HttpRequest request = HttpRequest.newBuilder()
                    .uri(URI.create(mercadoPagoProperties.getCheckoutBaseUrl() + "/preapproval/" + preapprovalId))
                    .header("Authorization", "Bearer " + mercadoPagoProperties.getAccessToken())
                    .header("Content-Type", "application/json")
                    .PUT(HttpRequest.BodyPublishers.ofString(objectMapper.writeValueAsString(payload)))
                    .build();

            HttpResponse<String> response = httpClient.send(request, HttpResponse.BodyHandlers.ofString());
            log.info("📥 Respuesta MP PUT /preapproval/{}: status={}", preapprovalId, response.statusCode());

            if (response.statusCode() >= 400) {
                log.error("❌ Error cancelando preapproval {}. status={}, body={}", preapprovalId, response.statusCode(), response.body());
                throw new BadRequestException("No se pudo cancelar la suscripción en Mercado Pago. status=" + response.statusCode());
            }
        } catch (BadRequestException ex) {
            throw ex;
        } catch (Exception ex) {
            log.error("❌ Error cancelando preapproval {} en Mercado Pago", preapprovalId, ex);
            throw new BadRequestException("No se pudo cancelar la suscripción en Mercado Pago", ex);
        }
    }

    @Override
    public MercadoPagoPaymentInfo buscarPagoPorPreferencia(String preferenceId) {
        validarConfiguracion();
        try {
            // GET /v1/payments/search?preference_id=XXX&sort=date_created&criteria=desc&limit=5
            String url = mercadoPagoProperties.getCheckoutBaseUrl()
                    + "/v1/payments/search?preference_id=" + preferenceId
                    + "&sort=date_created&criteria=desc&limit=5";

            HttpRequest request = HttpRequest.newBuilder()
                    .uri(URI.create(url))
                    .header("Authorization", "Bearer " + mercadoPagoProperties.getAccessToken())
                    .GET()
                    .build();

            HttpResponse<String> response = httpClient.send(request, HttpResponse.BodyHandlers.ofString());
            log.info("📥 Respuesta MP GET /v1/payments/search?preference_id={}: status={}", preferenceId, response.statusCode());

            if (response.statusCode() >= 400) {
                log.warn("⚠️ No se pudo buscar pagos por preferencia {}. status={}", preferenceId, response.statusCode());
                return null;
            }

            Map<String, Object> body = objectMapper.readValue(response.body(), new TypeReference<>() {});
            Object resultsObj = body.get("results");
            if (!(resultsObj instanceof List<?> results) || results.isEmpty()) {
                log.info("ℹ️ No se encontraron pagos para preference_id={}", preferenceId);
                return null;
            }

            // Buscar el primer pago aprobado
            for (Object item : results) {
                if (!(item instanceof Map<?, ?> paymentMap)) continue;
                String status = paymentMap.get("status") != null ? String.valueOf(paymentMap.get("status")) : null;
                if (!"approved".equalsIgnoreCase(status)) continue;

                String paymentId = paymentMap.get("id") != null ? String.valueOf(paymentMap.get("id")) : null;
                String extRef = paymentMap.get("external_reference") != null
                        ? String.valueOf(paymentMap.get("external_reference")) : null;

                String lastFour = null;
                Object cardObj = paymentMap.get("card");
                if (cardObj instanceof Map<?, ?> cardMap && cardMap.get("last_four_digits") != null) {
                    lastFour = String.valueOf(cardMap.get("last_four_digits"));
                }

                log.info("✅ Pago aprobado encontrado para preference_id={}: paymentId={}", preferenceId, paymentId);
                return MercadoPagoPaymentInfo.builder()
                        .paymentId(paymentId)
                        .status(status)
                        .preferenceId(preferenceId)
                        .externalReference(extRef)
                        .lastFourDigits(lastFour)
                        .build();
            }

            log.info("ℹ️ Ningún pago aprobado encontrado para preference_id={}", preferenceId);
            return null;

        } catch (Exception ex) {
            log.warn("⚠️ Error buscando pagos por preferencia {} en MP: {}", preferenceId, ex.getMessage());
            return null; // no lanzar excepción — es solo un check adicional
        }
    }

    @Override
    public MercadoPagoPaymentInfo buscarPagoAprobadoPorExternalReference(String externalReference) {
        validarConfiguracion();
        try {
            String url = mercadoPagoProperties.getCheckoutBaseUrl()
                    + "/v1/payments/search?external_reference=" + java.net.URLEncoder.encode(externalReference, java.nio.charset.StandardCharsets.UTF_8)
                    + "&sort=date_created&criteria=desc&limit=10";

            HttpRequest request = HttpRequest.newBuilder()
                    .uri(URI.create(url))
                    .header("Authorization", "Bearer " + mercadoPagoProperties.getAccessToken())
                    .GET()
                    .build();

            HttpResponse<String> response = httpClient.send(request, HttpResponse.BodyHandlers.ofString());
            log.info("📥 Respuesta MP GET /v1/payments/search?external_reference={}: status={}", externalReference, response.statusCode());

            if (response.statusCode() >= 400) {
                log.warn("⚠️ No se pudo buscar pagos por external_reference {}. status={}", externalReference, response.statusCode());
                return null;
            }

            Map<String, Object> body = objectMapper.readValue(response.body(), new TypeReference<>() {});
            Object resultsObj = body.get("results");
            if (!(resultsObj instanceof List<?> results) || results.isEmpty()) {
                log.info("ℹ️ No se encontraron pagos para external_reference={}", externalReference);
                return null;
            }

            for (Object item : results) {
                if (!(item instanceof Map<?, ?> paymentMap)) continue;
                String status = paymentMap.get("status") != null ? String.valueOf(paymentMap.get("status")) : null;
                if (!"approved".equalsIgnoreCase(status)) continue;

                String paymentId = paymentMap.get("id") != null ? String.valueOf(paymentMap.get("id")) : null;
                String prefId = null;
                Object orderObj = paymentMap.get("order");
                if (orderObj instanceof Map<?, ?> orderMap && orderMap.get("id") != null) {
                    prefId = String.valueOf(orderMap.get("id"));
                }
                String lastFour = null;
                Object cardObj = paymentMap.get("card");
                if (cardObj instanceof Map<?, ?> cardMap && cardMap.get("last_four_digits") != null) {
                    lastFour = String.valueOf(cardMap.get("last_four_digits"));
                }

                log.info("✅ Pago aprobado encontrado por external_reference={}: paymentId={}", externalReference, paymentId);
                return MercadoPagoPaymentInfo.builder()
                        .paymentId(paymentId)
                        .status(status)
                        .preferenceId(prefId)
                        .externalReference(externalReference)
                        .lastFourDigits(lastFour)
                        .build();
            }

            log.info("ℹ️ Ningún pago aprobado encontrado para external_reference={}", externalReference);
            return null;

        } catch (Exception ex) {
            log.warn("⚠️ Error buscando pagos por external_reference {} en MP: {}", externalReference, ex.getMessage());
            return null;
        }
    }

    private boolean hasAnyBackUrl() {
        return (mercadoPagoProperties.getSuccessUrl() != null && !mercadoPagoProperties.getSuccessUrl().isBlank())
                || (mercadoPagoProperties.getFailureUrl() != null && !mercadoPagoProperties.getFailureUrl().isBlank())
                || (mercadoPagoProperties.getPendingUrl() != null && !mercadoPagoProperties.getPendingUrl().isBlank());
    }
}
