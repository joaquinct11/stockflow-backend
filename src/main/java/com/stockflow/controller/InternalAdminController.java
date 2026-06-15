package com.stockflow.controller;

import com.stockflow.entity.Tenant;
import com.stockflow.exception.ResourceNotFoundException;
import com.stockflow.repository.TenantRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.Map;

/**
 * Endpoints internos de Fluxus — solo accesibles con la clave interna.
 * No requieren JWT; el acceso se valida por el header X-Internal-Key.
 *
 * Uso: cuando ApiSunat entrega el token de una nueva organización,
 * el equipo de Fluxus lo registra aquí para habilitar la facturación del tenant.
 */
@Slf4j
@RestController
@RequestMapping("/internal")
@RequiredArgsConstructor
public class InternalAdminController {

    private final TenantRepository tenantRepository;

    @Value("${fluxus.internal-api-key}")
    private String internalApiKey;

    /**
     * Asigna el token de ApiSunat a un tenant.
     * Body: { "token": "eyJ..." }
     */
    @PutMapping("/tenants/{tenantId}/apisunat-token")
    public ResponseEntity<?> setApiSunatToken(
            @PathVariable String tenantId,
            @RequestHeader("X-Internal-Key") String key,
            @RequestBody Map<String, String> body) {

        if (!internalApiKey.equals(key)) {
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED)
                    .body(Map.of("error", "Clave interna inválida"));
        }

        String token = body.get("token");
        if (token == null || token.isBlank()) {
            return ResponseEntity.badRequest()
                    .body(Map.of("error", "El campo 'token' es requerido"));
        }

        Tenant tenant = tenantRepository.findByTenantId(tenantId)
                .orElseThrow(() -> new ResourceNotFoundException("Tenant no encontrado: " + tenantId));

        tenant.setOseToken(token.trim());
        tenantRepository.save(tenant);

        log.info("✅ [Internal] ApiSunat token actualizado para tenant={}", tenantId);
        return ResponseEntity.ok(Map.of(
                "tenantId", tenantId,
                "nombre", tenant.getNombre() != null ? tenant.getNombre() : "",
                "mensaje", "Token de ApiSunat configurado correctamente"
        ));
    }

    /**
     * Elimina el token de ApiSunat de un tenant (deshabilita la facturación).
     */
    @DeleteMapping("/tenants/{tenantId}/apisunat-token")
    public ResponseEntity<?> removeApiSunatToken(
            @PathVariable String tenantId,
            @RequestHeader("X-Internal-Key") String key) {

        if (!internalApiKey.equals(key)) {
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED)
                    .body(Map.of("error", "Clave interna inválida"));
        }

        Tenant tenant = tenantRepository.findByTenantId(tenantId)
                .orElseThrow(() -> new ResourceNotFoundException("Tenant no encontrado: " + tenantId));

        tenant.setOseToken(null);
        tenantRepository.save(tenant);

        log.info("🗑️ [Internal] ApiSunat token eliminado para tenant={}", tenantId);
        return ResponseEntity.ok(Map.of("mensaje", "Token eliminado. Facturación deshabilitada para " + tenantId));
    }
}
