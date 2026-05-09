package com.stockflow.service.impl;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.stockflow.config.properties.MercadoPagoProperties;
import com.stockflow.exception.BadRequestException;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.Spy;
import org.mockito.junit.jupiter.MockitoExtension;

import java.math.BigDecimal;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.assertj.core.api.Assertions.assertThatNoException;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class MercadoPagoServiceImplTest {

    @Mock
    private MercadoPagoProperties mercadoPagoProperties;

    @Spy
    private ObjectMapper objectMapper;

    @InjectMocks
    private MercadoPagoServiceImpl service;

    // ── validarBackUrl ────────────────────────────────────────────────────────

    @Test
    void validarBackUrl_successUrlNull_lanzaExcepcion() {
        when(mercadoPagoProperties.getSuccessUrl()).thenReturn(null);

        assertThatThrownBy(() -> service.validarBackUrl())
                .isInstanceOf(BadRequestException.class)
                .hasMessageContaining("success-url");
    }

    @Test
    void validarBackUrl_successUrlBlank_lanzaExcepcion() {
        when(mercadoPagoProperties.getSuccessUrl()).thenReturn("   ");

        assertThatThrownBy(() -> service.validarBackUrl())
                .isInstanceOf(BadRequestException.class)
                .hasMessageContaining("success-url");
    }

    @Test
    void validarBackUrl_successUrlSinEsquema_lanzaExcepcion() {
        when(mercadoPagoProperties.getSuccessUrl()).thenReturn("stockflow.pe/checkout/success");

        assertThatThrownBy(() -> service.validarBackUrl())
                .isInstanceOf(BadRequestException.class)
                .hasMessageContaining("http");
    }

    @Test
    void validarBackUrl_successUrlConFtp_lanzaExcepcion() {
        when(mercadoPagoProperties.getSuccessUrl()).thenReturn("ftp://stockflow.pe/checkout/success");

        assertThatThrownBy(() -> service.validarBackUrl())
                .isInstanceOf(BadRequestException.class)
                .hasMessageContaining("http");
    }

    @Test
    void validarBackUrl_successUrlConHttps_noLanzaExcepcion() {
        when(mercadoPagoProperties.getSuccessUrl()).thenReturn("https://stockflow.pe/checkout/success");

        assertThatNoException().isThrownBy(() -> service.validarBackUrl());
    }

    @Test
    void validarBackUrl_successUrlConHttp_noLanzaExcepcion() {
        when(mercadoPagoProperties.getSuccessUrl()).thenReturn("http://localhost:3000/checkout/success");

        assertThatNoException().isThrownBy(() -> service.validarBackUrl());
    }

    // ── crearPreapproval: validación previa al HTTP ───────────────────────────

    @Test
    void crearPreapproval_accessTokenNulo_lanzaExcepcion() {
        when(mercadoPagoProperties.getAccessToken()).thenReturn(null);

        assertThatThrownBy(() -> service.crearPreapproval("PRO", BigDecimal.TEN, "tenant:1", "user@test.com", null, null))
                .isInstanceOf(BadRequestException.class)
                .hasMessageContaining("access-token");
    }

    @Test
    void crearPreapproval_notificationUrlNula_lanzaExcepcion() {
        when(mercadoPagoProperties.getAccessToken()).thenReturn("TEST-valid-token");
        when(mercadoPagoProperties.getNotificationUrl()).thenReturn(null);

        assertThatThrownBy(() -> service.crearPreapproval("PRO", BigDecimal.TEN, "tenant:1", "user@test.com", null, null))
                .isInstanceOf(BadRequestException.class)
                .hasMessageContaining("notification-url");
    }

    @Test
    void crearPreapproval_successUrlNula_lanzaExcepcion() {
        when(mercadoPagoProperties.getAccessToken()).thenReturn("TEST-valid-token");
        when(mercadoPagoProperties.getNotificationUrl()).thenReturn("https://example.com/webhook");
        when(mercadoPagoProperties.getSuccessUrl()).thenReturn(null);

        assertThatThrownBy(() -> service.crearPreapproval("PRO", BigDecimal.TEN, "tenant:1", "user@test.com", null, null))
                .isInstanceOf(BadRequestException.class)
                .hasMessageContaining("success-url");
    }

    @Test
    void crearPreapproval_successUrlSinHttps_lanzaExcepcion() {
        when(mercadoPagoProperties.getAccessToken()).thenReturn("TEST-valid-token");
        when(mercadoPagoProperties.getNotificationUrl()).thenReturn("https://example.com/webhook");
        when(mercadoPagoProperties.getSuccessUrl()).thenReturn("stockflow.pe/checkout/success");

        assertThatThrownBy(() -> service.crearPreapproval("PRO", BigDecimal.TEN, "tenant:1", "user@test.com", null, null))
                .isInstanceOf(BadRequestException.class)
                .hasMessageContaining("http");
    }

    // ── buildPreapprovalPayload: TEST vs PROD email handling ──────────────────

    @Test
    void buildPreapprovalPayload_tokenTest_sinTestPayerEmail_lanzaExcepcion() {
        when(mercadoPagoProperties.getCurrencyId()).thenReturn("PEN");
        when(mercadoPagoProperties.getNotificationUrl()).thenReturn("https://example.com/webhook");
        when(mercadoPagoProperties.getSuccessUrl()).thenReturn("https://example.com/success");
        when(mercadoPagoProperties.getTestPayerEmail()).thenReturn(null);

        assertThatThrownBy(() -> service.buildPreapprovalPayload(
                "PRO", BigDecimal.TEN, "tenant:1", "user@real.com",
                null, null, true))
                .isInstanceOf(BadRequestException.class)
                .hasMessageContaining("MERCADOPAGO_TEST_PAYER_EMAIL");
    }

    @Test
    void buildPreapprovalPayload_tokenTest_testPayerEmailBlank_lanzaExcepcion() {
        when(mercadoPagoProperties.getCurrencyId()).thenReturn("PEN");
        when(mercadoPagoProperties.getNotificationUrl()).thenReturn("https://example.com/webhook");
        when(mercadoPagoProperties.getSuccessUrl()).thenReturn("https://example.com/success");
        when(mercadoPagoProperties.getTestPayerEmail()).thenReturn("   ");

        assertThatThrownBy(() -> service.buildPreapprovalPayload(
                "PRO", BigDecimal.TEN, "tenant:1", "user@real.com",
                null, null, true))
                .isInstanceOf(BadRequestException.class)
                .hasMessageContaining("MERCADOPAGO_TEST_PAYER_EMAIL");
    }

    @Test
    void buildPreapprovalPayload_tokenTest_testPayerEmailSinArroba_lanzaExcepcion() {
        when(mercadoPagoProperties.getCurrencyId()).thenReturn("PEN");
        when(mercadoPagoProperties.getNotificationUrl()).thenReturn("https://example.com/webhook");
        when(mercadoPagoProperties.getSuccessUrl()).thenReturn("https://example.com/success");
        when(mercadoPagoProperties.getTestPayerEmail()).thenReturn("notanemail");

        assertThatThrownBy(() -> service.buildPreapprovalPayload(
                "PRO", BigDecimal.TEN, "tenant:1", "user@real.com",
                null, null, true))
                .isInstanceOf(BadRequestException.class)
                .hasMessageContaining("MERCADOPAGO_TEST_PAYER_EMAIL");
    }

    @Test
    void buildPreapprovalPayload_tokenTest_conTestPayerEmail_incluyePayerEmail() {
        when(mercadoPagoProperties.getCurrencyId()).thenReturn("PEN");
        when(mercadoPagoProperties.getNotificationUrl()).thenReturn("https://example.com/webhook");
        when(mercadoPagoProperties.getSuccessUrl()).thenReturn("https://example.com/success");
        when(mercadoPagoProperties.getTestPayerEmail()).thenReturn("testbuyer@testuser.com");

        Map<String, Object> payload = service.buildPreapprovalPayload(
                "PRO", BigDecimal.TEN, "tenant:1", "user@real.com",
                null, null, true);

        assertThat(payload).containsEntry("payer_email", "testbuyer@testuser.com");
        assertThat(payload).doesNotContainKey("payer");
    }

    @Test
    void buildPreapprovalPayload_tokenTest_conIdentificacion_incluyeTestEmailEnPayer() {
        when(mercadoPagoProperties.getCurrencyId()).thenReturn("PEN");
        when(mercadoPagoProperties.getNotificationUrl()).thenReturn("https://example.com/webhook");
        when(mercadoPagoProperties.getSuccessUrl()).thenReturn("https://example.com/success");
        when(mercadoPagoProperties.getTestPayerEmail()).thenReturn("testbuyer@testuser.com");

        Map<String, Object> payload = service.buildPreapprovalPayload(
                "PRO", BigDecimal.TEN, "tenant:1", "user@real.com",
                "DNI", "12345678", true);

        assertThat(payload).containsEntry("payer_email", "testbuyer@testuser.com");
        @SuppressWarnings("unchecked")
        Map<String, Object> payer = (Map<String, Object>) payload.get("payer");
        assertThat(payer).isNotNull();
        assertThat(payer).containsEntry("email", "testbuyer@testuser.com");
        assertThat(payer).containsKey("identification");
    }

    @Test
    void buildPreapprovalPayload_tokenProd_incluyePayerEmail() {
        when(mercadoPagoProperties.getCurrencyId()).thenReturn("PEN");
        when(mercadoPagoProperties.getNotificationUrl()).thenReturn("https://example.com/webhook");
        when(mercadoPagoProperties.getSuccessUrl()).thenReturn("https://example.com/success");

        Map<String, Object> payload = service.buildPreapprovalPayload(
                "PRO", BigDecimal.TEN, "tenant:1", "user@real.com",
                null, null, false);

        assertThat(payload).containsEntry("payer_email", "user@real.com");
    }

    @Test
    void buildPreapprovalPayload_tokenProd_conIdentificacion_incluyeEmailEnPayer() {
        when(mercadoPagoProperties.getCurrencyId()).thenReturn("PEN");
        when(mercadoPagoProperties.getNotificationUrl()).thenReturn("https://example.com/webhook");
        when(mercadoPagoProperties.getSuccessUrl()).thenReturn("https://example.com/success");

        Map<String, Object> payload = service.buildPreapprovalPayload(
                "PRO", BigDecimal.TEN, "tenant:1", "user@real.com",
                "DNI", "12345678", false);

        assertThat(payload).containsEntry("payer_email", "user@real.com");
        @SuppressWarnings("unchecked")
        Map<String, Object> payer = (Map<String, Object>) payload.get("payer");
        assertThat(payer).isNotNull();
        assertThat(payer).containsEntry("email", "user@real.com");
        assertThat(payer).containsKey("identification");
    }
}
