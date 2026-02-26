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
    public ResponseEntity<List<ProveedorDTO>> obtenerTodos() {
        String tenantId = TenantContext.getCurrentTenant();
        log.info("🏢 Obteniendo proveedores para tenant: {}", tenantId);

        return ResponseEntity.ok(
                proveedorMapper.toDTOList(proveedorService.obtenerProveedoresPorTenant(tenantId))
        );
    }

    @GetMapping("/activos")
    public ResponseEntity<List<ProveedorDTO>> obtenerActivos() {
        String tenantId = TenantContext.getCurrentTenant();
        log.info("✅ Obteniendo proveedores activos para tenant: {}", tenantId);

        return ResponseEntity.ok(
                proveedorMapper.toDTOList(proveedorService.obtenerProveedoresActivosPorTenant(tenantId))
        );
    }

    @GetMapping("/{id}")
    public ResponseEntity<ProveedorDTO> obtenerPorId(@PathVariable Long id) {
        return proveedorService.obtenerProveedorPorId(id)
                .map(proveedorMapper::toDTO)
                .map(ResponseEntity::ok)
                .orElse(ResponseEntity.notFound().build());
    }

    @GetMapping("/ruc/{ruc}")
    public ResponseEntity<ProveedorDTO> obtenerPorRuc(@PathVariable String ruc) {
        return proveedorService.obtenerProveedorPorRuc(ruc)
                .map(proveedorMapper::toDTO)
                .map(ResponseEntity::ok)
                .orElse(ResponseEntity.notFound().build());
    }

    @GetMapping("/buscar")
    public ResponseEntity<List<ProveedorDTO>> buscarPorNombre(@RequestParam String nombre) {
        return ResponseEntity.ok(
                proveedorMapper.toDTOList(proveedorService.buscarProveedoresPorNombre(nombre))
        );
    }

    /**
     * ✅ ACTUALIZADO: Setea tenantId automáticamente
     */
    @PostMapping
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
    public ResponseEntity<Void> eliminar(@PathVariable Long id) {
        log.info("🗑️ Eliminando proveedor ID: {}", id);
        proveedorService.eliminarProveedor(id);
        return ResponseEntity.noContent().build();
    }
}