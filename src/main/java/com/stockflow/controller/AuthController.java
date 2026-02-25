package com.stockflow.controller;

import com.stockflow.dto.JwtResponseDTO;
import com.stockflow.dto.LoginDTO;
import com.stockflow.dto.UsuarioDTO;
import com.stockflow.dto.RegistrationRequestDTO;
import com.stockflow.service.AuthService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

@Slf4j
@RestController
@RequestMapping("/auth")
@RequiredArgsConstructor
@Tag(name = "Autenticación", description = "Endpoints para autenticación y registro")
public class AuthController {

    private final AuthService authService;

    @PostMapping("/login")
    @Operation(summary = "Login", description = "Autentica un usuario y devuelve un JWT")
    public ResponseEntity<JwtResponseDTO> login(@Valid @RequestBody LoginDTO loginDTO) {
        log.info("🔐 Login: {}", loginDTO.getEmail());
        JwtResponseDTO response = authService.login(loginDTO);
        return ResponseEntity.ok(response);
    }

    @PostMapping("/registro")
    @Operation(summary = "Registro", description = "Registra un nuevo usuario en el sistema")
    public ResponseEntity<JwtResponseDTO> registro(@Valid @RequestBody UsuarioDTO usuarioDTO) {
        log.info("📝 Registro de usuario: {}", usuarioDTO.getEmail());
        JwtResponseDTO response = authService.registroUsuario(usuarioDTO);
        return ResponseEntity.status(HttpStatus.CREATED).body(response);
    }

    @PostMapping("/register")
    @Operation(summary = "Registro completo",
            description = "Registra una nueva farmacia (tenant) con su usuario admin y suscripción")
    public ResponseEntity<JwtResponseDTO> register(@Valid @RequestBody RegistrationRequestDTO request) {
        log.info("📝 Nuevo registro de farmacia: {}", request.getNombreFarmacia());
        JwtResponseDTO response = authService.registrar(request);
        return ResponseEntity.status(HttpStatus.CREATED).body(response);
    }

    @PostMapping("/logout")
    @Operation(summary = "Logout", description = "Cierra la sesión del usuario")
    public ResponseEntity<Void> logout(@RequestHeader("Authorization") String token) {
        log.info("👋 Logout del usuario");
        authService.logout(token.replace("Bearer ", ""));
        return ResponseEntity.noContent().build();
    }
}