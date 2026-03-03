package com.stockflow.controller;

import com.stockflow.dto.JwtResponseDTO;
import com.stockflow.dto.LoginDTO;
import com.stockflow.dto.RegistrationRequestDTO;
import com.stockflow.dto.RefreshTokenRequestDTO;
import com.stockflow.service.AuthService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.Map;

@Slf4j
@RestController
@RequestMapping("/auth")
@RequiredArgsConstructor
@Tag(name = "Autenticación", description = "Endpoints para autenticación y registro")
public class AuthController {

    private final AuthService authService;

    @PostMapping("/login")
    @Operation(summary = "Login", description = "Autentica un usuario y devuelve access token y refresh token")
    public ResponseEntity<JwtResponseDTO> login(@Valid @RequestBody LoginDTO loginDTO) {
        log.info("🔐 Login: {}", loginDTO.getEmail());
        JwtResponseDTO response = authService.login(loginDTO);
        return ResponseEntity.ok(response);
    }

    @PostMapping("/register")
    @Operation(summary = "Registro completo",
            description = "Registra una nueva farmacia (tenant) con su usuario admin y suscripción")
    public ResponseEntity<JwtResponseDTO> register(@Valid @RequestBody RegistrationRequestDTO request) {
        log.info("📝 Nuevo registro de farmacia: {}", request.getNombreFarmacia());
        JwtResponseDTO response = authService.registrar(request);
        return ResponseEntity.status(HttpStatus.CREATED).body(response);
    }

    @PostMapping("/refresh")
    @Operation(summary = "Renovar tokens", description = "Genera nuevos access y refresh tokens usando un refresh token válido")
    public ResponseEntity<JwtResponseDTO> refresh(@Valid @RequestBody RefreshTokenRequestDTO request) {
        log.info("🔄 Renovando tokens");
        JwtResponseDTO response = authService.refresh(request.getRefreshToken());
        return ResponseEntity.ok(response);
    }

    @PostMapping("/logout")
    @Operation(summary = "Logout", description = "Revoca el refresh token del usuario")
    public ResponseEntity<Map<String, String>> logout(@Valid @RequestBody RefreshTokenRequestDTO request) {
        log.info("👋 Logout del usuario");
        authService.logout(request.getRefreshToken());
        return ResponseEntity.ok(Map.of("mensaje", "Sesión cerrada exitosamente"));
    }
}
