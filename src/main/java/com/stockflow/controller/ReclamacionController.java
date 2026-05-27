package com.stockflow.controller;

import com.stockflow.service.EmailService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

import java.util.List;
import java.util.Map;

/**
 * Endpoint público para el Libro de Reclamaciones Virtual.
 * No requiere autenticación — cubierto por /auth/** en SecurityConfig.
 */
@Slf4j
@RestController
@RequestMapping("/auth/reclamaciones")
@RequiredArgsConstructor
public class ReclamacionController {

    private final EmailService emailService;

    @PostMapping(consumes = { "multipart/form-data", "application/x-www-form-urlencoded" })
    public ResponseEntity<Map<String, String>> enviarReclamacion(
            @RequestParam                                        String tipo,
            @RequestParam                                        String nombre,
            @RequestParam                                        String apellido,
            @RequestParam                                        String dni,
            @RequestParam                                        String correo,
            @RequestParam(required = false)                      String telefono,
            @RequestParam(required = false)                      String pedido,
            @RequestParam(required = false)                      String monto,
            @RequestParam                                        String descripcion,
            @RequestParam(name = "archivos", required = false)   List<MultipartFile> archivos) {

        log.info("📋 [Reclamaciones] {} de {} {} ({}) — adjuntos: {}",
                tipo, nombre, apellido, correo,
                archivos != null ? archivos.size() : 0);

        emailService.enviarReclamacion(tipo, nombre, apellido, dni, correo,
                telefono, pedido, monto, descripcion, archivos);

        return ResponseEntity.ok(Map.of(
                "mensaje", "Reclamación recibida. Te responderemos en un plazo máximo de 15 días hábiles."
        ));
    }
}
