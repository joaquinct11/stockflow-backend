package com.stockflow.controller;

import com.stockflow.dto.ActualizarMascotaRequestDTO;
import com.stockflow.dto.CrearMascotaRequestDTO;
import com.stockflow.dto.MascotaDTO;
import com.stockflow.dto.MascotaPageResponseDTO;
import com.stockflow.dto.MascotaResumenDTO;
import com.stockflow.dto.MascotaStatsDTO;
import com.stockflow.service.MascotaService;

import java.util.List;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

@Slf4j
@RestController
@RequestMapping("/mascotas")
@RequiredArgsConstructor
public class MascotaController {

    private final MascotaService mascotaService;

    @GetMapping("/stats")
    @PreAuthorize("hasRole('ADMIN') or hasAuthority('PERM_VER_MASCOTAS')")
    public ResponseEntity<MascotaStatsDTO> getStats() {
        return ResponseEntity.ok(mascotaService.getStats());
    }

    @GetMapping
    @PreAuthorize("hasRole('ADMIN') or hasAuthority('PERM_VER_MASCOTAS')")
    public ResponseEntity<MascotaPageResponseDTO> listar(
            @RequestParam(defaultValue = "0")       int    page,
            @RequestParam(defaultValue = "15")      int    size,
            @RequestParam(defaultValue = "ACTIVAS") String estado,
            @RequestParam(defaultValue = "")        String search) {
        return ResponseEntity.ok(mascotaService.listar(page, size, estado, search));
    }

    @GetMapping("/{id}")
    @PreAuthorize("hasRole('ADMIN') or hasAuthority('PERM_VER_MASCOTAS')")
    public ResponseEntity<MascotaDTO> obtenerPorId(@PathVariable Long id) {
        return ResponseEntity.ok(mascotaService.obtenerPorId(id));
    }

    @GetMapping("/cliente/{clienteId}")
    @PreAuthorize("hasRole('ADMIN') or hasAuthority('PERM_VER_MASCOTAS')")
    public ResponseEntity<List<MascotaResumenDTO>> listarPorCliente(@PathVariable Long clienteId) {
        return ResponseEntity.ok(mascotaService.listarPorCliente(clienteId));
    }

    @GetMapping("/buscar")
    @PreAuthorize("hasRole('ADMIN') or hasAuthority('PERM_VER_MASCOTAS')")
    public ResponseEntity<List<MascotaResumenDTO>> buscarPorNombre(@RequestParam String nombre) {
        return ResponseEntity.ok(mascotaService.buscarPorNombre(nombre));
    }

    @PostMapping
    @PreAuthorize("hasRole('ADMIN') or hasAuthority('PERM_CREAR_MASCOTA')")
    public ResponseEntity<MascotaDTO> crear(@Valid @RequestBody CrearMascotaRequestDTO dto) {
        log.info("🐾 POST /mascotas");
        return ResponseEntity.status(HttpStatus.CREATED).body(mascotaService.crearMascota(dto));
    }

    @PutMapping("/{id}")
    @PreAuthorize("hasRole('ADMIN') or hasAuthority('PERM_EDITAR_MASCOTA')")
    public ResponseEntity<MascotaDTO> actualizar(
            @PathVariable Long id,
            @Valid @RequestBody ActualizarMascotaRequestDTO dto) {
        return ResponseEntity.ok(mascotaService.actualizar(id, dto));
    }

    @PatchMapping("/{id}/activar")
    @PreAuthorize("hasRole('ADMIN') or hasAuthority('PERM_CAMBIAR_ESTADO_MASCOTA')")
    public ResponseEntity<MascotaDTO> activar(@PathVariable Long id) {
        return ResponseEntity.ok(mascotaService.activar(id));
    }

    @PatchMapping("/{id}/desactivar")
    @PreAuthorize("hasRole('ADMIN') or hasAuthority('PERM_CAMBIAR_ESTADO_MASCOTA')")
    public ResponseEntity<MascotaDTO> desactivar(@PathVariable Long id) {
        return ResponseEntity.ok(mascotaService.desactivar(id));
    }
}
