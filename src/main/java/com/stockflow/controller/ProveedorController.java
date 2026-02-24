package com.stockflow.controller;

import com.stockflow.dto.ProveedorDTO;
import com.stockflow.entity.Proveedor;
import com.stockflow.mapper.ProveedorMapper;
import com.stockflow.service.ProveedorService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import jakarta.validation.Valid;
import java.util.List;

@RestController
@RequestMapping("/proveedores")
@RequiredArgsConstructor
public class ProveedorController {

    private final ProveedorService proveedorService;
    private final ProveedorMapper proveedorMapper;

    @GetMapping
    public ResponseEntity<List<ProveedorDTO>> obtenerTodos() {
        return ResponseEntity.ok(
                proveedorMapper.toDTOList(proveedorService.obtenerTodosProveedores())
        );
    }

    @GetMapping("/activos")
    public ResponseEntity<List<ProveedorDTO>> obtenerActivos() {
        return ResponseEntity.ok(
                proveedorMapper.toDTOList(proveedorService.obtenerProveedoresActivos())
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

    @PostMapping
    public ResponseEntity<ProveedorDTO> crear(@Valid @RequestBody ProveedorDTO proveedorDTO) {
        Proveedor proveedor = proveedorMapper.toEntity(proveedorDTO);
        Proveedor proveedorCreado = proveedorService.crearProveedor(proveedor);
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(proveedorMapper.toDTO(proveedorCreado));
    }

    @PutMapping("/{id}")
    public ResponseEntity<ProveedorDTO> actualizar(
            @PathVariable Long id,
            @Valid @RequestBody ProveedorDTO proveedorDTO) {
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
        return proveedorService.obtenerProveedorPorId(id)
                .map(proveedor -> {
                    Proveedor proveedorActivado = proveedorService.activarProveedor(id);
                    return ResponseEntity.ok(proveedorMapper.toDTO(proveedorActivado));
                })
                .orElse(ResponseEntity.notFound().build());
    }

    @PatchMapping("/{id}/desactivar")
    public ResponseEntity<ProveedorDTO> desactivar(@PathVariable Long id) {
        return proveedorService.obtenerProveedorPorId(id)
                .map(proveedor -> {
                    Proveedor proveedorDesactivado = proveedorService.desactivarProveedor(id);
                    return ResponseEntity.ok(proveedorMapper.toDTO(proveedorDesactivado));
                })
                .orElse(ResponseEntity.notFound().build());
    }

    @DeleteMapping("/{id}")
    public ResponseEntity<Void> eliminar(@PathVariable Long id) {
        proveedorService.eliminarProveedor(id);
        return ResponseEntity.noContent().build();
    }
}