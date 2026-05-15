package com.stockflow.controller;

import com.stockflow.dto.NotificacionDTO;
import com.stockflow.service.NotificacionService;
import com.stockflow.util.TenantContext;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;

@Slf4j
@RestController
@RequestMapping("/notificaciones")
@RequiredArgsConstructor
public class NotificacionController {

    private final NotificacionService notificacionService;

    /** GET /notificaciones — últimas 50 del usuario (leídas + no leídas). */
    @GetMapping
    @PreAuthorize("isAuthenticated()")
    public ResponseEntity<List<NotificacionDTO>> getMisNotificaciones(Authentication auth) {
        Long   usuarioId = TenantContext.getCurrentUserId();
        String tenantId  = TenantContext.getCurrentTenant();
        String rolNombre = resolverRol(auth);
        return ResponseEntity.ok(notificacionService.getMisNotificaciones(usuarioId, rolNombre, tenantId));
    }

    /** GET /notificaciones/no-leidas-count — solo el número para el badge. */
    @GetMapping("/no-leidas-count")
    @PreAuthorize("isAuthenticated()")
    public ResponseEntity<Map<String, Long>> contarNoLeidas(Authentication auth) {
        Long   usuarioId = TenantContext.getCurrentUserId();
        String tenantId  = TenantContext.getCurrentTenant();
        String rolNombre = resolverRol(auth);
        long   count     = notificacionService.contarNoLeidas(usuarioId, rolNombre, tenantId);
        return ResponseEntity.ok(Map.of("count", count));
    }

    /** PATCH /notificaciones/{id}/leer — marca una notificación como leída. */
    @PatchMapping("/{id}/leer")
    @PreAuthorize("isAuthenticated()")
    public ResponseEntity<Void> marcarLeida(@PathVariable Long id, Authentication auth) {
        Long   usuarioId = TenantContext.getCurrentUserId();
        String tenantId  = TenantContext.getCurrentTenant();
        String rolNombre = resolverRol(auth);
        notificacionService.marcarLeida(id, usuarioId, rolNombre, tenantId);
        return ResponseEntity.noContent().build();
    }

    /** PATCH /notificaciones/leer-todas — marca todas como leídas. */
    @PatchMapping("/leer-todas")
    @PreAuthorize("isAuthenticated()")
    public ResponseEntity<Void> marcarTodasLeidas(Authentication auth) {
        Long   usuarioId = TenantContext.getCurrentUserId();
        String tenantId  = TenantContext.getCurrentTenant();
        String rolNombre = resolverRol(auth);
        notificacionService.marcarTodasLeidas(usuarioId, rolNombre, tenantId);
        return ResponseEntity.noContent().build();
    }

    /** DELETE /notificaciones/{id} — elimina una notificación. */
    @DeleteMapping("/{id}")
    @PreAuthorize("isAuthenticated()")
    public ResponseEntity<Void> eliminar(@PathVariable Long id, Authentication auth) {
        Long   usuarioId = TenantContext.getCurrentUserId();
        String tenantId  = TenantContext.getCurrentTenant();
        String rolNombre = resolverRol(auth);
        notificacionService.eliminar(id, usuarioId, rolNombre, tenantId);
        return ResponseEntity.noContent().build();
    }

    /** DELETE /notificaciones/leidas — elimina todas las notificaciones ya leídas. */
    @DeleteMapping("/leidas")
    @PreAuthorize("isAuthenticated()")
    public ResponseEntity<Void> eliminarTodasLeidas(Authentication auth) {
        Long   usuarioId = TenantContext.getCurrentUserId();
        String tenantId  = TenantContext.getCurrentTenant();
        String rolNombre = resolverRol(auth);
        notificacionService.eliminarTodasLeidas(usuarioId, rolNombre, tenantId);
        return ResponseEntity.noContent().build();
    }

    // ── helper ───────────────────────────────────────────────────────────────

    private String resolverRol(Authentication auth) {
        return auth.getAuthorities().stream()
                .map(GrantedAuthority::getAuthority)
                .filter(a -> a.startsWith("ROLE_"))
                .map(a -> a.replace("ROLE_", ""))
                .findFirst()
                .orElse("VENDEDOR");
    }
}
