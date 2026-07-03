package com.stockflow.controller;

import com.stockflow.dto.SucursalDTO;
import com.stockflow.service.SucursalService;
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
@RequestMapping("/sucursales")
@RequiredArgsConstructor
public class SucursalController {

    private final SucursalService sucursalService;

    @GetMapping
    @PreAuthorize("hasRole('ADMIN') or hasAuthority('PERM_VER_SUCURSALES')")
    public ResponseEntity<List<SucursalDTO>> listar() {
        String tenantId = TenantContext.getCurrentTenant();
        log.info("Listando sucursales para tenant: {}", tenantId);
        return ResponseEntity.ok(sucursalService.listar(tenantId));
    }

    @GetMapping("/{id}")
    @PreAuthorize("hasRole('ADMIN') or hasAuthority('PERM_VER_SUCURSALES')")
    public ResponseEntity<SucursalDTO> obtenerPorId(@PathVariable Long id) {
        String tenantId = TenantContext.getCurrentTenant();
        return ResponseEntity.ok(sucursalService.obtenerPorId(id, tenantId));
    }

    @PostMapping
    @PreAuthorize("hasRole('ADMIN') or hasAuthority('PERM_CREAR_SUCURSAL')")
    public ResponseEntity<SucursalDTO> crear(@Valid @RequestBody SucursalDTO dto) {
        String tenantId = TenantContext.getCurrentTenant();
        log.info("Creando sucursal '{}' para tenant: {}", dto.getNombre(), tenantId);
        return ResponseEntity.status(HttpStatus.CREATED).body(sucursalService.crear(dto, tenantId));
    }

    @PutMapping("/{id}")
    @PreAuthorize("hasRole('ADMIN') or hasAuthority('PERM_EDITAR_SUCURSAL')")
    public ResponseEntity<SucursalDTO> actualizar(@PathVariable Long id, @Valid @RequestBody SucursalDTO dto) {
        String tenantId = TenantContext.getCurrentTenant();
        log.info("Actualizando sucursal {} para tenant: {}", id, tenantId);
        return ResponseEntity.ok(sucursalService.actualizar(id, dto, tenantId));
    }

    @DeleteMapping("/{id}")
    @PreAuthorize("hasRole('ADMIN') or hasAuthority('PERM_ELIMINAR_SUCURSAL')")
    public ResponseEntity<Void> desactivar(@PathVariable Long id) {
        String tenantId = TenantContext.getCurrentTenant();
        log.info("Desactivando sucursal {} para tenant: {}", id, tenantId);
        sucursalService.desactivar(id, tenantId);
        return ResponseEntity.noContent().build();
    }

    /**
     * POST /sucursales/inicializar-principal
     * Crea la sucursal principal del tenant (si no existe) y migra todos
     * los registros existentes (ventas, movimientos, cajas, etc.) a ella.
     * Útil para activar multi-local en tenants que ya tenían datos previos.
     */
    @PostMapping("/inicializar-principal")
    @PreAuthorize("hasRole('ADMIN')")
    public ResponseEntity<SucursalDTO> inicializarPrincipal() {
        String tenantId = TenantContext.getCurrentTenant();
        log.info("Inicializando sucursal principal para tenant: {}", tenantId);
        return ResponseEntity.ok(sucursalService.inicializarPrincipal(tenantId));
    }

    /**
     * POST /sucursales/resync-stock-principal
     * Resincroniza el stock de la sucursal principal con productos.stock_actual.
     * Útil para corregir desincronizaciones históricas.
     */
    @PostMapping("/resync-stock-principal")
    @PreAuthorize("hasRole('ADMIN')")
    public ResponseEntity<Void> resyncStockPrincipal() {
        String tenantId = TenantContext.getCurrentTenant();
        log.info("Resync stock sucursal principal para tenant: {}", tenantId);
        sucursalService.resyncStockPrincipal(tenantId);
        return ResponseEntity.ok().build();
    }
}
