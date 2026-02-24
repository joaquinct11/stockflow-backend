package com.stockflow.config.properties;

import jakarta.validation.constraints.NotBlank;
import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.context.annotation.Configuration;
import org.springframework.validation.annotation.Validated;

@Data
@Validated
@Configuration
@ConfigurationProperties(prefix = "jwt")
public class JwtProperties {

    @NotBlank(message = "JWT secret no puede estar vacío")
    private String secret;

    private Long expiration = 3600000L;  // 1 hora

    private Refresh refresh = new Refresh();

    @Data
    public static class Refresh {
        private Long expiration = 604800000L;  // 7 días
    }
}