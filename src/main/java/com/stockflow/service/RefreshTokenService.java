package com.stockflow.service;

import com.stockflow.entity.RefreshToken;
import com.stockflow.entity.Usuario;

public interface RefreshTokenService {

    RefreshToken crearRefreshToken(Usuario usuario);

    RefreshToken validarRefreshToken(String token);

    void revocarRefreshToken(String token);

    void revocarTodosLosTokensDelUsuario(Long usuarioId);

    void limpiarTokensExpirados();
}
