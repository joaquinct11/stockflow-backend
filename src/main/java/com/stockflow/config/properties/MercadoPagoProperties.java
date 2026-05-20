package com.stockflow.config.properties;

import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.context.annotation.Configuration;

@Data
@Configuration
@ConfigurationProperties(prefix = "mercadopago")
public class MercadoPagoProperties {

    private String accessToken;
    private String checkoutBaseUrl = "https://api.mercadopago.com";
    private String currencyId = "PEN";
    private String successUrl;
    private String failureUrl;
    private String pendingUrl;
    private String notificationUrl;
    private String webhookSecret;
    private String testPayerEmail;

    /** Precio del plan en la moneda configurada (currencyId). */
    private java.math.BigDecimal precioBasico = new java.math.BigDecimal("150.00");
}
