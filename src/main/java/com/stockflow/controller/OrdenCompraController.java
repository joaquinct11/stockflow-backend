package com.stockflow.controller;

import com.stockflow.dto.OrdenCompraItemDTO;
import com.stockflow.dto.OrdenCompraRequestDTO;
import com.stockflow.dto.OrdenCompraResponseDTO;
import com.stockflow.service.OrdenCompraService;
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
@RequestMapping("/oc")
@RequiredArgsConstructor
public class OrdenCompraController {

    private final OrdenCompraService ordenCompraService;

    /**
     * POST /api/oc — Create a new purchase order with items.
     */
    @PostMapping
    @PreAuthorize("hasRole('ADMIN') or hasAuthority('PERM_CREAR_OC')")
    public ResponseEntity<OrdenCompraResponseDTO> crear(@Valid @RequestBody OrdenCompraRequestDTO request) {
        String tenantId = TenantContext.getCurrentTenant();
        Long usuarioId = TenantContext.getCurrentUserId();
        log.info("📦 Creando OC para tenant={}", tenantId);
        OrdenCompraResponseDTO response = ordenCompraService.crearOrdenCompra(request, usuarioId, tenantId);
        return ResponseEntity.status(HttpStatus.CREATED).body(response);
    }

    /**
     * GET /api/oc — List purchase orders (optional filters: estado, proveedorId).
     */
    @GetMapping
    @PreAuthorize("hasAnyRole('ADMIN', 'GERENTE') or hasAuthority('PERM_VER_OC')")
    public ResponseEntity<List<OrdenCompraResponseDTO>> listar(
            @RequestParam(required = false) String estado,
            @RequestParam(required = false) Long proveedorId) {
        String tenantId = TenantContext.getCurrentTenant();
        return ResponseEntity.ok(ordenCompraService.listar(tenantId, estado, proveedorId));
    }

    /**
     * GET /api/oc/{id} — Detail with items and received/pending quantities.
     */
    @GetMapping("/{id}")
    @PreAuthorize("hasAnyRole('ADMIN', 'GERENTE') or hasAuthority('PERM_VER_OC')")
    public ResponseEntity<OrdenCompraResponseDTO> obtenerPorId(@PathVariable Long id) {
        return ordenCompraService.obtenerPorId(id)
                .map(ResponseEntity::ok)
                .orElse(ResponseEntity.notFound().build());
    }

    /**
     * GET /api/oc/{id}/items — Items with received and pending quantities.
     */
    @GetMapping("/{id}/items")
    @PreAuthorize("hasAnyRole('ADMIN', 'GERENTE') or hasAuthority('PERM_VER_OC')")
    public ResponseEntity<List<OrdenCompraItemDTO>> obtenerItems(@PathVariable Long id) {
        return ResponseEntity.ok(ordenCompraService.obtenerItemsConPendientes(id));
    }
}
