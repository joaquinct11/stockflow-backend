package com.stockflow.controller;

import com.stockflow.dto.ActualizarEquipoRequestDTO;
import com.stockflow.dto.CrearEquipoRequestDTO;
import com.stockflow.dto.EquipoClienteDTO;
import com.stockflow.dto.EquipoPageResponseDTO;
import com.stockflow.dto.EquipoStatsDTO;
import com.stockflow.service.EquipoClienteService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/equipos")
@RequiredArgsConstructor
public class EquipoClienteController {

    private final EquipoClienteService equipoService;

    @GetMapping("/stats")
    @PreAuthorize("hasRole('ADMIN') or hasAuthority('PERM_VER_EQUIPOS')")
    public ResponseEntity<EquipoStatsDTO> getStats() {
        return ResponseEntity.ok(equipoService.getStats());
    }

    @GetMapping
    @PreAuthorize("hasRole('ADMIN') or hasAuthority('PERM_VER_EQUIPOS')")
    public ResponseEntity<EquipoPageResponseDTO> listar(
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size,
            @RequestParam(required = false) String estado,
            @RequestParam(required = false) String search,
            @RequestParam(required = false) Long clienteId) {
        return ResponseEntity.ok(equipoService.listar(page, size, estado, search, clienteId));
    }

    @GetMapping("/{id}")
    @PreAuthorize("hasRole('ADMIN') or hasAuthority('PERM_VER_EQUIPOS')")
    public ResponseEntity<EquipoClienteDTO> obtenerPorId(@PathVariable Long id) {
        return ResponseEntity.ok(equipoService.obtenerPorId(id));
    }

    @PostMapping
    @PreAuthorize("hasRole('ADMIN') or hasAuthority('PERM_CREAR_EQUIPO')")
    public ResponseEntity<EquipoClienteDTO> crear(@Valid @RequestBody CrearEquipoRequestDTO dto) {
        return ResponseEntity.status(HttpStatus.CREATED).body(equipoService.crear(dto));
    }

    @PutMapping("/{id}")
    @PreAuthorize("hasRole('ADMIN') or hasAuthority('PERM_EDITAR_EQUIPO')")
    public ResponseEntity<EquipoClienteDTO> actualizar(
            @PathVariable Long id,
            @Valid @RequestBody ActualizarEquipoRequestDTO dto) {
        return ResponseEntity.ok(equipoService.actualizar(id, dto));
    }

    @PatchMapping("/{id}/activar")
    @PreAuthorize("hasRole('ADMIN') or hasAuthority('PERM_CAMBIAR_ESTADO_EQUIPO')")
    public ResponseEntity<Void> activar(@PathVariable Long id) {
        equipoService.activar(id);
        return ResponseEntity.noContent().build();
    }

    @PatchMapping("/{id}/desactivar")
    @PreAuthorize("hasRole('ADMIN') or hasAuthority('PERM_CAMBIAR_ESTADO_EQUIPO')")
    public ResponseEntity<Void> desactivar(@PathVariable Long id) {
        equipoService.desactivar(id);
        return ResponseEntity.noContent().build();
    }
}
