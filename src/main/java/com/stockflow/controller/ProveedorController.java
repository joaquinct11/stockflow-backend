package com.stockflow.controller;

import com.stockflow.dto.ProveedorDTO;
import com.stockflow.entity.Proveedor;
import com.stockflow.service.ProveedorService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import jakarta.validation.Valid;

import java.util.List;
import java.util.stream.Collectors;

@RestController
@RequestMapping("/proveedores")
@RequiredArgsConstructor
public class ProveedorController {

    private final ProveedorService proveedorService;

    private ProveedorDTO convertToDTO(Proveedor proveedor) {
        return ProveedorDTO.builder()
                .id(proveedor.getId())
                .nombre(proveedor.getNombre())
                .ruc(proveedor.getRuc())
                .contacto(proveedor.getContacto())
                .telefono(proveedor.getTelefono())
                .email(proveedor.getEmail())
                .direccion(proveedor.getDireccion())
                .activo(proveedor.getActivo())
                .tenantId(proveedor.getTenantId())
                .build();
    }

    private Proveedor convertToEntity(ProveedorDTO dto) {
        return Proveedor.builder()
                .nombre(dto.getNombre())
                .ruc(dto.getRuc())
                .contacto(dto.getContacto())
                .telefono(dto.getTelefono())
                .email(dto.getEmail())
                .direccion(dto.getDireccion())
                .activo(dto.getActivo() != null ? dto.getActivo() : true)
                .tenantId(dto.getTenantId())
                .build();
    }

    // ✅ CAMBIAR - Ahora devuelve TODOS los proveedores (activos e inactivos)
    @GetMapping
    public ResponseEntity<List<ProveedorDTO>> obtenerTodos() {
        List<Proveedor> proveedores = proveedorService.obtenerTodosProveedores();
        List<ProveedorDTO> proveedoresDTO = proveedores.stream()
                .map(this::convertToDTO)
                .collect(Collectors.toList());
        return ResponseEntity.ok(proveedoresDTO);
    }

    // ✅ NUEVO - Obtener solo proveedores activos
    @GetMapping("/activos")
    public ResponseEntity<List<ProveedorDTO>> obtenerActivos() {
        List<Proveedor> proveedores = proveedorService.obtenerProveedoresActivos();
        List<ProveedorDTO> proveedoresDTO = proveedores.stream()
                .map(this::convertToDTO)
                .collect(Collectors.toList());
        return ResponseEntity.ok(proveedoresDTO);
    }

    @GetMapping("/{id}")
    public ResponseEntity<ProveedorDTO> obtenerPorId(@PathVariable Long id) {
        return proveedorService.obtenerProveedorPorId(id)
                .map(this::convertToDTO)
                .map(ResponseEntity::ok)
                .orElse(ResponseEntity.notFound().build());
    }

    @GetMapping("/ruc/{ruc}")
    public ResponseEntity<ProveedorDTO> obtenerPorRuc(@PathVariable String ruc) {
        return proveedorService.obtenerProveedorPorRuc(ruc)
                .map(this::convertToDTO)
                .map(ResponseEntity::ok)
                .orElse(ResponseEntity.notFound().build());
    }

    @GetMapping("/buscar")
    public ResponseEntity<List<ProveedorDTO>> buscarPorNombre(@RequestParam String nombre) {
        List<Proveedor> proveedores = proveedorService.buscarProveedoresPorNombre(nombre);
        List<ProveedorDTO> proveedoresDTO = proveedores.stream()
                .map(this::convertToDTO)
                .collect(Collectors.toList());
        return ResponseEntity.ok(proveedoresDTO);
    }

    @PostMapping
    public ResponseEntity<ProveedorDTO> crear(@Valid @RequestBody ProveedorDTO proveedorDTO) {
        Proveedor proveedor = convertToEntity(proveedorDTO);
        Proveedor proveedorCreado = proveedorService.crearProveedor(proveedor);
        return ResponseEntity.status(HttpStatus.CREATED).body(convertToDTO(proveedorCreado));
    }

    @PutMapping("/{id}")
    public ResponseEntity<ProveedorDTO> actualizar(
            @PathVariable Long id,
            @Valid @RequestBody ProveedorDTO proveedorDTO) {
        try {
            Proveedor proveedor = convertToEntity(proveedorDTO);
            Proveedor proveedorActualizado = proveedorService.actualizarProveedor(id, proveedor);
            return ResponseEntity.ok(convertToDTO(proveedorActualizado));
        } catch (RuntimeException e) {
            return ResponseEntity.notFound().build();
        }
    }

    // ✅ NUEVO - Activar proveedor
    @PatchMapping("/{id}/activar")
    public ResponseEntity<ProveedorDTO> activar(@PathVariable Long id) {
        try {
            Proveedor proveedorActivado = proveedorService.activarProveedor(id);
            return ResponseEntity.ok(convertToDTO(proveedorActivado));
        } catch (RuntimeException e) {
            return ResponseEntity.notFound().build();
        }
    }

    // ✅ NUEVO - Desactivar proveedor
    @PatchMapping("/{id}/desactivar")
    public ResponseEntity<ProveedorDTO> desactivar(@PathVariable Long id) {
        try {
            Proveedor proveedorDesactivado = proveedorService.desactivarProveedor(id);
            return ResponseEntity.ok(convertToDTO(proveedorDesactivado));
        } catch (RuntimeException e) {
            return ResponseEntity.notFound().build();
        }
    }

    @DeleteMapping("/{id}")
    public ResponseEntity<Void> eliminar(@PathVariable Long id) {
        proveedorService.eliminarProveedor(id);
        return ResponseEntity.noContent().build();
    }
}