package com.stockflow.service;

import com.stockflow.dto.JwtResponseDTO;
import com.stockflow.dto.LoginDTO;
import com.stockflow.dto.UsuarioDTO;

public interface AuthService {

    JwtResponseDTO login(LoginDTO loginDTO);

    JwtResponseDTO registroUsuario(UsuarioDTO usuarioDTO);

    void logout(String token);
}