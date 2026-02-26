package com.stockflow.controller;

import com.stockflow.dto.SuscripcionDTO;
import com.stockflow.entity.Suscripcion;
import com.stockflow.entity.Usuario;
import com.stockflow.mapper.SuscripcionMapper;
import com.stockflow.service.SuscripcionService;
import com.stockflow.service.UsuarioService;
import com.stockflow.util.TenantContext;
import com.stockflow.exception.ResourceNotFoundException;
import com.stockflow.exception.BadRequestException;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import jakarta.validation.Valid;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.List;
import java.util.Random;

@Slf4j
@RestController
@RequestMapping("/suscripciones")
@RequiredArgsConstructor
public class SuscripcionController {

    private final SuscripcionService suscripcionService;
    private final UsuarioService usuarioService;
    private final SuscripcionMapper suscripcionMapper;

    /**
     * ✅ ACTUALIZADO: Obtiene suscripciones del tenant actual
     */
    @GetMapping
    public ResponseEntity<List<SuscripcionDTO>> obtenerTodas() {
        String tenantId = TenantContext.getCurrentTenant();
        log.info("💳 Obteniendo suscripciones para tenant: {}", tenantId);

        return ResponseEntity.ok(
                suscripcionMapper.toDTOList(suscripcionService.obtenerSuscripcionesPorTenant(tenantId))
        );
    }

    @GetMapping("/{id}")
    public ResponseEntity<SuscripcionDTO> obtenerPorId(@PathVariable Long id) {
        return suscripcionService.obtenerSuscripcionPorId(id)
                .map(suscripcionMapper::toDTO)
                .map(ResponseEntity::ok)
                .orElse(ResponseEntity.notFound().build());
    }

    @GetMapping("/usuario/{usuarioId}")
    public ResponseEntity<SuscripcionDTO> obtenerPorUsuario(@PathVariable Long usuarioId) {
        log.info("👤 Obteniendo suscripción del usuario: {}", usuarioId);
        return suscripcionService.obtenerSuscripcionPorUsuario(usuarioId)
                .map(suscripcionMapper::toDTO)
                .map(ResponseEntity::ok)
                .orElse(ResponseEntity.notFound().build());
    }

    @GetMapping("/estado/{estado}")
    public ResponseEntity<List<SuscripcionDTO>> obtenerPorEstado(@PathVariable String estado) {
        String tenantId = TenantContext.getCurrentTenant();
        log.info("🔍 Obteniendo suscripciones con estado: {} para tenant: {}", estado, tenantId);

        return ResponseEntity.ok(
                suscripcionMapper.toDTOList(suscripcionService.obtenerSuscripcionesPorEstadoYTenant(estado, tenantId))
        );
    }

    /**
     * ✅ ACTUALIZADO: Setea tenantId automáticamente
     */
    @PostMapping
    public ResponseEntity<SuscripcionDTO> crear(@Valid @RequestBody SuscripcionDTO suscripcionDTO) {
        String tenantId = TenantContext.getCurrentTenant();
        log.info("➕ Creando suscripción para tenant: {}", tenantId);

        // Validar usuario principal
        Usuario usuario = usuarioService.obtenerUsuarioPorId(suscripcionDTO.getUsuarioPrincipalId())
                .orElseThrow(() -> new ResourceNotFoundException("Usuario no encontrado"));

        // Validar que no exista otra suscripción activa
        suscripcionService.obtenerSuscripcionPorUsuario(suscripcionDTO.getUsuarioPrincipalId())
                .ifPresent(suscripcion -> {
                    if ("ACTIVA".equals(suscripcion.getEstado())) {
                        throw new BadRequestException("El usuario ya tiene una suscripción activa");
                    }
                });

        // Validar plan
        if (!suscripcionDTO.getPlanId().matches("FREE|BASICO|PRO")) {
            throw new BadRequestException("Plan no válido. Use: FREE, BASICO, PRO");
        }

        // Generar preapprovalId automáticamente
        String preapprovalId = generarPreapprovalId();

        // Crear suscripción usando mapper
        Suscripcion suscripcion = suscripcionMapper.toEntity(suscripcionDTO);
        suscripcion.setUsuarioPrincipal(usuario);
        suscripcion.setPreapprovalId(preapprovalId);
        suscripcion.setEstado("ACTIVA");
        suscripcion.setTenantId(tenantId);

        Suscripcion suscripcionCreada = suscripcionService.crearSuscripcion(suscripcion);

        log.info("✅ Suscripción creada: Plan {} para tenant {}", suscripcionCreada.getPlanId(), tenantId);

        return ResponseEntity.status(HttpStatus.CREATED)
                .body(suscripcionMapper.toDTO(suscripcionCreada));
    }

    @PatchMapping("/{id}/cancelar")
    public ResponseEntity<SuscripcionDTO> cancelar(@PathVariable Long id) {
        log.info("🗑️ Cancelando suscripción ID: {}", id);
        return suscripcionService.obtenerSuscripcionPorId(id)
                .map(suscripcion -> {
                    suscripcion.setEstado("CANCELADA");
                    Suscripcion suscripcionActualizada = suscripcionService.actualizarSuscripcion(id, suscripcion);
                    return ResponseEntity.ok(suscripcionMapper.toDTO(suscripcionActualizada));
                })
                .orElseThrow(() -> new ResourceNotFoundException("Suscripción no encontrada"));
    }

    @PatchMapping("/{id}/activar")
    public ResponseEntity<SuscripcionDTO> activar(@PathVariable Long id) {
        log.info("🗑️ Activando suscripción ID: {}", id);
        Suscripcion suscripcionActivada = suscripcionService.activarSuscripcion(id);
        return ResponseEntity.ok(suscripcionMapper.toDTO(suscripcionActivada));
    }

    @DeleteMapping("/{id}")
    public ResponseEntity<Void> eliminar(@PathVariable Long id) {
        log.info("🗑️ Eliminando suscripción ID: {}", id);
        suscripcionService.eliminarSuscripcion(id);
        return ResponseEntity.noContent().build();
    }

    private String generarPreapprovalId() {
        // Formato: PRE-YYYYMMDD-XXXXXX
        LocalDateTime ahora = LocalDateTime.now();
        DateTimeFormatter formatter = DateTimeFormatter.ofPattern("yyyyMMdd");
        String fecha = ahora.format(formatter);

        Random random = new Random();
        int numero = random.nextInt(999999);

        return String.format("PRE-%s-%06d", fecha, numero);
    }
}