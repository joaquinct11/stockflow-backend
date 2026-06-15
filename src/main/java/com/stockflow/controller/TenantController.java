package com.stockflow.controller;

import com.stockflow.util.TenantContext;
import com.stockflow.dto.TenantConfigDTO;
import com.stockflow.entity.Tenant;
import com.stockflow.exception.ResourceNotFoundException;
import com.stockflow.repository.TenantRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

@Slf4j
@RestController
@RequestMapping("/tenant")
@RequiredArgsConstructor
public class TenantController {

    private final TenantRepository tenantRepository;

    /** GET /api/tenant/config — cualquier usuario autenticado del tenant */
    @GetMapping("/config")
    public ResponseEntity<TenantConfigDTO> getConfig() {
        String tenantId = TenantContext.getCurrentTenant();
        Tenant tenant = tenantRepository.findByTenantId(tenantId)
                .orElseThrow(() -> new ResourceNotFoundException("Tenant no encontrado"));
        return ResponseEntity.ok(toDTO(tenant));
    }

    /** PUT /api/tenant/config — solo ADMIN */
    @PutMapping("/config")
    @PreAuthorize("hasRole('ADMIN')")
    public ResponseEntity<TenantConfigDTO> updateConfig(@RequestBody TenantConfigDTO dto) {
        String tenantId = TenantContext.getCurrentTenant();
        Tenant tenant = tenantRepository.findByTenantId(tenantId)
                .orElseThrow(() -> new ResourceNotFoundException("Tenant no encontrado"));

        if (dto.getNombreNegocio() != null && !dto.getNombreNegocio().isBlank()) {
            tenant.setNombre(dto.getNombreNegocio());
        }
        tenant.setRuc(dto.getRuc());
        tenant.setDireccion(dto.getDireccion());
        tenant.setTelefono(dto.getTelefono());
        tenant.setEmailContacto(dto.getEmailContacto());
        tenant.setCiudad(dto.getCiudad());
        tenant.setPiePaginaPdf(dto.getPiePaginaPdf());

        if (dto.getMoneda() != null && !dto.getMoneda().isBlank()) {
            tenant.setMoneda(dto.getMoneda());
        }
        if (dto.getIgvPorcentaje() != null) {
            tenant.setIgvPorcentaje(dto.getIgvPorcentaje());
        }
        if (dto.getSerieBoleta() != null && !dto.getSerieBoleta().isBlank()) {
            tenant.setSerieBoleta(dto.getSerieBoleta());
        }
        if (dto.getSerieFactura() != null && !dto.getSerieFactura().isBlank()) {
            tenant.setSerieFactura(dto.getSerieFactura());
        }
        // Logo: null = mantener actual; "" = borrar; "data:..." = actualizar
        if (dto.getLogoBase64() != null) {
            tenant.setLogoBase64(dto.getLogoBase64().isBlank() ? null : dto.getLogoBase64());
        }
        // OSE URL: null = mantener; "" = borrar; valor = actualizar
        if (dto.getOseUrl() != null) {
            tenant.setOseUrl(dto.getOseUrl().isBlank() ? null : dto.getOseUrl().trim());
        }
        // OSE Token: solo se actualiza si el frontend manda un valor no vacío.
        // El frontend nunca pre-carga el token (seguridad), así que siempre llega ""
        // cuando el usuario no escribe nada → en ese caso conservamos el existente.
        if (dto.getOseToken() != null && !dto.getOseToken().isBlank()) {
            tenant.setOseToken(dto.getOseToken().trim());
        }
        if (dto.getRubro() != null && !dto.getRubro().isBlank()) {
            tenant.setRubro(dto.getRubro().trim().toUpperCase());
        }

        Tenant saved = tenantRepository.save(tenant);
        log.info("✅ Config del negocio actualizada para tenant={}", tenantId);
        return ResponseEntity.ok(toDTO(saved));
    }

    // ── helpers ──────────────────────────────────────────────────────────────

    /**
     * Devuelve "••••••{últimos 6 chars}" si el token tiene contenido,
     * null si no está configurado.
     */
    private String maskToken(String token) {
        if (token == null || token.isBlank()) return null;
        int show = Math.min(6, token.length());
        return "••••••" + token.substring(token.length() - show);
    }

    private TenantConfigDTO toDTO(Tenant t) {
        return TenantConfigDTO.builder()
                .tenantId(t.getTenantId())
                .nombreNegocio(t.getNombre())
                .ruc(t.getRuc())
                .direccion(t.getDireccion())
                .telefono(t.getTelefono())
                .emailContacto(t.getEmailContacto())
                .ciudad(t.getCiudad())
                .logoBase64(t.getLogoBase64())
                .moneda(t.getMoneda() != null ? t.getMoneda() : "S/.")
                .igvPorcentaje(t.getIgvPorcentaje() != null ? t.getIgvPorcentaje() : 18.0)
                .piePaginaPdf(t.getPiePaginaPdf())
                .serieBoleta(t.getSerieBoleta() != null ? t.getSerieBoleta() : "BBB1")
                .serieFactura(t.getSerieFactura() != null ? t.getSerieFactura() : "FFF1")
                .oseUrl(t.getOseUrl())
                // Enmascaramos el token: devolvemos solo los últimos 6 caracteres para
                // que la UI sepa que ya está configurado sin exponer el valor completo.
                .oseToken(maskToken(t.getOseToken()))
                .rubro(t.getRubro() != null ? t.getRubro() : "OTRO")
                .build();
    }
}
