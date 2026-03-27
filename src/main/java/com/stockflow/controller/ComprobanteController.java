package com.stockflow.controller;

import com.stockflow.dto.ComprobanteDTO;
import com.stockflow.dto.EmitirComprobanteRequest;
import com.stockflow.service.ComprobanteService;
import com.stockflow.util.TenantContext;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.time.LocalDateTime;
import java.util.List;

@Slf4j
@RestController
@RequestMapping("/facturacion/comprobantes")
@RequiredArgsConstructor
public class ComprobanteController {

    private final ComprobanteService comprobanteService;

    /**
     * List comprobantes for the current tenant with optional filters.
     */
    @GetMapping
    @PreAuthorize("hasRole('ADMIN') or hasAuthority('PERM_VER_FACTURACION')")
    public ResponseEntity<List<ComprobanteDTO>> listar(
            @RequestParam(required = false) String tipo,
            @RequestParam(required = false) String estado,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) LocalDateTime from,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) LocalDateTime to,
            @RequestParam(required = false) Long ventaId,
            @RequestParam(required = false) String search) {

        String tenantId = TenantContext.getCurrentTenant();
        log.info("📋 Listando comprobantes para tenant: {}", tenantId);
        return ResponseEntity.ok(
                comprobanteService.listar(tenantId, tipo, estado, from, to, ventaId, search));
    }

    /**
     * Emit a new comprobante (boleta/factura) for an existing venta.
     */
    @PostMapping
    @PreAuthorize("hasRole('ADMIN') or hasAuthority('PERM_EMITIR_COMPROBANTE')")
    public ResponseEntity<ComprobanteDTO> emitir(@Valid @RequestBody EmitirComprobanteRequest request) {
        String tenantId = TenantContext.getCurrentTenant();
        log.info("🧾 Emitiendo comprobante tipo {} para venta {} (tenant {})",
                request.getTipo(), request.getVentaId(), tenantId);
        ComprobanteDTO dto = comprobanteService.emitir(request, tenantId);
        return ResponseEntity.status(HttpStatus.CREATED).body(dto);
    }

    /**
     * Get a single comprobante by its ID.
     */
    @GetMapping("/{id}")
    @PreAuthorize("hasRole('ADMIN') or hasAuthority('PERM_VER_COMPROBANTE')")
    public ResponseEntity<ComprobanteDTO> obtenerPorId(@PathVariable Long id) {
        String tenantId = TenantContext.getCurrentTenant();
        return comprobanteService.obtenerPorId(id, tenantId)
                .map(ResponseEntity::ok)
                .orElse(ResponseEntity.notFound().build());
    }

    /**
     * Anular (void) a comprobante.
     */
    @PostMapping("/{id}/anular")
    @PreAuthorize("hasRole('ADMIN') or hasAuthority('PERM_ANULAR_COMPROBANTE')")
    public ResponseEntity<ComprobanteDTO> anular(@PathVariable Long id) {
        String tenantId = TenantContext.getCurrentTenant();
        log.info("🗑️ Anulando comprobante ID: {} (tenant {})", id, tenantId);
        return ResponseEntity.ok(comprobanteService.anular(id, tenantId));
    }
}
