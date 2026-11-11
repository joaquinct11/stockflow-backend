package com.stockflow.controller;

import com.stockflow.dto.*;
import com.stockflow.entity.EstadoCita;
import com.stockflow.entity.TipoCita;
import com.stockflow.service.CitaService;
import java.util.List;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.time.LocalDateTime;

@Slf4j
@RestController
@RequestMapping("/citas")
@RequiredArgsConstructor
public class CitaController {

    private final CitaService citaService;

    /** Listado paginado con filtros opcionales. */
    @GetMapping
    @PreAuthorize("hasRole('ADMIN') or hasAuthority('PERM_VER_CITAS')")
    public ResponseEntity<CitaPageResponseDTO> listar(
            @RequestParam(defaultValue = "0")                           int           page,
            @RequestParam(defaultValue = "15")                          int           size,
            @RequestParam(required = false)                             EstadoCita    estado,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) LocalDateTime fechaInicio,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) LocalDateTime fechaFin,
            @RequestParam(required = false)                             Long          clienteId,
            @RequestParam(required = false)                             Long          mascotaId,
            @RequestParam(required = false)                             Long          usuarioTenantId,
            @RequestParam(required = false)                             Long          sucursalId,
            @RequestParam(defaultValue = "")                            String        q
    ) {
        return ResponseEntity.ok(citaService.listar(
                page, size, estado, fechaInicio, fechaFin,
                clienteId, mascotaId, usuarioTenantId, sucursalId, q));
    }

    /** Detalle de una cita. */
    @GetMapping("/{id}")
    @PreAuthorize("hasRole('ADMIN') or hasAuthority('PERM_VER_CITAS')")
    public ResponseEntity<CitaDTO> obtenerPorId(@PathVariable Long id) {
        return ResponseEntity.ok(citaService.obtenerPorId(id));
    }

    /** Crear nueva cita. El creadoPorId se resuelve automáticamente del SecurityContext. */
    @PostMapping
    @PreAuthorize("hasRole('ADMIN') or hasAuthority('PERM_CREAR_CITA')")
    public ResponseEntity<CitaDTO> crear(@Valid @RequestBody CrearCitaRequestDTO dto) {
        return ResponseEntity.status(HttpStatus.CREATED).body(citaService.crear(dto));
    }

    /** Actualizar / reprogramar una cita. */
    @PutMapping("/{id}")
    @PreAuthorize("hasRole('ADMIN') or hasAuthority('PERM_EDITAR_CITA')")
    public ResponseEntity<CitaDTO> actualizar(
            @PathVariable Long id,
            @Valid @RequestBody ActualizarCitaRequestDTO dto) {
        return ResponseEntity.ok(citaService.actualizar(id, dto));
    }

    /** Cambiar el estado de una cita. */
    @PatchMapping("/{id}/estado")
    @PreAuthorize("hasRole('ADMIN') or hasAuthority('PERM_CAMBIAR_ESTADO_CITA')")
    public ResponseEntity<CitaDTO> cambiarEstado(
            @PathVariable Long id,
            @Valid @RequestBody CambiarEstadoCitaRequestDTO dto) {
        return ResponseEntity.ok(citaService.cambiarEstado(id, dto));
    }

    /**
     * Profesionales elegibles para asignar a una cita.
     * Filtra por tenant (automático), rol según tipo de cita y sucursal activa.
     * VETERINARIO para citas clínicas; GROOMER para PELUQUERIA.
     */
    @GetMapping("/profesionales")
    @PreAuthorize("hasRole('ADMIN') or hasAuthority('PERM_VER_CITAS')")
    public ResponseEntity<List<ProfesionalElegibleDTO>> listarProfesionalesElegibles(
            @RequestParam(required = false) Long     sucursalId,
            @RequestParam(required = false) TipoCita tipo) {
        return ResponseEntity.ok(citaService.listarProfesionalesElegibles(sucursalId, tipo));
    }
}
