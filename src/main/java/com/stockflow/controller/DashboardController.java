package com.stockflow.controller;

import com.stockflow.dto.ActividadRecienteDTO;
import com.stockflow.util.TenantContext;
import com.stockflow.service.DashboardService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/dashboard")
@RequiredArgsConstructor
public class DashboardController {

    private final DashboardService dashboardService;

    @GetMapping("/actividad-reciente")
    public ResponseEntity<List<ActividadRecienteDTO>> actividadReciente(
            @RequestParam(defaultValue = "15") int limit,
            @RequestParam(required = false) Long sucursalId) {

        String tenantId = TenantContext.getCurrentTenant();
        List<ActividadRecienteDTO> resultado = dashboardService.getActividadReciente(tenantId, sucursalId, Math.min(limit, 50));
        return ResponseEntity.ok(resultado);
    }
}
