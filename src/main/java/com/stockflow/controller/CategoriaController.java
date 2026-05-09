package com.stockflow.controller;

import com.stockflow.dto.CategoriaRequestDTO;
import com.stockflow.dto.CategoriaResponseDTO;
import com.stockflow.service.CategoriaService;
import com.stockflow.util.TenantContext;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@Slf4j
@RestController
@RequestMapping("/categorias")
@RequiredArgsConstructor
public class CategoriaController {

    private final CategoriaService categoriaService;

    @GetMapping
    @PreAuthorize("hasRole('ADMIN') or hasAuthority('PERM_VER_PRODUCTOS')")
    public ResponseEntity<List<CategoriaResponseDTO>> listar() {
        String tenantId = TenantContext.getCurrentTenant();
        log.info("Obteniendo categorías para tenant: {}", tenantId);
        return ResponseEntity.ok(categoriaService.listar(tenantId));
    }

    @PostMapping
    @PreAuthorize("hasAnyRole('ADMIN', 'GESTOR_INVENTARIO') or hasAuthority('PERM_CREAR_PRODUCTO')")
    public ResponseEntity<CategoriaResponseDTO> crear(@RequestBody CategoriaRequestDTO request) {
        String tenantId = TenantContext.getCurrentTenant();
        log.info("Creando categoría '{}' para tenant: {}", request.getNombre(), tenantId);
        CategoriaResponseDTO created = categoriaService.crear(request.getNombre(), tenantId);
        return ResponseEntity.status(HttpStatus.CREATED).body(created);
    }
}
