package com.stockflow.controller;

import com.stockflow.dto.reportes.ReportesResumenDTO;
import com.stockflow.exception.BadRequestException;
import com.stockflow.service.ReportesService;
import com.stockflow.util.TenantContext;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.time.LocalDate;

@Slf4j
@RestController
@RequestMapping("/reportes")
@RequiredArgsConstructor
public class ReportesController {

    private final ReportesService reportesService;

    /**
     * GET /api/reportes/resumen?desde=YYYY-MM-DD&hasta=YYYY-MM-DD
     * <p>
     * Devuelve un resumen compacto con métricas de inventario, movimientos,
     * compras/recepciones y ventas para el tenant actual en el rango indicado.
     */
    @GetMapping("/resumen")
    @PreAuthorize("hasAnyRole('ADMIN', 'GERENTE') or hasAuthority('PERM_VER_REPORTES')")
    public ResponseEntity<ReportesResumenDTO> obtenerResumen(
            @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate desde,
            @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate hasta) {

        if (desde == null || hasta == null) {
            throw new BadRequestException("Los parámetros 'desde' y 'hasta' son obligatorios (formato YYYY-MM-DD)");
        }
        if (desde.isAfter(hasta)) {
            throw new BadRequestException("El parámetro 'desde' no puede ser posterior a 'hasta'");
        }

        String tenantId = TenantContext.getCurrentTenant();
        log.info("📊 Solicitud de reporte resumen: tenant={} rango=[{}, {}]", tenantId, desde, hasta);

        ReportesResumenDTO resumen = reportesService.obtenerResumen(tenantId, desde, hasta);
        return ResponseEntity.ok(resumen);
    }
}
