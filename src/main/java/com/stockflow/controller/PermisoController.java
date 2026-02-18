package com.stockflow.controller;

import com.stockflow.dto.PermisoDTO;
import com.stockflow.entity.Permiso;
import com.stockflow.entity.Rol;
import com.stockflow.service.PermisoService;
import com.stockflow.service.RolService;
import com.stockflow.exception.ResourceNotFoundException;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import jakarta.validation.Valid;
import java.util.List;
import java.util.stream.Collectors;

@RestController
@RequestMapping("/permisos")
@RequiredArgsConstructor
public class PermisoController {

    private final PermisoService permisoService;
    private final RolService rolService;

    private PermisoDTO convertToDTO(Permiso permiso) {
        return PermisoDTO.builder()
                .id(permiso.getId())
                .nombre(permiso.getNombre())
                .descripcion(permiso.getDescripcion())
                .rolId(permiso.getRol() != null ? permiso.getRol().getId() : null)
                .build();
    }

    @GetMapping
    public ResponseEntity<List<PermisoDTO>> obtenerTodos() {
        List<Permiso> permisos = permisoService.obtenerTodosPermisos();
        List<PermisoDTO> permisosDTO = permisos.stream()
                .map(this::convertToDTO)
                .collect(Collectors.toList());
        return ResponseEntity.ok(permisosDTO);
    }

    @GetMapping("/{id}")
    public ResponseEntity<PermisoDTO> obtenerPorId(@PathVariable Long id) {
        return permisoService.obtenerPermisoPorId(id)
                .map(this::convertToDTO)
                .map(ResponseEntity::ok)
                .orElse(ResponseEntity.notFound().build());
    }

    @GetMapping("/rol/{rolId}")
    public ResponseEntity<List<PermisoDTO>> obtenerPorRol(@PathVariable Long rolId) {
        List<Permiso> permisos = permisoService.obtenerPermisosPorRol(rolId);
        List<PermisoDTO> permisosDTO = permisos.stream()
                .map(this::convertToDTO)
                .collect(Collectors.toList());
        return ResponseEntity.ok(permisosDTO);
    }

    @GetMapping("/nombre/{nombre}")
    public ResponseEntity<PermisoDTO> obtenerPorNombre(@PathVariable String nombre) {
        return permisoService.obtenerPermisoPorNombre(nombre)
                .map(this::convertToDTO)
                .map(ResponseEntity::ok)
                .orElse(ResponseEntity.notFound().build());
    }

    @PostMapping
    public ResponseEntity<PermisoDTO> crear(@Valid @RequestBody PermisoDTO permisoDTO) {
        Rol rol = null;

        if (permisoDTO.getRolId() != null) {
            rol = rolService.obtenerRolPorId(permisoDTO.getRolId())
                    .orElseThrow(() -> new ResourceNotFoundException("Rol no encontrado"));
        }

        Permiso permiso = Permiso.builder()
                .nombre(permisoDTO.getNombre())
                .descripcion(permisoDTO.getDescripcion())
                .rol(rol)
                .build();

        Permiso permisoCreado = permisoService.crearPermiso(permiso);
        return ResponseEntity.status(HttpStatus.CREATED).body(convertToDTO(permisoCreado));
    }

    @PutMapping("/{id}")
    public ResponseEntity<PermisoDTO> actualizar(@PathVariable Long id, @Valid @RequestBody PermisoDTO permisoDTO) {
        try {
            Permiso permiso = permisoService.obtenerPermisoPorId(id)
                    .orElseThrow(() -> new ResourceNotFoundException("Permiso no encontrado"));

            permiso.setNombre(permisoDTO.getNombre());
            permiso.setDescripcion(permisoDTO.getDescripcion());

            if (permisoDTO.getRolId() != null) {
                Rol rol = rolService.obtenerRolPorId(permisoDTO.getRolId())
                        .orElseThrow(() -> new ResourceNotFoundException("Rol no encontrado"));
                permiso.setRol(rol);
            }

            Permiso permisoActualizado = permisoService.actualizarPermiso(id, permiso);
            return ResponseEntity.ok(convertToDTO(permisoActualizado));
        } catch (RuntimeException e) {
            return ResponseEntity.notFound().build();
        }
    }

    @DeleteMapping("/{id}")
    public ResponseEntity<Void> eliminar(@PathVariable Long id) {
        permisoService.eliminarPermiso(id);
        return ResponseEntity.noContent().build();
    }
}