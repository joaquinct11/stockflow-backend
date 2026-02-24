package com.stockflow.controller;

import com.stockflow.dto.SuscripcionDTO;
import com.stockflow.entity.Suscripcion;
import com.stockflow.entity.Usuario;
import com.stockflow.mapper.SuscripcionMapper;
import com.stockflow.service.SuscripcionService;
import com.stockflow.service.UsuarioService;
import com.stockflow.exception.ResourceNotFoundException;
import com.stockflow.exception.BadRequestException;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import jakarta.validation.Valid;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.List;

@RestController
@RequestMapping("/suscripciones")
@RequiredArgsConstructor
public class SuscripcionController {

    private final SuscripcionService suscripcionService;
    private final UsuarioService usuarioService;
    private final SuscripcionMapper suscripcionMapper;

    @GetMapping
    public ResponseEntity<List<SuscripcionDTO>> obtenerTodas() {
        return ResponseEntity.ok(
                suscripcionMapper.toDTOList(suscripcionService.obtenerTodasSuscripciones())
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
        return suscripcionService.obtenerSuscripcionPorUsuario(usuarioId)
                .map(suscripcionMapper::toDTO)
                .map(ResponseEntity::ok)
                .orElse(ResponseEntity.notFound().build());
    }

    @GetMapping("/estado/{estado}")
    public ResponseEntity<List<SuscripcionDTO>> obtenerPorEstado(@PathVariable String estado) {
        return ResponseEntity.ok(
                suscripcionMapper.toDTOList(suscripcionService.obtenerSuscripcionesPorEstado(estado))
        );
    }

    @PostMapping
    public ResponseEntity<SuscripcionDTO> crear(@Valid @RequestBody SuscripcionDTO suscripcionDTO) {
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

        Suscripcion suscripcionCreada = suscripcionService.crearSuscripcion(suscripcion);
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(suscripcionMapper.toDTO(suscripcionCreada));
    }

    private String generarPreapprovalId() {
        // Formato: PRE-YYYYMMDD-XXXXXX
        LocalDateTime ahora = LocalDateTime.now();
        DateTimeFormatter formatter = DateTimeFormatter.ofPattern("yyyyMMdd");
        String fecha = ahora.format(formatter);

        // Generar 6 caracteres aleatorios
        String caracteres = "ABCDEFGHIJKLMNOPQRSTUVWXYZ0123456789";
        StringBuilder sb = new StringBuilder();
        java.util.Random random = new java.util.Random();
        for (int i = 0; i < 6; i++) {
            sb.append(caracteres.charAt(random.nextInt(caracteres.length())));
        }

        return "PRE-" + fecha + "-" + sb.toString();
    }

    @PutMapping("/{id}")
    public ResponseEntity<SuscripcionDTO> actualizar(
            @PathVariable Long id,
            @Valid @RequestBody SuscripcionDTO suscripcionDTO) {
        return suscripcionService.obtenerSuscripcionPorId(id)
                .map(suscripcion -> {
                    suscripcion.setPlanId(suscripcionDTO.getPlanId());
                    suscripcion.setPrecioMensual(suscripcionDTO.getPrecioMensual());
                    suscripcion.setMetodoPago(suscripcionDTO.getMetodoPago());
                    suscripcion.setUltimos4Digitos(suscripcionDTO.getUltimos4Digitos());

                    Suscripcion suscripcionActualizada = suscripcionService.actualizarSuscripcion(id, suscripcion);
                    return ResponseEntity.ok(suscripcionMapper.toDTO(suscripcionActualizada));
                })
                .orElse(ResponseEntity.notFound().build());
    }

    @PutMapping("/{id}/cancelar")
    public ResponseEntity<SuscripcionDTO> cancelar(@PathVariable Long id) {
        return suscripcionService.obtenerSuscripcionPorId(id)
                .map(suscripcion -> {
                    suscripcion.setEstado("CANCELADA");
                    Suscripcion suscripcionActualizada = suscripcionService.actualizarSuscripcion(id, suscripcion);
                    return ResponseEntity.ok(suscripcionMapper.toDTO(suscripcionActualizada));
                })
                .orElseThrow(() -> new ResourceNotFoundException("Suscripción no encontrada"));
    }

    @PutMapping("/{id}/activar")
    public ResponseEntity<SuscripcionDTO> activar(@PathVariable Long id) {
        Suscripcion suscripcionActivada = suscripcionService.activarSuscripcion(id);
        return ResponseEntity.ok(suscripcionMapper.toDTO(suscripcionActivada));
    }

    @DeleteMapping("/{id}")
    public ResponseEntity<Void> eliminar(@PathVariable Long id) {
        suscripcionService.eliminarSuscripcion(id);
        return ResponseEntity.noContent().build();
    }
}