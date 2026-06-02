package com.stockflow.controller;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.time.LocalDateTime;
import java.util.Map;

@RestController
@RequestMapping("/info")
@PreAuthorize("hasRole('ADMIN')")
public class InfoController {

    @Value("${spring.profiles.active:default}")
    private String activeProfile;

    @Value("${spring.application.name}")
    private String appName;

    @GetMapping
    public ResponseEntity<Map<String, Object>> getInfo() {
        return ResponseEntity.ok(Map.of(
                "app",       appName,
                "profile",   activeProfile,
                "timestamp", LocalDateTime.now(),
                "version",   "1.0.0"
        ));
    }

    @GetMapping("/health")
    public ResponseEntity<Map<String, String>> health() {
        return ResponseEntity.ok(Map.of(
                "status",    "UP",
                "profile",   activeProfile,
                "timestamp", LocalDateTime.now().toString()
        ));
    }
}
