package com.stockflow.util;

import com.stockflow.config.properties.JwtProperties;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.SignatureAlgorithm;
import io.jsonwebtoken.security.Keys;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import javax.crypto.SecretKey;
import java.util.Date;
import java.util.UUID;

@Slf4j
@Component
@RequiredArgsConstructor
public class JwtUtil {

    private final JwtProperties jwtProperties;

    private SecretKey getSigningKey() {
        return Keys.hmacShaKeyFor(jwtProperties.getSecret().getBytes());
    }

    public String generateToken(Long usuarioId, String email, String nombre, String rol, String tenantId) {
        return Jwts.builder()
                .setSubject(email)
                .claim("usuarioId", usuarioId)
                .claim("nombre", nombre)
                .claim("rol", rol)
                .claim("tenantId", tenantId)
                .claim("nonce", UUID.randomUUID().toString())
                .setIssuedAt(new Date())
                .setExpiration(new Date(System.currentTimeMillis() + jwtProperties.getExpiration()))
                .signWith(getSigningKey(), SignatureAlgorithm.HS256)
                .compact();
    }

    public String generateRefreshToken(Long usuarioId, String email) {
        return Jwts.builder()
                .setSubject(email)
                .claim("usuarioId", usuarioId)
                .claim("type", "refresh")
                .setIssuedAt(new Date())
                .setExpiration(new Date(System.currentTimeMillis() + jwtProperties.getRefresh().getExpiration()))
                .signWith(getSigningKey(), SignatureAlgorithm.HS256)
                .compact();
    }

    public String getEmailFromToken(String token) {
        return Jwts.parserBuilder()
                .setSigningKey(getSigningKey()).build()
                .parseClaimsJws(token).getBody()
                .getSubject();
    }

    public Long getUserIdFromToken(String token) {
        Object id = Jwts.parserBuilder()
                .setSigningKey(getSigningKey()).build()
                .parseClaimsJws(token).getBody()
                .get("usuarioId");
        return (id instanceof Integer) ? ((Integer) id).longValue() : (Long) id;
    }

    public String getRolFromToken(String token) {
        return (String) Jwts.parserBuilder()
                .setSigningKey(getSigningKey()).build()
                .parseClaimsJws(token).getBody()
                .get("rol");
    }

    public String getTenantIdFromToken(String token) {
        return (String) Jwts.parserBuilder()
                .setSigningKey(getSigningKey()).build()
                .parseClaimsJws(token).getBody()
                .get("tenantId");
    }

    public String getTypeFromToken(String token) {
        try {
            Object type = Jwts.parserBuilder()
                    .setSigningKey(getSigningKey()).build()
                    .parseClaimsJws(token).getBody()
                    .get("type");
            return type != null ? (String) type : "access";
        } catch (Exception e) {
            return "access";
        }
    }

    public boolean validateToken(String token) {
        try {
            Jwts.parserBuilder().setSigningKey(getSigningKey()).build().parseClaimsJws(token);
            return true;
        } catch (Exception e) {
            log.error("Token inválido: {}", e.getMessage());
            return false;
        }
    }

    public boolean isTokenExpired(String token) {
        try {
            return Jwts.parserBuilder()
                    .setSigningKey(getSigningKey()).build()
                    .parseClaimsJws(token).getBody()
                    .getExpiration().before(new Date());
        } catch (Exception e) {
            return true;
        }
    }
}
