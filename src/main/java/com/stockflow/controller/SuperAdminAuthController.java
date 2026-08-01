package com.stockflow.controller;

import com.stockflow.config.properties.SuperAdminProperties;
import com.stockflow.dto.superadmin.SuperAdminLoginRequestDTO;
import com.stockflow.dto.superadmin.SuperAdminTokenDTO;
import com.stockflow.exception.BadRequestException;
import com.stockflow.util.JwtUtil;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

@Slf4j
@RestController
@RequestMapping("/superadmin/auth")
@RequiredArgsConstructor
public class SuperAdminAuthController {

    private final SuperAdminProperties superAdminProperties;
    private final JwtUtil              jwtUtil;

    @PostMapping("/login")
    public ResponseEntity<SuperAdminTokenDTO> login(@RequestBody SuperAdminLoginRequestDTO req) {
        if (!superAdminProperties.getUsername().equals(req.getUsername()) ||
            !superAdminProperties.getPassword().equals(req.getPassword())) {
            log.warn("🚫 Intento de login SuperAdmin fallido para: {}", req.getUsername());
            throw new BadRequestException("Credenciales incorrectas");
        }
        String token = jwtUtil.generateSuperAdminToken(req.getUsername());
        log.info("✅ SuperAdmin autenticado: {}", req.getUsername());
        return ResponseEntity.ok(new SuperAdminTokenDTO(token, req.getUsername()));
    }
}
