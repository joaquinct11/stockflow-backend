package com.stockflow.service.impl;

import com.stockflow.config.properties.JwtProperties;
import com.stockflow.entity.RefreshToken;
import com.stockflow.entity.Usuario;
import com.stockflow.exception.UnauthorizedException;
import com.stockflow.repository.RefreshTokenRepository;
import com.stockflow.service.RefreshTokenService;
import com.stockflow.util.JwtUtil;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;

@Slf4j
@Service
@RequiredArgsConstructor
public class RefreshTokenServiceImpl implements RefreshTokenService {

    private final RefreshTokenRepository refreshTokenRepository;
    private final JwtProperties jwtProperties;
    private final JwtUtil jwtUtil;

    @Override
    @Transactional
    public RefreshToken crearRefreshToken(Usuario usuario) {
        String token = jwtUtil.generateRefreshToken(usuario.getId(), usuario.getEmail());

        LocalDateTime expiracion = LocalDateTime.now()
                .plusSeconds(jwtProperties.getRefresh().getExpiration() / 1000);

        RefreshToken refreshToken = RefreshToken.builder()
                .token(token)
                .usuario(usuario)
                .expiracion(expiracion)
                .revocado(false)
                .build();

        log.debug("Creando refresh token para usuario: {}", usuario.getEmail());
        return refreshTokenRepository.save(refreshToken);
    }

    @Override
    public RefreshToken validarRefreshToken(String token) {
        RefreshToken refreshToken = refreshTokenRepository.findByToken(token)
                .orElseThrow(() -> new UnauthorizedException("Refresh token no encontrado"));

        if (refreshToken.getRevocado()) {
            throw new UnauthorizedException("Refresh token revocado");
        }

        if (refreshToken.getExpiracion().isBefore(LocalDateTime.now())) {
            throw new UnauthorizedException("Refresh token expirado");
        }

        return refreshToken;
    }

    @Override
    @Transactional
    public void revocarRefreshToken(String token) {
        refreshTokenRepository.revocarToken(token);
        log.debug("Refresh token revocado");
    }

    @Override
    @Transactional
    public void revocarTodosLosTokensDelUsuario(Long usuarioId) {
        refreshTokenRepository.deleteByUsuarioId(usuarioId);
        log.debug("Todos los refresh tokens del usuario {} revocados", usuarioId);
    }

    @Override
    @Transactional
    @Scheduled(cron = "0 0 2 * * ?")
    public void limpiarTokensExpirados() {
        refreshTokenRepository.deleteByExpiracionBefore(LocalDateTime.now());
        log.info("Limpieza de refresh tokens expirados completada");
    }
}
