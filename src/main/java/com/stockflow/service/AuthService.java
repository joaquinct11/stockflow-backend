package com.stockflow.service;

import com.stockflow.dto.JwtResponseDTO;
import com.stockflow.dto.LoginDTO;
import com.stockflow.dto.UsuarioDTO;
import com.stockflow.dto.RegistrationRequestDTO;

public interface AuthService {

    JwtResponseDTO login(LoginDTO loginDTO);

    JwtResponseDTO registrar(RegistrationRequestDTO request);

    JwtResponseDTO refresh(String refreshToken);

    void logout(String refreshToken);
}