package com.stockflow.filter;

import io.github.bucket4j.Bandwidth;
import io.github.bucket4j.Bucket;
import io.github.bucket4j.Refill;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;
import java.time.Duration;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Rate limiting para endpoints sensibles de autenticación.
 *
 * Reglas (por IP):
 *  - POST /api/auth/login          → 10 intentos / 5 minutos
 *  - POST /api/auth/forgot-password → 5 intentos / 15 minutos
 *
 * Implementación en memoria con Bucket4j (sin Redis).
 * Suficiente para un backend de instancia única.
 * Si en el futuro escala horizontalmente, migrar a bucket4j-redis.
 */
@Slf4j
@Component
public class RateLimitFilter extends OncePerRequestFilter {

    // Buckets separados por endpoint + IP para no mezclar contadores
    private final Map<String, Bucket> loginBuckets        = new ConcurrentHashMap<>();
    private final Map<String, Bucket> forgotPwdBuckets    = new ConcurrentHashMap<>();

    @Override
    protected void doFilterInternal(HttpServletRequest request,
                                    HttpServletResponse response,
                                    FilterChain filterChain)
            throws ServletException, IOException {

        String uri    = request.getRequestURI();
        String method = request.getMethod();

        if (!"POST".equalsIgnoreCase(method)) {
            filterChain.doFilter(request, response);
            return;
        }

        String clientIp = resolverIp(request);

        if (uri.endsWith("/auth/login")) {
            Bucket bucket = loginBuckets.computeIfAbsent(clientIp, ip -> crearBucketLogin());
            if (!bucket.tryConsume(1)) {
                log.warn("🚫 Rate limit excedido en /auth/login para IP: {}", clientIp);
                rechazar(response, "Demasiados intentos de inicio de sesión. Espera 5 minutos.");
                return;
            }
        } else if (uri.endsWith("/auth/forgot-password")) {
            Bucket bucket = forgotPwdBuckets.computeIfAbsent(clientIp, ip -> crearBucketForgotPwd());
            if (!bucket.tryConsume(1)) {
                log.warn("🚫 Rate limit excedido en /auth/forgot-password para IP: {}", clientIp);
                rechazar(response, "Demasiadas solicitudes de recuperación. Espera 15 minutos.");
                return;
            }
        }

        filterChain.doFilter(request, response);
    }

    /**
     * 10 intentos cada 5 minutos por IP.
     * Refill greedy: los tokens se recargan gradualmente (1 cada 30s).
     */
    private Bucket crearBucketLogin() {
        Bandwidth limit = Bandwidth.classic(10, Refill.greedy(10, Duration.ofMinutes(5)));
        return Bucket.builder().addLimit(limit).build();
    }

    /**
     * 5 intentos cada 15 minutos por IP.
     */
    private Bucket crearBucketForgotPwd() {
        Bandwidth limit = Bandwidth.classic(5, Refill.greedy(5, Duration.ofMinutes(15)));
        return Bucket.builder().addLimit(limit).build();
    }

    /**
     * Extrae la IP real considerando proxies/load balancers.
     */
    private String resolverIp(HttpServletRequest request) {
        String forwarded = request.getHeader("X-Forwarded-For");
        if (forwarded != null && !forwarded.isBlank()) {
            // Puede ser "ip1, ip2, ip3" — la primera es la IP real del cliente
            return forwarded.split(",")[0].trim();
        }
        return request.getRemoteAddr();
    }

    private void rechazar(HttpServletResponse response, String mensaje) throws IOException {
        response.setStatus(HttpStatus.TOO_MANY_REQUESTS.value());
        response.setContentType(MediaType.APPLICATION_JSON_VALUE);
        response.setCharacterEncoding("UTF-8");
        response.getWriter().write(
                "{\"status\":429,\"error\":\"Too Many Requests\",\"mensaje\":\"" + mensaje + "\"}"
        );
    }
}
