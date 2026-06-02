package com.stockflow.util;

import lombok.extern.slf4j.Slf4j;

@Slf4j
public class TenantContext {

    private static final ThreadLocal<String> tenantContext  = new ThreadLocal<>();
    private static final ThreadLocal<Long>   userIdContext  = new ThreadLocal<>();

    // ── Tenant ────────────────────────────────────────────────

    public static void setCurrentTenant(String tenantId) {
        tenantContext.set(tenantId);
        log.debug("TenantContext establecido: {}", tenantId);
    }

    public static String getCurrentTenant() {
        String tenantId = tenantContext.get();
        if (tenantId == null) {
            log.warn("TenantContext es NULL — usuario probablemente no autenticado");
        }
        return tenantId;
    }

    // ── Usuario ───────────────────────────────────────────────

    public static void setCurrentUserId(Long userId) {
        userIdContext.set(userId);
    }

    public static Long getCurrentUserId() {
        Long userId = userIdContext.get();
        if (userId == null) {
            throw new IllegalStateException("Usuario no establecido en el contexto");
        }
        return userId;
    }

    // ── Limpieza (llamar en el finally de TenantFilter) ───────

    public static void clear() {
        log.debug("Limpiando TenantContext: {}", tenantContext.get());
        tenantContext.remove();
        userIdContext.remove();
    }
}
