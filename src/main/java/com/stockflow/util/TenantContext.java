package com.stockflow.util;

import lombok.extern.slf4j.Slf4j;

@Slf4j
public class TenantContext {

    private static final ThreadLocal<String> currentTenant = new ThreadLocal<>();

    /**
     * Establecer el tenantId para el thread actual
     */
    public static void setCurrentTenant(String tenantId) {
        currentTenant.set(tenantId);
        log.debug("🔑 TenantContext establecido: {}", tenantId);
    }

    /**
     * Obtener el tenantId del thread actual
     */
    public static String getCurrentTenant() {
        String tenantId = currentTenant.get();
        if (tenantId == null) {
            log.warn("⚠️ TenantContext es NULL - Usuario probablemente no autenticado");
        }
        return tenantId;
    }

    /**
     * Limpiar el tenantId del thread actual
     */
    public static void clear() {
        String tenantId = currentTenant.get();
        if (tenantId != null) {
            log.debug("🧹 Limpiando TenantContext: {}", tenantId);
        }
        currentTenant.remove();
    }

    /**
     * Verificar si hay un tenant establecido
     */
    public static boolean hasTenant() {
        return currentTenant.get() != null;
    }

    private static final ThreadLocal<Long> userIdContext = new ThreadLocal<>();

    public static void setCurrentUserId(Long usuarioId) {
        userIdContext.set(usuarioId);
    }

    public static Long getCurrentUserId() {
        Long usuarioId = userIdContext.get();
        if (usuarioId == null) {
            throw new IllegalStateException("Usuario no establecido en el contexto");
        }
        return usuarioId;
    }

    public static void clearUserId() {
        userIdContext.remove();
    }
}