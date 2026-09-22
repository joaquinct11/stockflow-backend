package com.stockflow.controller;

import com.stockflow.dto.ProductoPresentacionDTO;
import com.stockflow.service.ProductoPresentacionService;
import com.stockflow.util.TenantContext;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/productos")
@RequiredArgsConstructor
public class ProductoPresentacionController {

    private final ProductoPresentacionService service;

    @GetMapping("/{productoId}/presentaciones")
    @PreAuthorize("hasRole('ADMIN') or hasAuthority('PERM_VER_PRODUCTOS')")
    public ResponseEntity<List<ProductoPresentacionDTO>> listar(@PathVariable Long productoId) {
        String tenantId = TenantContext.getCurrentTenant();
        return ResponseEntity.ok(service.listarPorProducto(productoId, tenantId));
    }

    @PostMapping("/{productoId}/presentaciones")
    @PreAuthorize("hasRole('ADMIN') or hasAuthority('PERM_EDITAR_PRODUCTOS')")
    public ResponseEntity<ProductoPresentacionDTO> crear(
            @PathVariable Long productoId,
            @Valid @RequestBody ProductoPresentacionDTO dto) {
        String tenantId = TenantContext.getCurrentTenant();
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(service.crear(productoId, dto, tenantId));
    }

    @PutMapping("/presentaciones/{id}")
    @PreAuthorize("hasRole('ADMIN') or hasAuthority('PERM_EDITAR_PRODUCTOS')")
    public ResponseEntity<ProductoPresentacionDTO> actualizar(
            @PathVariable Long id,
            @Valid @RequestBody ProductoPresentacionDTO dto) {
        String tenantId = TenantContext.getCurrentTenant();
        return ResponseEntity.ok(service.actualizar(id, dto, tenantId));
    }

    @DeleteMapping("/presentaciones/{id}")
    @PreAuthorize("hasRole('ADMIN') or hasAuthority('PERM_EDITAR_PRODUCTOS')")
    public ResponseEntity<Void> eliminar(@PathVariable Long id) {
        String tenantId = TenantContext.getCurrentTenant();
        service.eliminar(id, tenantId);
        return ResponseEntity.noContent().build();
    }
}
