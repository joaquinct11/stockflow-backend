package com.stockflow.service;

import com.stockflow.dto.CambiarPasswordDTO;
import com.stockflow.dto.ForgotPasswordDTO;
import com.stockflow.dto.JwtResponseDTO;
import com.stockflow.dto.LoginDTO;
import com.stockflow.dto.RegistrationRequestDTO;
import com.stockflow.dto.ResetPasswordDTO;
import com.stockflow.dto.UsuarioProfileDTO;

public interface AuthService {
    JwtResponseDTO login(LoginDTO loginDTO);
    JwtResponseDTO registrar(RegistrationRequestDTO request);
    JwtResponseDTO refresh(String refreshToken);
    void logout(String refreshToken);

    // ✅ NUEVOS MÉTODOS
    UsuarioProfileDTO obtenerPerfil(Long usuarioId);
    void cambiarContraseña(Long usuarioId, CambiarPasswordDTO dto);
    void solicitarRecuperacionContraseña(ForgotPasswordDTO dto);
    void resetearContraseña(ResetPasswordDTO dto);
}