package com.stockflow.config.properties;

import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.context.annotation.Configuration;

import java.math.BigDecimal;

@Data
@Configuration
@ConfigurationProperties(prefix = "culqi")
public class CulqiProperties {

    private String secretKey;
    private String publicKey;
    private String baseUrl       = "https://api.culqi.com/v2";
    private String secureBaseUrl = "https://secure.culqi.com/v2";

    /** ID del plan Básico en Culqi (POST /api/culqi/admin/crear-plan) */
    private String planIdBasico;

    /** Precio mensual plan Básico en soles */
    private BigDecimal precioBasico = new BigDecimal("89.00");

    /** ID del plan Pro en Culqi (POST /api/culqi/admin/crear-plan-pro) */
    private String planIdPro;

    /** Precio mensual plan Pro en soles */
    private BigDecimal precioPro = new BigDecimal("169.00");

    /**
     * Llave pública RSA de Culqi (Desarrollo → RSA Keys en el panel).
     * Requerida para endpoints de Recurrencia (subscriptions).
     * Formato PEM completo incluyendo cabeceras BEGIN/END PUBLIC KEY.
     */
    private String rsaPublicKey;

    /**
     * ID de la llave RSA en Culqi (Desarrollo → RSA Keys → columna "ID").
     * Es un UUID como "5243bad7-1d88-49c0-9699-f8ae156da58f".
     * Se envía en el header x-culqi-rsa-id en las peticiones cifradas.
     */
    private String rsaId;
}
