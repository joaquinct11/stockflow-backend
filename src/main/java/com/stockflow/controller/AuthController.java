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
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import com.stockflow.dto.CambiarPasswordDTO;
import com.stockflow.dto.ForgotPasswordDTO;
import com.stockflow.dto.ResetPasswordDTO;
import com.stockflow.dto.UsuarioProfileDTO;
import com.stockflow.util.TenantContext;

import java.util.Map;

@RestController
@RequestMapping("/auth")
@RequiredArgsConstructor
@Tag(name = "Autenticación", description = "Endpoints para autenticación y registro")
public class AuthController {

    private final AuthService authService;

    @PostMapping("/login")
    @Operation(summary = "Login", description = "Autentica un usuario y devuelve access token y refresh token")
    public ResponseEntity<JwtResponseDTO> login(@Valid @RequestBody LoginDTO loginDTO) {
        JwtResponseDTO response = authService.login(loginDTO);
        return ResponseEntity.ok(response);
    }

    @PostMapping("/register")
    @Operation(summary = "Registro completo",
            description = "Registra una nueva farmacia (tenant) con su usuario admin y suscripción")
    public ResponseEntity<JwtResponseDTO> register(@Valid @RequestBody RegistrationRequestDTO request) {
        JwtResponseDTO response = authService.registrar(request);
        return ResponseEntity.status(HttpStatus.CREATED).body(response);
    }

    @PostMapping("/refresh")
    @Operation(summary = "Renovar tokens", description = "Genera nuevos access y refresh tokens usando un refresh token válido")
    public ResponseEntity<JwtResponseDTO> refresh(@Valid @RequestBody RefreshTokenRequestDTO request) {
        JwtResponseDTO response = authService.refresh(request.getRefreshToken());
        return ResponseEntity.ok(response);
    }

    @PostMapping("/logout")
    @Operation(summary = "Logout", description = "Revoca el refresh token del usuario")
    public ResponseEntity<Map<String, String>> logout(@Valid @RequestBody RefreshTokenRequestDTO request) {
        authService.logout(request.getRefreshToken());
        return ResponseEntity.ok(Map.of("mensaje", "Sesión cerrada exitosamente"));
    }

    @GetMapping("/me")
    @Operation(summary = "Obtener perfil del usuario actual", description = "Retorna los datos del usuario autenticado")
    public ResponseEntity<UsuarioProfileDTO> obtenerPerfil() {
        Long usuarioId = TenantContext.getCurrentUserId();
        UsuarioProfileDTO profile = authService.obtenerPerfil(usuarioId);
        return ResponseEntity.ok(profile);
    }

    @PostMapping("/cambiar-contraseña")
    @Operation(summary = "Cambiar contraseña", description = "Cambia la contraseña del usuario actual")
    public ResponseEntity<Map<String, String>> cambiarContraseña(
            @Valid @RequestBody CambiarPasswordDTO dto) {
        Long usuarioId = TenantContext.getCurrentUserId();
        authService.cambiarContraseña(usuarioId, dto);
        return ResponseEntity.ok(Map.of("mensaje", "Contraseña cambiada exitosamente"));
    }

    @PostMapping("/forgot-password")
    @Operation(summary = "Solicitar recuperación de contraseña", description = "Envía email con link para recuperar contraseña")
    public ResponseEntity<Map<String, String>> forgotPassword(
            @Valid @RequestBody ForgotPasswordDTO dto) {
        authService.solicitarRecuperacionContraseña(dto);
        return ResponseEntity.ok(Map.of("mensaje", "Email de recuperación enviado. Revisa tu bandeja de entrada"));
    }

    @PostMapping("/reset-password")
    @Operation(summary = "Resetear contraseña", description = "Cambia la contraseña usando el token de recuperación")
    public ResponseEntity<Map<String, String>> resetPassword(
            @Valid @RequestBody ResetPasswordDTO dto) {
        authService.resetearContraseña(dto);
        return ResponseEntity.ok(Map.of("mensaje", "Contraseña reseteada exitosamente"));
    }

    @PostMapping("/activate-account")
    @Operation(summary = "Activar cuenta de usuario nuevo",
               description = "Permite al usuario nuevo establecer su contraseña usando el token del email de bienvenida")
    public ResponseEntity<Map<String, String>> activarCuenta(
            @Valid @RequestBody ResetPasswordDTO dto) {
        authService.activarCuenta(dto);
        return ResponseEntity.ok(Map.of("mensaje", "Cuenta activada exitosamente. Ya puedes iniciar sesión."));
    }
}
