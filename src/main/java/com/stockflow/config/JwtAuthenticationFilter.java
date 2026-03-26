package com.stockflow.config;

import com.stockflow.repository.UsuarioPermisoRepository;
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
import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;

@Slf4j
@Component
@RequiredArgsConstructor
public class JwtAuthenticationFilter extends OncePerRequestFilter {

    private final JwtUtil jwtUtil;
    private final UsuarioPermisoRepository usuarioPermisoRepository;
    private final RolePermissionDefaults rolePermissionDefaults;

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
                    TenantContext.setCurrentUserId(usuarioId);
                    TenantContext.setCurrentTenant(tenantId);

                    // Construir authorities: rol + permisos base del rol + permisos directos del usuario
                    Set<SimpleGrantedAuthority> authoritySet = new LinkedHashSet<>();
                    authoritySet.add(new SimpleGrantedAuthority("ROLE_" + rol));

                    // Cargar permisos base del rol desde la definición canónica
                    try {
                        Set<String> basePerms = rolePermissionDefaults.getBasePermissions(rol);
                        for (String codigo : basePerms) {
                            authoritySet.add(new SimpleGrantedAuthority("PERM_" + codigo));
                        }
                    } catch (Exception e) {
                        log.error("❌ Error cargando permisos base del rol {}: {}. El acceso se basará solo en el rol.",
                                rol, e.getMessage());
                    }

                    // Cargar permisos directos asignados al usuario en este tenant
                    try {
                        List<String> permisoCodigos = usuarioPermisoRepository.findPermisoCodigos(usuarioId, tenantId);
                        for (String codigo : permisoCodigos) {
                            authoritySet.add(new SimpleGrantedAuthority("PERM_" + codigo));
                        }
                    } catch (Exception e) {
                        log.error("❌ Error cargando permisos del usuario {} en tenant {}: {}. El acceso se basará solo en el rol.",
                                usuarioId, tenantId, e.getMessage());
                    }

                    List<SimpleGrantedAuthority> authorities = new ArrayList<>(authoritySet);

                    UsernamePasswordAuthenticationToken authentication =
                            new UsernamePasswordAuthenticationToken(
                                    email,
                                    null,
                                    authorities
                            );

                    authentication.setDetails(usuarioId);
                    SecurityContextHolder.getContext().setAuthentication(authentication);

                    log.debug("✅ Usuario autenticado: {} | Tenant: {} | Rol: {} | Permisos: {}",
                            email, tenantId, rol, authorities.size() - 1);
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