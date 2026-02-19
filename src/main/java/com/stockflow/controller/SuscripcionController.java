package com.stockflow.controller;

import com.stockflow.dto.SuscripcionDTO;
import com.stockflow.entity.Suscripcion;
import com.stockflow.entity.Usuario;
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
import java.util.stream.Collectors;

@RestController
@RequestMapping("/suscripciones")
@RequiredArgsConstructor
public class SuscripcionController {

    private final SuscripcionService suscripcionService;
    private final UsuarioService usuarioService;

    private SuscripcionDTO convertToDTO(Suscripcion suscripcion) {
        return SuscripcionDTO.builder()
                .id(suscripcion.getId())
                .usuarioPrincipalId(suscripcion.getUsuarioPrincipal().getId())
                .planId(suscripcion.getPlanId())
                .precioMensual(suscripcion.getPrecioMensual())
                .preapprovalId(suscripcion.getPreapprovalId())
                .estado(suscripcion.getEstado())
                .metodoPago(suscripcion.getMetodoPago())
                .ultimos4Digitos(suscripcion.getUltimos4Digitos())
                .build();
    }

    @GetMapping
    public ResponseEntity<List<SuscripcionDTO>> obtenerTodas() {
        List<Suscripcion> suscripciones = suscripcionService.obtenerTodasSuscripciones();
        List<SuscripcionDTO> suscripcionesDTO = suscripciones.stream()
                .map(this::convertToDTO)
                .collect(Collectors.toList());
        return ResponseEntity.ok(suscripcionesDTO);
    }

    @GetMapping("/{id}")
    public ResponseEntity<SuscripcionDTO> obtenerPorId(@PathVariable Long id) {
        return suscripcionService.obtenerSuscripcionPorId(id)
                .map(this::convertToDTO)
                .map(ResponseEntity::ok)
                .orElse(ResponseEntity.notFound().build());
    }

    @GetMapping("/usuario/{usuarioId}")
    public ResponseEntity<SuscripcionDTO> obtenerPorUsuario(@PathVariable Long usuarioId) {
        return suscripcionService.obtenerSuscripcionPorUsuario(usuarioId)
                .map(this::convertToDTO)
                .map(ResponseEntity::ok)
                .orElse(ResponseEntity.notFound().build());
    }

    @GetMapping("/estado/{estado}")
    public ResponseEntity<List<SuscripcionDTO>> obtenerPorEstado(@PathVariable String estado) {
        List<Suscripcion> suscripciones = suscripcionService.obtenerSuscripcionesPorEstado(estado);
        List<SuscripcionDTO> suscripcionesDTO = suscripciones.stream()
                .map(this::convertToDTO)
                .collect(Collectors.toList());
        return ResponseEntity.ok(suscripcionesDTO);
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

        // GENERAR preapprovalId automáticamente ← AQUÍ
        String preapprovalId = generarPreapprovalId();
//        log.info("✅ PreapprovalId generado: {}", preapprovalId);

        // Crear suscripción
        Suscripcion suscripcion = Suscripcion.builder()
                .usuarioPrincipal(usuario)
                .planId(suscripcionDTO.getPlanId())
                .precioMensual(suscripcionDTO.getPrecioMensual())
                .preapprovalId(preapprovalId)
                .estado("ACTIVA")
                .metodoPago(suscripcionDTO.getMetodoPago())
                .ultimos4Digitos(suscripcionDTO.getUltimos4Digitos())
                .build();

        Suscripcion suscripcionCreada = suscripcionService.crearSuscripcion(suscripcion);
        return ResponseEntity.status(HttpStatus.CREATED).body(convertToDTO(suscripcionCreada));
    }

    private String generarPreapprovalId() {
        // Formato: PRE-YYYYMMDD-XXXXXX
        // Ejemplo: PRE-20260218-ABC123
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
    public ResponseEntity<SuscripcionDTO> actualizar(@PathVariable Long id, @Valid @RequestBody SuscripcionDTO suscripcionDTO) {
        try {
            Suscripcion suscripcion = suscripcionService.obtenerSuscripcionPorId(id)
                    .orElseThrow(() -> new ResourceNotFoundException("Suscripción no encontrada"));

            suscripcion.setPlanId(suscripcionDTO.getPlanId());
            suscripcion.setPrecioMensual(suscripcionDTO.getPrecioMensual());
            suscripcion.setMetodoPago(suscripcionDTO.getMetodoPago());
            suscripcion.setUltimos4Digitos(suscripcionDTO.getUltimos4Digitos());

            Suscripcion suscripcionActualizada = suscripcionService.actualizarSuscripcion(id, suscripcion);
            return ResponseEntity.ok(convertToDTO(suscripcionActualizada));
        } catch (RuntimeException e) {
            return ResponseEntity.notFound().build();
        }
    }

    @PutMapping("/{id}/cancelar")
    public ResponseEntity<SuscripcionDTO> cancelar(@PathVariable Long id) {
        Suscripcion suscripcion = suscripcionService.obtenerSuscripcionPorId(id)
                .orElseThrow(() -> new ResourceNotFoundException("Suscripción no encontrada"));

        suscripcion.setEstado("CANCELADA");
        Suscripcion suscripcionActualizada = suscripcionService.actualizarSuscripcion(id, suscripcion);
        return ResponseEntity.ok(convertToDTO(suscripcionActualizada));
    }

    @PutMapping("/{id}/activar")
    public ResponseEntity<SuscripcionDTO> activar(@PathVariable Long id) {
        Suscripcion suscripcionActivada = suscripcionService.activarSuscripcion(id);
        return ResponseEntity.ok(convertToDTO(suscripcionActivada));
    }

    @DeleteMapping("/{id}")
    public ResponseEntity<Void> eliminar(@PathVariable Long id) {
        suscripcionService.eliminarSuscripcion(id);
        return ResponseEntity.noContent().build();
    }
}