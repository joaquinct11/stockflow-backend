package com.stockflow.controller;

import com.stockflow.dto.ProductoVarianteDTO;
import com.stockflow.service.ProductoVarianteService;
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
@RequestMapping("/producto-variantes")
@RequiredArgsConstructor
public class ProductoVarianteController {

    private final ProductoVarianteService varianteService;

    /** GET /producto-variantes/producto/{productoId}?sucursalId= */
    @GetMapping("/producto/{productoId}")
    @PreAuthorize("hasAnyRole('ADMIN','GERENTE','VENDEDOR') or hasAuthority('PERM_VER_PRODUCTOS')")
    public ResponseEntity<List<ProductoVarianteDTO>> getByProducto(
            @PathVariable Long productoId,
            @RequestParam(required = false) Long sucursalId) {
        String tenantId = TenantContext.getCurrentTenant();
        return ResponseEntity.ok(varianteService.getByProducto(productoId, tenantId, sucursalId));
    }

    /** POST /producto-variantes */
    @PostMapping
    @PreAuthorize("hasAnyRole('ADMIN','GERENTE') or hasAuthority('PERM_CREAR_PRODUCTOS')")
    public ResponseEntity<ProductoVarianteDTO> create(@Valid @RequestBody ProductoVarianteDTO dto) {
        String tenantId = TenantContext.getCurrentTenant();
        return ResponseEntity.status(HttpStatus.CREATED).body(varianteService.create(dto, tenantId));
    }

    /** PUT /producto-variantes/{id} */
    @PutMapping("/{id}")
    @PreAuthorize("hasAnyRole('ADMIN','GERENTE') or hasAuthority('PERM_EDITAR_PRODUCTOS')")
    public ResponseEntity<ProductoVarianteDTO> update(@PathVariable Long id,
                                                      @Valid @RequestBody ProductoVarianteDTO dto) {
        String tenantId = TenantContext.getCurrentTenant();
        return ResponseEntity.ok(varianteService.update(id, dto, tenantId));
    }

    /** DELETE /producto-variantes/{id} */
    @DeleteMapping("/{id}")
    @PreAuthorize("hasAnyRole('ADMIN','GERENTE') or hasAuthority('PERM_ELIMINAR_PRODUCTOS')")
    public ResponseEntity<Void> delete(@PathVariable Long id) {
        String tenantId = TenantContext.getCurrentTenant();
        varianteService.delete(id, tenantId);
        return ResponseEntity.noContent().build();
    }
}
