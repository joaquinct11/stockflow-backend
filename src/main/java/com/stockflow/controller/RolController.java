package com.stockflow.controller;

import com.stockflow.dto.RolDTO;
import com.stockflow.entity.Rol;
import com.stockflow.mapper.RolMapper;
import com.stockflow.service.RolService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import jakarta.validation.Valid;
import java.util.List;

@RestController
@RequestMapping("/roles")
@RequiredArgsConstructor
public class RolController {

    private final RolService rolService;
    private final RolMapper rolMapper;

    @GetMapping
    public ResponseEntity<List<RolDTO>> obtenerTodos() {
        return ResponseEntity.ok(
                rolMapper.toDTOList(rolService.obtenerTodosRoles())
        );
    }

    @GetMapping("/{id}")
    public ResponseEntity<RolDTO> obtenerPorId(@PathVariable Long id) {
        return rolService.obtenerRolPorId(id)
                .map(rolMapper::toDTO)
                .map(ResponseEntity::ok)
                .orElse(ResponseEntity.notFound().build());
    }

    @PostMapping
    public ResponseEntity<RolDTO> crear(@Valid @RequestBody RolDTO rolDTO) {
        Rol rol = rolMapper.toEntity(rolDTO);
        Rol rolCreado = rolService.crearRol(rol);
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(rolMapper.toDTO(rolCreado));
    }

    @PutMapping("/{id}")
    public ResponseEntity<RolDTO> actualizar(
            @PathVariable Long id,
            @Valid @RequestBody RolDTO rolDTO) {
        return rolService.obtenerRolPorId(id)
                .map(rolExistente -> {
                    rolMapper.updateEntityFromDTO(rolDTO, rolExistente);
                    Rol rolActualizado = rolService.actualizarRol(id, rolExistente);
                    return ResponseEntity.ok(rolMapper.toDTO(rolActualizado));
                })
                .orElse(ResponseEntity.notFound().build());
    }

    @DeleteMapping("/{id}")
    public ResponseEntity<Void> eliminar(@PathVariable Long id) {
        rolService.eliminarRol(id);
        return ResponseEntity.noContent().build();
    }
}