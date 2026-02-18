package com.stockflow.controller;

import com.stockflow.dto.RolDTO;
import com.stockflow.entity.Rol;
import com.stockflow.service.RolService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;
import jakarta.validation.Valid;
import java.util.List;
import java.util.stream.Collectors;

@RestController
@RequestMapping("/roles")
@RequiredArgsConstructor
public class RolController {

    private final RolService rolService;

    @GetMapping
    public ResponseEntity<List<RolDTO>> obtenerTodos() {
        List<Rol> roles = rolService.obtenerTodosRoles();
        List<RolDTO> rolesDTO = roles.stream()
                .map(rol -> RolDTO.builder()
                        .id(rol.getId())
                        .nombre(rol.getNombre())
                        .descripcion(rol.getDescripcion())
                        .build())
                .collect(Collectors.toList());
        return ResponseEntity.ok(rolesDTO);
    }

    @GetMapping("/{id}")
    public ResponseEntity<RolDTO> obtenerPorId(@PathVariable Long id) {
        return rolService.obtenerRolPorId(id)
                .map(rol -> RolDTO.builder()
                        .id(rol.getId())
                        .nombre(rol.getNombre())
                        .descripcion(rol.getDescripcion())
                        .build())
                .map(ResponseEntity::ok)
                .orElse(ResponseEntity.notFound().build());
    }

    @PostMapping
    public ResponseEntity<RolDTO> crear(@Valid @RequestBody RolDTO rolDTO) {
        Rol rol = Rol.builder()
                .nombre(rolDTO.getNombre())
                .descripcion(rolDTO.getDescripcion())
                .build();

        Rol rolCreado = rolService.crearRol(rol);

        RolDTO response = RolDTO.builder()
                .id(rolCreado.getId())
                .nombre(rolCreado.getNombre())
                .descripcion(rolCreado.getDescripcion())
                .build();

        return ResponseEntity.status(HttpStatus.CREATED).body(response);
    }

    @PutMapping("/{id}")
    public ResponseEntity<RolDTO> actualizar(@PathVariable Long id, @Valid @RequestBody RolDTO rolDTO) {
        try {
            Rol rol = Rol.builder()
                    .nombre(rolDTO.getNombre())
                    .descripcion(rolDTO.getDescripcion())
                    .build();

            Rol rolActualizado = rolService.actualizarRol(id, rol);

            RolDTO response = RolDTO.builder()
                    .id(rolActualizado.getId())
                    .nombre(rolActualizado.getNombre())
                    .descripcion(rolActualizado.getDescripcion())
                    .build();

            return ResponseEntity.ok(response);
        } catch (RuntimeException e) {
            return ResponseEntity.notFound().build();
        }
    }

    @DeleteMapping("/{id}")
    public ResponseEntity<Void> eliminar(@PathVariable Long id) {
        rolService.eliminarRol(id);
        return ResponseEntity.noContent().build();
    }
}