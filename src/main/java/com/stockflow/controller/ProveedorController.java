package com.stockflow.controller;

import com.stockflow.dto.ProveedorDTO;
import com.stockflow.entity.Proveedor;
import com.stockflow.mapper.ProveedorMapper;
import com.stockflow.service.ProveedorService;
import com.stockflow.util.TenantContext;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;
import jakarta.validation.Valid;
import java.util.List;

@Slf4j
@RestController
@RequestMapping("/proveedores")
@RequiredArgsConstructor
public class ProveedorController {

    private final ProveedorService proveedorService;
    private final ProveedorMapper proveedorMapper;

    /**
     * ✅ ACTUALIZADO: Obtiene proveedores del tenant actual
     */
    @GetMapping
    @PreAuthorize("hasAnyRole('ADMIN', 'GERENTE', 'GESTOR_INVENTARIO', 'VENDEDOR') or hasAuthority('PERM_VER_PROVEEDORES')")
    public ResponseEntity<List<ProveedorDTO>> obtenerTodos() {
        String tenantId = TenantContext.getCurrentTenant();
        log.info("🏢 Obteniendo proveedores para tenant: {}", tenantId);

        return ResponseEntity.ok(
                proveedorMapper.toDTOList(proveedorService.obtenerProveedoresPorTenant(tenantId))
        );
    }

    @GetMapping("/activos")
    @PreAuthorize("hasAnyRole('ADMIN', 'GERENTE', 'GESTOR_INVENTARIO', 'VENDEDOR') or hasAuthority('PERM_VER_PROVEEDORES')")
    public ResponseEntity<List<ProveedorDTO>> obtenerActivos() {
        String tenantId = TenantContext.getCurrentTenant();
        log.info("✅ Obteniendo proveedores activos para tenant: {}", tenantId);

        return ResponseEntity.ok(
                proveedorMapper.toDTOList(proveedorService.obtenerProveedoresActivosPorTenant(tenantId))
        );
    }

    @GetMapping("/{id}")
    @PreAuthorize("hasAnyRole('ADMIN', 'GERENTE', 'GESTOR_INVENTARIO') or hasAuthority('PERM_VER_PROVEEDORES')")
    public ResponseEntity<ProveedorDTO> obtenerPorId(@PathVariable Long id) {
        return proveedorService.obtenerProveedorPorId(id)
                .map(proveedorMapper::toDTO)
                .map(ResponseEntity::ok)
                .orElse(ResponseEntity.notFound().build());
    }

    @GetMapping("/ruc/{ruc}")
    @PreAuthorize("hasAnyRole('ADMIN', 'GERENTE', 'GESTOR_INVENTARIO') or hasAuthority('PERM_VER_PROVEEDORES')")
    public ResponseEntity<ProveedorDTO> obtenerPorRuc(@PathVariable String ruc) {
        return proveedorService.obtenerProveedorPorRuc(ruc)
                .map(proveedorMapper::toDTO)
                .map(ResponseEntity::ok)
                .orElse(ResponseEntity.notFound().build());
    }

    @GetMapping("/buscar")
    @PreAuthorize("hasAnyRole('ADMIN', 'GERENTE', 'GESTOR_INVENTARIO') or hasAuthority('PERM_VER_PROVEEDORES')")
    public ResponseEntity<List<ProveedorDTO>> buscarPorNombre(@RequestParam String nombre) {
        return ResponseEntity.ok(
                proveedorMapper.toDTOList(proveedorService.buscarProveedoresPorNombre(nombre))
        );
    }

    /**
     * ✅ ACTUALIZADO: Setea tenantId automáticamente
     */
    @PostMapping
    @PreAuthorize("hasAnyRole('ADMIN', 'GERENTE', 'GESTOR_INVENTARIO') or hasAuthority('PERM_CREAR_PROVEEDOR')")
    public ResponseEntity<ProveedorDTO> crear(@Valid @RequestBody ProveedorDTO proveedorDTO) {
        String tenantId = TenantContext.getCurrentTenant();
        log.info("➕ Creando proveedor para tenant: {}", tenantId);

        proveedorDTO.setTenantId(tenantId);

        Proveedor proveedor = proveedorMapper.toEntity(proveedorDTO);
        Proveedor proveedorCreado = proveedorService.crearProveedor(proveedor);

        return ResponseEntity.status(HttpStatus.CREATED)
                .body(proveedorMapper.toDTO(proveedorCreado));
    }

    @PutMapping("/{id}")
    @PreAuthorize("hasAnyRole('ADMIN', 'GERENTE', 'GESTOR_INVENTARIO') or hasAuthority('PERM_EDITAR_PROVEEDOR')")
    public ResponseEntity<ProveedorDTO> actualizar(
            @PathVariable Long id,
            @Valid @RequestBody ProveedorDTO proveedorDTO) {
        log.info("✏️ Actualizando proveedor ID: {}", id);

        return proveedorService.obtenerProveedorPorId(id)
                .map(proveedorExistente -> {
                    proveedorMapper.updateEntityFromDTO(proveedorDTO, proveedorExistente);
                    Proveedor proveedorActualizado = proveedorService.actualizarProveedor(id, proveedorExistente);
                    return ResponseEntity.ok(proveedorMapper.toDTO(proveedorActualizado));
                })
                .orElse(ResponseEntity.notFound().build());
    }

    @PatchMapping("/{id}/activar")
    @PreAuthorize("hasAnyRole('ADMIN', 'GERENTE', 'GESTOR_INVENTARIO') or hasAuthority('PERM_ACTIVAR_PROVEEDOR')")
    public ResponseEntity<ProveedorDTO> activar(@PathVariable Long id) {
        log.info("✅ Activando proveedor ID: {}", id);
        return proveedorService.obtenerProveedorPorId(id)
                .map(proveedor -> {
                    Proveedor proveedorActivado = proveedorService.activarProveedor(id);
                    return ResponseEntity.ok(proveedorMapper.toDTO(proveedorActivado));
                })
                .orElse(ResponseEntity.notFound().build());
    }

    @PatchMapping("/{id}/desactivar")
    @PreAuthorize("hasAnyRole('ADMIN', 'GERENTE', 'GESTOR_INVENTARIO') or hasAuthority('PERM_ACTIVAR_PROVEEDOR')")
    public ResponseEntity<ProveedorDTO> desactivar(@PathVariable Long id) {
        log.info("🔒 Desactivando proveedor ID: {}", id);
        return proveedorService.obtenerProveedorPorId(id)
                .map(proveedor -> {
                    Proveedor proveedorDesactivado = proveedorService.desactivarProveedor(id);
                    return ResponseEntity.ok(proveedorMapper.toDTO(proveedorDesactivado));
                })
                .orElse(ResponseEntity.notFound().build());
    }

    @DeleteMapping("/{id}")
    @PreAuthorize("hasRole('ADMIN') or hasAuthority('PERM_ELIMINAR_PROVEEDOR')")
    public ResponseEntity<Void> eliminar(@PathVariable Long id) {
        log.info("🗑️ Eliminando proveedor ID: {}", id);
        proveedorService.eliminarProveedor(id);
        return ResponseEntity.noContent().build();
    }
}