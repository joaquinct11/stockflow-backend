package com.stockflow.controller;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.stockflow.config.properties.MercadoPagoProperties;
import com.stockflow.dto.MercadoPagoWebhookRequestDTO;
import com.stockflow.service.SuscripcionCheckoutService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import java.nio.charset.StandardCharsets;
import java.security.InvalidKeyException;
import java.security.NoSuchAlgorithmException;
import java.util.HexFormat;
import java.util.Map;

@Slf4j
@RestController
@RequestMapping("/webhooks")
@RequiredArgsConstructor
public class WebhookController {

    private final SuscripcionCheckoutService suscripcionCheckoutService;
    private final MercadoPagoProperties mercadoPagoProperties;
    private final ObjectMapper objectMapper;

    @PostMapping("/mercadopago")
    public ResponseEntity<Map<String, String>> recibirWebhookMercadoPago(
            @RequestBody(required = false) String rawPayload,
            @RequestHeader(value = "x-signature", required = false) String xSignature,
            @RequestHeader(value = "x-request-id", required = false) String xRequestId,
            @RequestParam(value = "data.id", required = false) String dataId) {

        // Validar firma de Mercado Pago
        if (!validarFirmaMercadoPago(rawPayload, xSignature, xRequestId, dataId)) {
            log.warn("⚠️ Webhook Mercado Pago rechazado por firma inválida");
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED)
                    .body(Map.of("status", "unauthorized"));
        }

        try {
            MercadoPagoWebhookRequestDTO payload = objectMapper.readValue(
                    rawPayload, MercadoPagoWebhookRequestDTO.class);
            suscripcionCheckoutService.procesarWebhook(payload);
            return ResponseEntity.ok(Map.of("status", "ok"));
        } catch (Exception e) {
            log.error("❌ Error procesando webhook de Mercado Pago", e);
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                    .body(Map.of("status", "error", "message", e.getMessage()));
        }
    }

    /**
     * Valida la firma HMAC SHA-256 de Mercado Pago.
     * Documentación: https://www.mercadopago.com/developers/es/docs/your-integrations/notifications/webhooks
     */
    private boolean validarFirmaMercadoPago(String payload, String xSignature,
                                            String xRequestId, String dataId) {
        String webhookSecret = mercadoPagoProperties.getWebhookSecret();

        if (webhookSecret == null || webhookSecret.isBlank()) {
            log.warn("⚠️ mercadopago.webhook-secret no está configurado; webhook sin validación.");
            return true; // Permitir en desarrollo, NUNCA en producción
        }

        if (xSignature == null || xSignature.isBlank()) {
            log.warn("⚠️ Header x-signature ausente en webhook MP");
            return false;
        }

        try {
            // Extraer valores de x-signature
            // Formato: "ts=1234567890,v1=hash_value"
            String[] parts = xSignature.split(",");
            String ts = null;
            String hash = null;

            for (String part : parts) {
                String[] keyValue = part.split("=", 2);
                if (keyValue.length == 2) {
                    if ("ts".equals(keyValue[0])) {
                        ts = keyValue[1];
                    } else if ("v1".equals(keyValue[0])) {
                        hash = keyValue[1];
                    }
                }
            }

            if (ts == null || hash == null) {
                log.warn("⚠️ Formato inválido de x-signature: {}", xSignature);
                return false;
            }

            // Construir el mensaje para validar
            // Formato: id:{dataId};request-id:{xRequestId};ts:{ts};
            String mensaje = String.format("id:%s;request-id:%s;ts:%s;",
                    dataId != null ? dataId : "",
                    xRequestId != null ? xRequestId : "",
                    ts);

            // Calcular HMAC SHA-256
            String hashCalculado = calcularHmacSha256(mensaje, webhookSecret);

            boolean esValido = hashCalculado.equalsIgnoreCase(hash);

            if (!esValido) {
                log.warn("⚠️ Firma inválida. Esperado: {}, Recibido: {}", hashCalculado, hash);
            }

            return esValido;

        } catch (Exception e) {
            log.error("❌ Error validando firma de webhook MP", e);
            return false;
        }
    }

    private String calcularHmacSha256(String mensaje, String secreto)
            throws NoSuchAlgorithmException, InvalidKeyException {
        Mac hmacSha256 = Mac.getInstance("HmacSHA256");
        SecretKeySpec secretKey = new SecretKeySpec(
                secreto.getBytes(StandardCharsets.UTF_8), "HmacSHA256");
        hmacSha256.init(secretKey);
        byte[] hashBytes = hmacSha256.doFinal(mensaje.getBytes(StandardCharsets.UTF_8));
        return HexFormat.of().formatHex(hashBytes);
    }
}