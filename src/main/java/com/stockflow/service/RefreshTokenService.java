package com.stockflow.service;

import com.stockflow.entity.RefreshToken;
import com.stockflow.entity.Usuario;

public interface RefreshTokenService {

    /** Crea un refresh token que incluye el tenantId activo (multi-tenant). */
    RefreshToken crearRefreshToken(Usuario usuario, String tenantId);

    /** @deprecated Use crearRefreshToken(usuario, tenantId) */
    @Deprecated
    RefreshToken crearRefreshToken(Usuario usuario);

    RefreshToken validarRefreshToken(String token);

    void revocarRefreshToken(String token);

    void revocarTodosLosTokensDelUsuario(Long usuarioId);

    void limpiarTokensExpirados();
}
