package com.stockflow.config.properties;

import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

@Data
@Component
@ConfigurationProperties(prefix = "superadmin")
public class SuperAdminProperties {
    private String username;
    private String password;
}
