package com.stockflow.filter;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.extern.slf4j.Slf4j;
import org.slf4j.MDC;
import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.lang.NonNull;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;
import java.util.UUID;

/**
 * Filtro de primer nivel: se ejecuta antes que Spring Security (-100)
 * gracias a Order(-200).
 *
 * Responsabilidades:
 *  1. Generar un requestId único y agregarlo al MDC (Mapped Diagnostic Context)
 *     → cada línea de log incluye automáticamente este ID en el patrón/JSON.
 *  2. Agregar el header X-Request-Id a la respuesta para que el frontend
 *     pueda reportar el ID exacto al abrir un issue.
 *  3. Loguear entrada y salida de cada request con método, path, status y duración.
 *  4. Limpiar el MDC al finalizar (imprescindible para evitar leaks entre requests).
 *
 * El tenantId y userId se agregan al MDC desde JwtAuthenticationFilter (que corre
 * dentro del filtro de Spring Security, orden -100) y por eso aparecen en todos
 * los logs del hilo sin necesidad de propagarlos manualmente.
 */
@Slf4j
@Component
@Order(Ordered.HIGHEST_PRECEDENCE)   // -2147483648 — corre antes de Spring Security (-100)
public class RequestLoggingFilter extends OncePerRequestFilter {

    private static final String REQUEST_ID_HEADER = "X-Request-Id";
    private static final String MDC_REQUEST_ID    = "requestId";

    @Override
    protected void doFilterInternal(
            @NonNull HttpServletRequest  request,
            @NonNull HttpServletResponse response,
            @NonNull FilterChain         chain) throws ServletException, IOException {

        String requestId = shortUuid();
        String method    = request.getMethod();
        String path      = request.getRequestURI();

        MDC.put(MDC_REQUEST_ID, requestId);
        response.setHeader(REQUEST_ID_HEADER, requestId);

        long start = System.currentTimeMillis();

        try {
            log.debug(">> {} {}", method, path);
            chain.doFilter(request, response);
        } finally {
            long   duration = System.currentTimeMillis() - start;
            int    status   = response.getStatus();
            String msg      = "<< {} {} {} ({}ms)";

            if      (status >= 500) log.error(msg, method, path, status, duration);
            else if (status >= 400) log.warn (msg, method, path, status, duration);
            else                    log.info (msg, method, path, status, duration);

            MDC.clear();
        }
    }

    /** Excluir rutas de infraestructura para no contaminar los logs de negocio. */
    @Override
    protected boolean shouldNotFilter(HttpServletRequest request) {
        String path = request.getRequestURI();
        return path.startsWith("/actuator")
            || path.startsWith("/swagger-ui")
            || path.startsWith("/v3/api-docs")
            || path.startsWith("/api-docs");
    }

    private static String shortUuid() {
        return UUID.randomUUID().toString().replace("-", "").substring(0, 8);
    }
}
