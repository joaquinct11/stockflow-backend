package com.stockflow.controller;

import com.stockflow.dto.OnboardingProgresoDTO;
import com.stockflow.service.OnboardingService;
import com.stockflow.util.TenantContext;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@Slf4j
@RestController
@RequestMapping("/onboarding")
@RequiredArgsConstructor
public class OnboardingController {

    private final OnboardingService onboardingService;

    /**
     * GET /api/onboarding/progreso
     * Calcula el progreso de configuración inicial del tenant.
     * Solo visible para el ADMIN del tenant.
     */
    @GetMapping("/progreso")
    @PreAuthorize("hasRole('ADMIN')")
    public ResponseEntity<OnboardingProgresoDTO> getProgreso() {
        String tenantId = TenantContext.getCurrentTenant();
        log.debug("📋 Consultando progreso de onboarding para tenant: {}", tenantId);
        return ResponseEntity.ok(onboardingService.calcularProgreso(tenantId));
    }
}
