package com.stockflow.controller;

import com.stockflow.config.properties.MercadoPagoProperties;
import com.stockflow.dto.MercadoPagoWebhookRequestDTO;
import com.stockflow.service.SuscripcionCheckoutService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.Map;

@Slf4j
@RestController
@RequestMapping("/webhooks")
@RequiredArgsConstructor
public class WebhookController {

    private final SuscripcionCheckoutService suscripcionCheckoutService;
    private final MercadoPagoProperties mercadoPagoProperties;

    @PostMapping("/mercadopago")
    public ResponseEntity<Map<String, String>> recibirWebhookMercadoPago(
            @RequestBody(required = false) MercadoPagoWebhookRequestDTO payload,
            @RequestHeader(value = "X-Webhook-Token", required = false) String headerToken,
            @RequestParam(value = "token", required = false) String queryToken) {

        if (!isWebhookTokenValid(headerToken, queryToken)) {
            log.warn("⚠️ Webhook Mercado Pago rechazado por token inválido");
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED)
                    .body(Map.of("status", "unauthorized"));
        }

        suscripcionCheckoutService.procesarWebhook(payload);
        return ResponseEntity.ok(Map.of("status", "ok"));
    }

    private boolean isWebhookTokenValid(String headerToken, String queryToken) {
        String configuredSecret = mercadoPagoProperties.getWebhookSecret();
        if (configuredSecret == null || configuredSecret.isBlank()) {
            log.warn("⚠️ mercadopago.webhook-secret no está configurado; webhook sin validación de token.");
            return true;
        }
        return configuredSecret.equals(headerToken) || configuredSecret.equals(queryToken);
    }
}
