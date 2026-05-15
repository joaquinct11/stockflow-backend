package com.stockflow.controller;

import com.stockflow.dto.CrearDevolucionDTO;
import com.stockflow.dto.DevolucionDTO;
import com.stockflow.service.DevolucionService;
import com.stockflow.util.TenantContext;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@Slf4j
@RestController
@RequestMapping("/devoluciones")
@RequiredArgsConstructor
public class DevolucionController {

    private final DevolucionService devolucionService;

    @PostMapping
    @PreAuthorize("hasAnyRole('ADMIN', 'GERENTE', 'VENDEDOR') or hasAuthority('PERM_CREAR_DEVOLUCION')")
    public ResponseEntity<DevolucionDTO> crear(@Valid @RequestBody CrearDevolucionDTO dto) {
        Long usuarioId = TenantContext.getCurrentUserId();
        String tenantId = TenantContext.getCurrentTenant();
        log.info("Registrando devolución para venta {} (tenant={})", dto.getVentaId(), tenantId);
        DevolucionDTO result = devolucionService.crear(dto, usuarioId, tenantId);
        return ResponseEntity.status(HttpStatus.CREATED).body(result);
    }

    @GetMapping("/venta/{ventaId}")
    @PreAuthorize("hasAnyRole('ADMIN', 'GERENTE', 'VENDEDOR') or hasAuthority('PERM_VER_DEVOLUCIONES')")
    public ResponseEntity<List<DevolucionDTO>> getByVenta(@PathVariable Long ventaId) {
        String tenantId = TenantContext.getCurrentTenant();
        return ResponseEntity.ok(devolucionService.getByVenta(ventaId, tenantId));
    }

    @GetMapping
    @PreAuthorize("hasAnyRole('ADMIN', 'GERENTE') or hasAuthority('PERM_VER_DEVOLUCIONES')")
    public ResponseEntity<List<DevolucionDTO>> getAll() {
        String tenantId = TenantContext.getCurrentTenant();
        return ResponseEntity.ok(devolucionService.getAll(tenantId));
    }
}
