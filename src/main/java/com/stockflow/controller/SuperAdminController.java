package com.stockflow.controller;

import com.stockflow.dto.superadmin.*;
import com.stockflow.service.SuperAdminService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/superadmin")
@RequiredArgsConstructor
@PreAuthorize("hasRole('SUPER_ADMIN')")
public class SuperAdminController {

    private final SuperAdminService superAdminService;

    @GetMapping("/stats")
    public ResponseEntity<SuperAdminStatsDTO> stats() {
        return ResponseEntity.ok(superAdminService.obtenerStats());
    }

    @GetMapping("/finanzas")
    public ResponseEntity<SuperAdminFinanzasDTO> finanzas() {
        return ResponseEntity.ok(superAdminService.obtenerFinanzas());
    }

    @GetMapping("/tenants")
    public ResponseEntity<List<TenantResumenDTO>> tenants() {
        return ResponseEntity.ok(superAdminService.listarTenants());
    }

    @GetMapping("/tenants/{tenantId}/cobros")
    public ResponseEntity<List<SuscripcionHistorialDTO>> cobros(@PathVariable String tenantId) {
        return ResponseEntity.ok(superAdminService.obtenerCobros(tenantId));
    }

    @PutMapping("/tenants/{tenantId}/suscripcion")
    public ResponseEntity<TenantResumenDTO> actualizarSuscripcion(
            @PathVariable String tenantId,
            @RequestBody SuscripcionManualUpdateDTO dto) {
        return ResponseEntity.ok(superAdminService.actualizarSuscripcion(tenantId, dto));
    }

    @PostMapping("/tenants/{tenantId}/trial")
    public ResponseEntity<TenantResumenDTO> extenderTrial(
            @PathVariable String tenantId,
            @RequestBody TrialExtenderDTO dto) {
        return ResponseEntity.ok(superAdminService.extenderTrial(tenantId, dto.getDias()));
    }

    @PutMapping("/tenants/{tenantId}/activo")
    public ResponseEntity<Void> toggleActivo(
            @PathVariable String tenantId,
            @RequestBody Map<String, Boolean> body) {
        superAdminService.toggleActivo(tenantId, body.getOrDefault("activo", true));
        return ResponseEntity.ok().build();
    }

    @PutMapping("/tenants/{tenantId}/ose")
    public ResponseEntity<Void> actualizarOse(
            @PathVariable String tenantId,
            @RequestBody OseUpdateDTO dto) {
        superAdminService.actualizarOse(tenantId, dto);
        return ResponseEntity.ok().build();
    }
}
