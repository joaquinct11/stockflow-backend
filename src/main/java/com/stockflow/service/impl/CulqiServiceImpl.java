package com.stockflow.service.impl;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.stockflow.config.properties.CulqiProperties;
import com.stockflow.exception.BadRequestException;
import com.stockflow.service.CulqiService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import javax.crypto.Cipher;
import javax.crypto.KeyGenerator;
import javax.crypto.SecretKey;
import javax.crypto.spec.GCMParameterSpec;
import javax.crypto.spec.OAEPParameterSpec;
import javax.crypto.spec.PSource;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.security.KeyFactory;
import java.security.PublicKey;
import java.security.SecureRandom;
import java.security.spec.MGF1ParameterSpec;
import java.security.spec.X509EncodedKeySpec;
import java.util.Arrays;
import java.util.Base64;
import java.util.HashMap;
import java.util.Map;

@Slf4j
@Service
@RequiredArgsConstructor
public class CulqiServiceImpl implements CulqiService {

    private final CulqiProperties culqiProperties;
    private final ObjectMapper objectMapper;
    private final HttpClient httpClient = HttpClient.newHttpClient();

    // ── Clientes ─────────────────────────────────────────────────────────────

    @Override
    public String crearCliente(String email, String firstName, String lastName, String phoneNumber) {
        String fn    = firstName   != null && !firstName.isBlank()   ? firstName   : "Cliente";
        String ln    = lastName    != null && !lastName.isBlank()    ? lastName    : "-";
        String phone = phoneNumber != null && !phoneNumber.isBlank() ? phoneNumber : "51000000000";

        // Buscar cliente existente primero (find-or-create) para evitar duplicados
        String existingId = buscarClientePorEmail(email);
        if (existingId != null) {
            log.info("♻️ Culqi cliente ya existe, actualizando datos: {}", existingId);
            actualizarCliente(existingId, fn, ln, phone);
            return existingId;
        }

        Map<String, Object> body = new HashMap<>();
        body.put("first_name",   fn);
        body.put("last_name",    ln);
        body.put("email",        email);
        body.put("address",      "Lima, Peru");
        body.put("address_city", "Lima");
        body.put("country_code", "PE");
        body.put("phone_number", phone);

        Map<String, Object> response = post("/customers", body, false);
        String customerId = (String) response.get("id");
        log.info("✅ Culqi cliente creado: {}", customerId);
        return customerId;
    }

    /**
     * Actualiza los datos antifraud de un cliente existente en Culqi.
     * PATCH /customers/{id}
     */
    private void actualizarCliente(String customerId, String firstName, String lastName, String phoneNumber) {
        try {
            Map<String, Object> body = new HashMap<>();
            body.put("first_name",   firstName);
            body.put("last_name",    lastName);
            body.put("phone_number", phoneNumber);
            body.put("address",      "Lima, Peru");
            body.put("address_city", "Lima");
            body.put("country_code", "PE");

            String json  = objectMapper.writeValueAsString(body);
            String url   = culqiProperties.getBaseUrl() + "/customers/" + customerId;

            HttpRequest request = HttpRequest.newBuilder()
                    .uri(URI.create(url))
                    .header("Authorization", "Bearer " + culqiProperties.getSecretKey())
                    .header("Content-Type",  "application/json")
                    .method("PATCH", HttpRequest.BodyPublishers.ofString(json))
                    .build();

            HttpResponse<String> response = httpClient.send(request, HttpResponse.BodyHandlers.ofString());
            log.info("📥 Culqi PATCH /customers/{}: status={}", customerId, response.statusCode());

            if (response.statusCode() >= 400) {
                log.warn("⚠️ No se pudo actualizar cliente en Culqi (id={}): {}", customerId, response.body());
            } else {
                log.info("✅ Culqi cliente actualizado: {}", customerId);
            }
        } catch (Exception e) {
            // No bloquear el flujo si falla el update, solo loguear
            log.warn("⚠️ Error actualizando cliente Culqi (id={}): {}", customerId, e.getMessage());
        }
    }

    /**
     * Busca un cliente en Culqi por email.
     * GET /customers?email={email} devuelve { "data": [...] } o { "items": [...] }
     * Retorna el ID del primer cliente encontrado, o null si no existe.
     */
    @SuppressWarnings("unchecked")
    private String buscarClientePorEmail(String email) {
        try {
            String url = culqiProperties.getBaseUrl() + "/customers?email=" + email;
            HttpRequest request = HttpRequest.newBuilder()
                    .uri(URI.create(url))
                    .header("Authorization", "Bearer " + culqiProperties.getSecretKey())
                    .header("Content-Type",  "application/json")
                    .GET()
                    .build();

            HttpResponse<String> response = httpClient.send(request, HttpResponse.BodyHandlers.ofString());
            log.info("📥 Culqi GET /customers?email: status={}, body={}", response.statusCode(), response.body());

            if (response.statusCode() != 200) return null;

            Map<String, Object> responseMap = objectMapper.readValue(
                    response.body(), new TypeReference<Map<String, Object>>() {});

            // Culqi puede devolver "data" o "items" según la versión
            java.util.List<Map<String, Object>> items = null;
            if (responseMap.containsKey("data")) {
                items = (java.util.List<Map<String, Object>>) responseMap.get("data");
            } else if (responseMap.containsKey("items")) {
                items = (java.util.List<Map<String, Object>>) responseMap.get("items");
            }

            if (items != null && !items.isEmpty()) {
                return (String) items.get(0).get("id");
            }
            return null;
        } catch (Exception e) {
            log.warn("⚠️ No se pudo buscar cliente por email en Culqi: {}", e.getMessage());
            return null; // Si falla la búsqueda, intentamos crear normalmente
        }
    }

    // ── Tarjetas ─────────────────────────────────────────────────────────────

    @Override
    public String crearTarjeta(String customerId, String tokenId) {
        Map<String, Object> body = new HashMap<>();
        body.put("customer_id", customerId);
        body.put("token_id",    tokenId);

        Map<String, Object> response = post("/cards", body, false);
        String cardId = (String) response.get("id");
        log.info("✅ Culqi tarjeta registrada: {}", cardId);
        return cardId;
    }

    // ── Suscripciones ─────────────────────────────────────────────────────────

    @Override
    public String crearSuscripcion(String cardId, String planId) {
        Map<String, Object> body = new HashMap<>();
        body.put("card_id", cardId);
        body.put("plan_id", planId);
        body.put("tyc",     true);   // términos y condiciones requerido por Culqi

        // Endpoint correcto según docs oficiales: POST /recurrent/subscriptions/create
        // Body: plain JSON (el cifrado AES+RSA solo aplica al PATCH /actualizar)
        Map<String, Object> response = post("/recurrent/subscriptions/create", body, false);
        String subscriptionId = (String) response.get("id");
        log.info("✅ Culqi suscripción creada: {}", subscriptionId);
        return subscriptionId;
    }

    @Override
    public void cancelarSuscripcion(String subscriptionId) {
        deleteWithRsaAuth("/recurrent/subscriptions/" + subscriptionId);
        log.info("✅ Culqi suscripción cancelada: {}", subscriptionId);
    }

    // ── Planes ───────────────────────────────────────────────────────────────

    @Override
    public String crearPlan(String nombre, long montoCentavos) {
        Map<String, Object> body = new HashMap<>();
        body.put("name",           nombre);
        body.put("amount",         montoCentavos);
        body.put("currency_code",  "PEN");
        body.put("interval",       "month");
        body.put("interval_count", 1);
        body.put("limit",          0);

        Map<String, Object> response = post("/recurrent/plans", body, false);
        String planId = (String) response.get("id");
        log.info("✅ Culqi plan creado: {}", planId);
        return planId;
    }

    // ── HTTP helpers ──────────────────────────────────────────────────────────

    private Map<String, Object> post(String path, Map<String, Object> body, boolean useRsa) {
        try {
            String json  = objectMapper.writeValueAsString(body);
            String url   = culqiProperties.getBaseUrl() + path;
            // useRsa flag ya no se usa (la autenticación cifrada la maneja postWithRsaBody)
            String token = culqiProperties.getSecretKey();

            HttpRequest request = HttpRequest.newBuilder()
                    .uri(URI.create(url))
                    .header("Authorization", "Bearer " + token)
                    .header("Content-Type",  "application/json")
                    .POST(HttpRequest.BodyPublishers.ofString(json))
                    .build();

            HttpResponse<String> response = httpClient.send(request, HttpResponse.BodyHandlers.ofString());
            String responseBody = response.body();
            log.info("📥 Culqi POST {}: status={}, body={}", path, response.statusCode(), responseBody);

            Map<String, Object> responseMap = objectMapper.readValue(
                    responseBody, new TypeReference<Map<String, Object>>() {});

            if (response.statusCode() < 200 || response.statusCode() >= 300) {
                String mensaje = extractMensajeError(responseMap);
                throw new BadRequestException("Error Culqi [" + path + "]: " + mensaje);
            }
            return responseMap;

        } catch (BadRequestException e) {
            throw e;
        } catch (Exception e) {
            log.error("❌ Error en Culqi POST {}: {}", path, e.getMessage(), e);
            throw new BadRequestException("Error de comunicación con Culqi: " + e.getMessage());
        }
    }

    private void delete(String path, boolean useRsa) {
        try {
            String url   = culqiProperties.getBaseUrl() + path;
            String token = culqiProperties.getSecretKey();

            HttpRequest request = HttpRequest.newBuilder()
                    .uri(URI.create(url))
                    .header("Authorization", "Bearer " + token)
                    .header("Content-Type",  "application/json")
                    .DELETE()
                    .build();

            HttpResponse<String> response = httpClient.send(request, HttpResponse.BodyHandlers.ofString());
            log.info("📥 Culqi DELETE {}: status={}", path, response.statusCode());

            if (response.statusCode() >= 400) {
                Map<String, Object> responseMap = objectMapper.readValue(
                        response.body(), new TypeReference<Map<String, Object>>() {});
                throw new BadRequestException("Error cancelando en Culqi: " + extractMensajeError(responseMap));
            }
        } catch (BadRequestException e) {
            throw e;
        } catch (Exception e) {
            log.error("❌ Error en Culqi DELETE {}: {}", path, e.getMessage(), e);
            throw new BadRequestException("Error de comunicación con Culqi: " + e.getMessage());
        }
    }

    /**
     * POST con cifrado híbrido AES-GCM + RSA-OAEP (requerido por Culqi Recurrencia).
     * Modelo de seguridad:
     *   - Header Authorization: Bearer {sk_test_xxx}   (plain secret key)
     *   - Header x-culqi-rsa-id: {rsaId}               (UUID del par de llaves en Culqi)
     *   - Body: { "encrypted_data": "...", "encrypted_key": "...", "encrypted_iv": "..." }
     */
    private Map<String, Object> postWithRsaBody(String path, Map<String, Object> body) {
        try {
            String rsaId = culqiProperties.getRsaId();
            if (rsaId == null || rsaId.isBlank()) {
                throw new BadRequestException(
                    "culqi.rsa-id no está configurado. " +
                    "Ve a Culqi Panel → Desarrollo → RSA Keys, copia el UUID de la columna ID " +
                    "y agrégalo como CULQI_RSA_ID en tu .env / application-dev.yml.");
            }

            String json          = objectMapper.writeValueAsString(body);
            String encryptedBody = encryptAesGcmRsaOaep(json);
            String url           = culqiProperties.getBaseUrl() + path;

            // Determinar entorno por prefijo de la secret key
            String secretKey = culqiProperties.getSecretKey();
            String env = secretKey != null && secretKey.startsWith("sk_live_") ? "live" : "test";

            log.info("🔐 [Culqi AES+RSA] Enviando body cifrado a {} (rsaId={}, env={})", path, rsaId, env);

            HttpRequest request = HttpRequest.newBuilder()
                    .uri(URI.create(url))
                    .header("Authorization",          "Bearer " + secretKey)
                    .header("Content-Type",           "application/json")
                    .header("x-culqi-rsa-id",         rsaId)
                    .header("x-culqi-env",            env)
                    .header("x-api-version",          "2")
                    .header("x-culqi-client",         "culqi-java")
                    .header("x-culqi-client-version", "2.0.4")
                    .POST(HttpRequest.BodyPublishers.ofString(encryptedBody))
                    .build();

            HttpResponse<String> response = httpClient.send(request, HttpResponse.BodyHandlers.ofString());
            String responseBody = response.body();
            log.info("📥 Culqi POST (AES+RSA) {}: status={}, body={}", path, response.statusCode(), responseBody);

            Map<String, Object> responseMap = objectMapper.readValue(
                    responseBody, new TypeReference<Map<String, Object>>() {});

            if (response.statusCode() < 200 || response.statusCode() >= 300) {
                throw new BadRequestException("Error Culqi [" + path + "]: " + extractMensajeError(responseMap));
            }
            return responseMap;

        } catch (BadRequestException e) {
            throw e;
        } catch (Exception e) {
            log.error("❌ Error en Culqi POST AES+RSA {}: {}", path, e.getMessage(), e);
            throw new BadRequestException("Error de comunicación con Culqi: " + e.getMessage());
        }
    }

    /**
     * DELETE con plain SK (cancelar suscripción no requiere cifrado).
     */
    private void deleteWithRsaAuth(String path) {
        delete(path, false);
    }

    /**
     * Cifrado híbrido AES-256-GCM + RSA-OAEP usado por el SDK oficial de Culqi.
     *
     * Proceso:
     *   1. Genera clave AES-256 aleatoria
     *   2. Genera IV GCM de 12 bytes aleatorio
     *   3. Cifra el payload con AES/GCM/NoPadding y elimina los últimos 16 bytes (tag de autenticación)
     *   4. Cifra la clave AES con RSA/ECB/OAEPWithSHA-256AndMGF1Padding → encrypted_key
     *   5. Cifra el IV con el mismo esquema RSA → encrypted_iv
     *
     * Retorna JSON: { "encrypted_data": "...", "encrypted_key": "...", "encrypted_iv": "..." }
     */
    private String encryptAesGcmRsaOaep(String plaintext) throws Exception {
        String rsaPem = culqiProperties.getRsaPublicKey();
        if (rsaPem == null || rsaPem.isBlank()) {
            throw new BadRequestException(
                "culqi.rsa-public-key no está configurada. " +
                "Ve a Culqi Panel → Desarrollo → RSA Keys.");
        }

        // ── 1. Generar clave AES-256 ─────────────────────────────────────
        KeyGenerator keyGen = KeyGenerator.getInstance("AES");
        keyGen.init(256, new SecureRandom());
        SecretKey aesKey = keyGen.generateKey();

        // ── 2. Generar IV GCM de 12 bytes ────────────────────────────────
        byte[] ivBytes = new byte[12];
        new SecureRandom().nextBytes(ivBytes);
        GCMParameterSpec gcmSpec = new GCMParameterSpec(128, ivBytes);

        // ── 3. Cifrar payload con AES/GCM/NoPadding ──────────────────────
        Cipher aesCipher = Cipher.getInstance("AES/GCM/NoPadding");
        aesCipher.init(Cipher.ENCRYPT_MODE, aesKey, gcmSpec);
        byte[] rawCipher = aesCipher.doFinal(plaintext.getBytes(StandardCharsets.UTF_8));
        // Eliminar últimos 16 bytes (GCM auth tag) — comportamiento del SDK oficial de Culqi
        byte[] cipherWithoutTag = Arrays.copyOfRange(rawCipher, 0, rawCipher.length - 16);
        String encryptedData = Base64.getEncoder().encodeToString(cipherWithoutTag);

        // ── 4+5. Cifrar clave e IV con RSA-OAEP ──────────────────────────
        PublicKey rsaPublicKey = loadRsaPublicKey(rsaPem);
        OAEPParameterSpec oaepSpec = new OAEPParameterSpec(
                "SHA-256", "MGF1", new MGF1ParameterSpec("SHA-256"), PSource.PSpecified.DEFAULT);

        Cipher rsaCipher = Cipher.getInstance("RSA/ECB/OAEPWithSHA-256AndMGF1Padding");
        rsaCipher.init(Cipher.ENCRYPT_MODE, rsaPublicKey, oaepSpec);
        String encryptedKey = Base64.getEncoder().encodeToString(rsaCipher.doFinal(aesKey.getEncoded()));

        rsaCipher.init(Cipher.ENCRYPT_MODE, rsaPublicKey, oaepSpec);
        String encryptedIv  = Base64.getEncoder().encodeToString(rsaCipher.doFinal(ivBytes));

        // ── 6. Armar JSON de respuesta ────────────────────────────────────
        Map<String, String> result = new HashMap<>();
        result.put("encrypted_data", encryptedData);
        result.put("encrypted_key",  encryptedKey);
        result.put("encrypted_iv",   encryptedIv);
        return objectMapper.writeValueAsString(result);
    }

    /**
     * Carga una llave pública RSA desde formato PEM (X.509 / PKCS#8).
     */
    private PublicKey loadRsaPublicKey(String rsaPem) throws Exception {
        String cleanPem = rsaPem
                .replace("-----BEGIN PUBLIC KEY-----", "")
                .replace("-----END PUBLIC KEY-----",   "")
                .replaceAll("[\\r\\n\\s]", "");
        byte[] keyBytes = Base64.getDecoder().decode(cleanPem);
        return KeyFactory.getInstance("RSA").generatePublic(new X509EncodedKeySpec(keyBytes));
    }

    private String extractMensajeError(Map<String, Object> response) {
        if (response.containsKey("merchant_message")) return (String) response.get("merchant_message");
        if (response.containsKey("user_message"))     return (String) response.get("user_message");
        if (response.containsKey("message"))          return (String) response.get("message");
        return response.toString();
    }
}
