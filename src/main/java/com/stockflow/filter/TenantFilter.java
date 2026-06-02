package com.stockflow.filter;

import com.stockflow.util.JwtUtil;
import com.stockflow.util.TenantContext;
import jakarta.servlet.*;
import jakarta.servlet.http.HttpServletRequest;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;

import java.io.IOException;

@Slf4j
@Component
@Order(2) // Se ejecuta después del JwtAuthenticationFilter (que es Order(1))
@RequiredArgsConstructor
public class TenantFilter implements Filter {

    private final JwtUtil jwtUtil;

    @Override
    public void doFilter(ServletRequest request, ServletResponse response, FilterChain chain)
            throws IOException, ServletException {

        HttpServletRequest httpRequest = (HttpServletRequest) request;
        String requestURI = httpRequest.getRequestURI();

        // Endpoints públicos que NO requieren tenant
        if (isPublicEndpoint(requestURI)) {
            log.debug("🌐 Endpoint público, no requiere tenant: {}", requestURI);
            chain.doFilter(request, response);
            return;
        }

        String authHeader = httpRequest.getHeader("Authorization");

        if (authHeader != null && authHeader.startsWith("Bearer ")) {
            String token = authHeader.substring(7);
            try {
                // Extraer tenantId del JWT
                String tenantId = jwtUtil.getTenantIdFromToken(token);

                if (tenantId != null && !tenantId.isEmpty()) {
                    TenantContext.setCurrentTenant(tenantId);
                    log.debug("🔑 TenantId establecido: {} para URI: {}", tenantId, requestURI);
                } else {
                    log.warn("⚠️ Token válido pero sin tenantId: {}", requestURI);
                }
            } catch (Exception e) {
                log.warn("⚠️ Error extrayendo tenantId del token: {} - URI: {}", e.getMessage(), requestURI);
            }
        } else {
            log.debug("ℹ️ No hay token Bearer en la petición: {}", requestURI);
        }

        try {
            chain.doFilter(request, response);
        } finally {
            // CRÍTICO: Limpiar ThreadLocal al finalizar la petición
            TenantContext.clear();
        }
    }

    /**
     * Verificar si es un endpoint público que NO requiere autenticación
     */
    private boolean isPublicEndpoint(String uri) {
        return uri.startsWith("/api/auth/login") ||
                uri.startsWith("/api/auth/register") ||
                uri.startsWith("/api/auth/registro") ||
                uri.startsWith("/swagger-ui") ||
                uri.startsWith("/v3/api-docs") ||
                uri.startsWith("/actuator");
    }
}