package com.stockflow.controller;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.time.LocalDateTime;
import java.util.HashMap;
import java.util.Map;

@RestController
@RequestMapping("/info")
public class InfoController {

    @Value("${spring.profiles.active:default}")
    private String activeProfile;

    @Value("${spring.application.name}")
    private String appName;

    @Value("${server.port}")
    private String serverPort;

    @Value("${spring.datasource.url}")
    private String datasourceUrl;

    @GetMapping
    public ResponseEntity<Map<String, Object>> getInfo() {
        Map<String, Object> info = new HashMap<>();
        info.put("app", appName);
        info.put("profile", activeProfile);
        info.put("timestamp", LocalDateTime.now());
        info.put("version", "1.0.0");
        info.put("port", serverPort);
        info.put("database", ocultarPassword(datasourceUrl));
        return ResponseEntity.ok(info);
    }

    private String ocultarPassword(String url) {
        // Ocultar credenciales si están en la URL
        return url.replaceAll("password=[^&]*", "password=****");
    }

    @GetMapping("/health")
    public ResponseEntity<Map<String, String>> health() {
        Map<String, String> health = new HashMap<>();
        health.put("status", "UP");
        health.put("profile", activeProfile);
        health.put("timestamp", LocalDateTime.now().toString());
        return ResponseEntity.ok(health);
    }
}