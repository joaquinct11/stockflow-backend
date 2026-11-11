package com.stockflow.service;

import com.stockflow.dto.CambiarPasswordDTO;
import com.stockflow.dto.CrearNegocioRequestDTO;
import com.stockflow.dto.ForgotPasswordDTO;
import com.stockflow.dto.JwtResponseDTO;
import com.stockflow.dto.LoginDTO;
import com.stockflow.dto.RegistrationRequestDTO;
import com.stockflow.dto.ResetPasswordDTO;
import com.stockflow.dto.SelectTenantRequestDTO;
import com.stockflow.dto.TenantInfoDTO;
import com.stockflow.dto.UsuarioProfileDTO;

import java.util.List;

public interface AuthService {
    JwtResponseDTO login(LoginDTO loginDTO);
    JwtResponseDTO registrar(RegistrationRequestDTO request);
    JwtResponseDTO refresh(String refreshToken);
    void logout(String refreshToken);

    UsuarioProfileDTO obtenerPerfil(Long usuarioId);
    void cambiarContraseña(Long usuarioId, CambiarPasswordDTO dto);
    void solicitarRecuperacionContraseña(ForgotPasswordDTO dto);
    void resetearContraseña(ResetPasswordDTO dto);
    void activarCuenta(ResetPasswordDTO dto);

    /** Multi-tenant: lista los tenants activos del usuario autenticado. */
    List<TenantInfoDTO> getTenants(Long usuarioId);

    /**
     * Multi-tenant: selecciona un tenant y emite access+refresh token normales.
     * Requiere selection token válido. Valida usuario_tenant.activo = true.
     */
    JwtResponseDTO selectTenant(Long usuarioId, SelectTenantRequestDTO dto);

    /**
     * Crea un nuevo negocio (tenant) para el usuario ya autenticado.
     * No crea un nuevo usuario — vincula el usuario actual como ADMIN del nuevo tenant.
     */
    TenantInfoDTO crearNegocio(Long usuarioId, CrearNegocioRequestDTO dto);
}