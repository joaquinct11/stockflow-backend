package com.stockflow.service;

import com.stockflow.config.properties.JwtProperties;
import com.stockflow.entity.RefreshToken;
import com.stockflow.entity.Usuario;
import com.stockflow.exception.UnauthorizedException;
import com.stockflow.repository.RefreshTokenRepository;
import com.stockflow.service.impl.RefreshTokenServiceImpl;
import com.stockflow.util.JwtUtil;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.LocalDateTime;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class RefreshTokenServiceTest {

    @Mock
    private RefreshTokenRepository refreshTokenRepository;

    @Mock
    private JwtProperties jwtProperties;

    @Mock
    private JwtUtil jwtUtil;

    @InjectMocks
    private RefreshTokenServiceImpl refreshTokenService;

    private Usuario usuario;
    private JwtProperties.Refresh refreshConfig;

    @BeforeEach
    void setUp() {
        usuario = Usuario.builder()
                .id(1L)
                .email("test@example.com")
                .nombre("Test User")
                .build();

        refreshConfig = new JwtProperties.Refresh();
        // 7 días en ms
        refreshConfig.setExpiration(604800000L);
    }

    @Test
    void crearRefreshToken_debeGuardarYRetornarToken() {
        String tokenString = "eyJhbGc.test.token";
        when(jwtUtil.generateRefreshToken(usuario.getId(), usuario.getEmail())).thenReturn(tokenString);
        when(jwtProperties.getRefresh()).thenReturn(refreshConfig);
        when(refreshTokenRepository.save(any(RefreshToken.class))).thenAnswer(inv -> inv.getArgument(0));

        RefreshToken result = refreshTokenService.crearRefreshToken(usuario);

        assertThat(result.getToken()).isEqualTo(tokenString);
        assertThat(result.getUsuario()).isEqualTo(usuario);
        assertThat(result.getRevocado()).isFalse();
        assertThat(result.getExpiracion()).isAfter(LocalDateTime.now());
        verify(refreshTokenRepository).save(any(RefreshToken.class));
    }

    @Test
    void validarRefreshToken_conTokenValido_debeRetornarToken() {
        String tokenString = "valid.token";
        RefreshToken refreshToken = RefreshToken.builder()
                .token(tokenString)
                .usuario(usuario)
                .expiracion(LocalDateTime.now().plusDays(7))
                .revocado(false)
                .build();

        when(refreshTokenRepository.findByToken(tokenString)).thenReturn(Optional.of(refreshToken));

        RefreshToken result = refreshTokenService.validarRefreshToken(tokenString);

        assertThat(result).isEqualTo(refreshToken);
    }

    @Test
    void validarRefreshToken_conTokenNoEncontrado_debeLanzarExcepcion() {
        when(refreshTokenRepository.findByToken("unknown")).thenReturn(Optional.empty());

        assertThatThrownBy(() -> refreshTokenService.validarRefreshToken("unknown"))
                .isInstanceOf(UnauthorizedException.class)
                .hasMessageContaining("no encontrado");
    }

    @Test
    void validarRefreshToken_conTokenRevocado_debeLanzarExcepcion() {
        String tokenString = "revoked.token";
        RefreshToken refreshToken = RefreshToken.builder()
                .token(tokenString)
                .usuario(usuario)
                .expiracion(LocalDateTime.now().plusDays(7))
                .revocado(true)
                .build();

        when(refreshTokenRepository.findByToken(tokenString)).thenReturn(Optional.of(refreshToken));

        assertThatThrownBy(() -> refreshTokenService.validarRefreshToken(tokenString))
                .isInstanceOf(UnauthorizedException.class)
                .hasMessageContaining("revocado");
    }

    @Test
    void validarRefreshToken_conTokenExpirado_debeLanzarExcepcion() {
        String tokenString = "expired.token";
        RefreshToken refreshToken = RefreshToken.builder()
                .token(tokenString)
                .usuario(usuario)
                .expiracion(LocalDateTime.now().minusDays(1))
                .revocado(false)
                .build();

        when(refreshTokenRepository.findByToken(tokenString)).thenReturn(Optional.of(refreshToken));

        assertThatThrownBy(() -> refreshTokenService.validarRefreshToken(tokenString))
                .isInstanceOf(UnauthorizedException.class)
                .hasMessageContaining("expirado");
    }

    @Test
    void revocarRefreshToken_debeInvocarRepositorio() {
        String tokenString = "some.token";

        refreshTokenService.revocarRefreshToken(tokenString);

        verify(refreshTokenRepository).revocarToken(tokenString);
    }

    @Test
    void revocarTodosLosTokensDelUsuario_debeEliminarPorUsuarioId() {
        Long usuarioId = 1L;

        refreshTokenService.revocarTodosLosTokensDelUsuario(usuarioId);

        verify(refreshTokenRepository).deleteByUsuarioId(usuarioId);
    }

    @Test
    void limpiarTokensExpirados_debeEliminarTokensAnterioresAHoy() {
        refreshTokenService.limpiarTokensExpirados();

        verify(refreshTokenRepository).deleteByExpiracionBefore(any(LocalDateTime.class));
    }
}
