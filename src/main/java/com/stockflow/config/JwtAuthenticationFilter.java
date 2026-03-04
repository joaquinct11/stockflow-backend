package com.stockflow.config;

import com.stockflow.util.JwtUtil;
import com.stockflow.util.TenantContext;
import io.jsonwebtoken.ExpiredJwtException;
import io.jsonwebtoken.MalformedJwtException;
import io.jsonwebtoken.UnsupportedJwtException;
import io.jsonwebtoken.security.SignatureException;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;
import java.util.List;

@Slf4j
@Component
@RequiredArgsConstructor
public class JwtAuthenticationFilter extends OncePerRequestFilter {

    private final JwtUtil jwtUtil;

    @Override
    protected void doFilterInternal(
            HttpServletRequest request,
            HttpServletResponse response,
            FilterChain filterChain) throws ServletException, IOException {

        try {
            String token = getTokenFromRequest(request);

            if (token != null) {
                // ✅ Validar que el token no esté expirado
                if (jwtUtil.isTokenExpired(token)) {
                    request.setAttribute("jwt_error", "Token JWT expirado");
                    log.warn("⏰ Token expirado detectado");
                } else if (jwtUtil.validateToken(token)) {
                    // ✅ Token válido y no expirado
                    String email = jwtUtil.getEmailFromToken(token);
                    String rol = jwtUtil.getRolFromToken(token);
                    String tenantId = jwtUtil.getTenantIdFromToken(token);
                    Long usuarioId = jwtUtil.getUserIdFromToken(token);

                    // Establecer TenantContext
                    TenantContext.setCurrentTenant(tenantId);

                    UsernamePasswordAuthenticationToken authentication =
                            new UsernamePasswordAuthenticationToken(
                                    email,
                                    null,
                                    List.of(new SimpleGrantedAuthority("ROLE_" + rol))
                            );

                    authentication.setDetails(usuarioId);
                    SecurityContextHolder.getContext().setAuthentication(authentication);

                    log.debug("✅ Usuario autenticado: {} | Tenant: {} | Rol: {}", email, tenantId, rol);
                } else {
                    request.setAttribute("jwt_error", "Token JWT inválido");
                }
            }
        } catch (ExpiredJwtException e) {
            log.warn("⏰ ExpiredJwtException: {}", e.getMessage());
            request.setAttribute("jwt_error", "Token JWT expirado");

        } catch (MalformedJwtException e) {
            log.error("❌ MalformedJwtException: {}", e.getMessage());
            request.setAttribute("jwt_error", "Token JWT malformado");

        } catch (SignatureException e) {
            log.error("❌ SignatureException: {}", e.getMessage());
            request.setAttribute("jwt_error", "Firma del token inválida");

        } catch (UnsupportedJwtException e) {
            log.error("❌ UnsupportedJwtException: {}", e.getMessage());
            request.setAttribute("jwt_error", "Formato de token no soportado");

        } catch (IllegalArgumentException e) {
            log.error("❌ IllegalArgumentException: {}", e.getMessage());
            request.setAttribute("jwt_error", "Token JWT vacío");

        } catch (Exception e) {
            log.error("❌ Error inesperado al procesar JWT: {}", e.getMessage(), e);
            request.setAttribute("jwt_error", "Error al procesar token de autenticación");
        }

        filterChain.doFilter(request, response);
    }

    private String getTokenFromRequest(HttpServletRequest request) {
        String header = request.getHeader("Authorization");

        if (header != null && header.startsWith("Bearer ")) {
            return header.substring(7);
        }

        return null;
    }
}