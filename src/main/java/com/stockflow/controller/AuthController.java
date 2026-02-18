package com.stockflow.controller;

import com.stockflow.dto.JwtResponseDTO;
import com.stockflow.dto.LoginDTO;
import com.stockflow.dto.UsuarioDTO;
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
        JwtResponseDTO response = authService.login(loginDTO);
        log.info("Token generado -> " + response.getToken());
        log.info("User -> " + response.getUsuarioId());
        return ResponseEntity.ok(response);
    }

    @PostMapping("/registro")
    @Operation(summary = "Registro", description = "Registra un nuevo usuario en el sistema")
    public ResponseEntity<JwtResponseDTO> registro(@Valid @RequestBody UsuarioDTO usuarioDTO) {
        JwtResponseDTO response = authService.registroUsuario(usuarioDTO);
        return ResponseEntity.status(HttpStatus.CREATED).body(response);
    }

    @PostMapping("/logout")
    @Operation(summary = "Logout", description = "Cierra la sesión del usuario")
    public ResponseEntity<Void> logout(@RequestHeader("Authorization") String token) {
        // Elimina "Bearer " del token
        String tokenSinBearer = token != null ? token.replace("Bearer ", "").trim() : "";

        if (!tokenSinBearer.isEmpty()) {
            authService.logout(tokenSinBearer);
        }

        return ResponseEntity.noContent().build();
    }
}