package com.stockflow.controller;

import com.stockflow.dto.ComprobanteProveedorDTO;
import com.stockflow.dto.RecepcionDetalleRequestDTO;
import com.stockflow.dto.RecepcionDetalleResponseDTO;
import com.stockflow.dto.RecepcionRequestDTO;
import com.stockflow.dto.RecepcionResponseDTO;
import com.stockflow.service.RecepcionService;
import com.stockflow.util.TenantContext;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@Slf4j
@RestController
@RequestMapping("/recepciones")
@RequiredArgsConstructor
public class RecepcionController {

    private final RecepcionService recepcionService;

    /**
     * POST /api/recepciones — Create a recepcion (from OC or manual with proveedor).
     */
    @PostMapping
    @PreAuthorize("hasRole('ADMIN') or hasAuthority('PERM_CREAR_RECEPCION')")
    public ResponseEntity<RecepcionResponseDTO> crear(@Valid @RequestBody RecepcionRequestDTO request) {
        String tenantId = TenantContext.getCurrentTenant();
        Long usuarioId = TenantContext.getCurrentUserId();
        log.info("📥 Creando recepción para tenant={}", tenantId);
        RecepcionResponseDTO response = recepcionService.crearRecepcion(request, usuarioId, tenantId);
        return ResponseEntity.status(HttpStatus.CREATED).body(response);
    }

    /**
     * GET /api/recepciones — List recepciones for current tenant.
     */
    @GetMapping
    @PreAuthorize("hasAnyRole('ADMIN', 'GERENTE') or hasAuthority('PERM_VER_RECEPCIONES') or hasAuthority('PERM_CREAR_RECEPCION')")
    public ResponseEntity<List<RecepcionResponseDTO>> listar() {
        String tenantId = TenantContext.getCurrentTenant();
        return ResponseEntity.ok(recepcionService.listar(tenantId));
    }

    /**
     * GET /api/recepciones/{id} — Detail of a recepcion.
     */
    @GetMapping("/{id}")
    @PreAuthorize("hasAnyRole('ADMIN', 'GERENTE') or hasAuthority('PERM_VER_RECEPCIONES') or hasAuthority('PERM_CREAR_RECEPCION')")
    public ResponseEntity<RecepcionResponseDTO> obtenerPorId(@PathVariable Long id) {
        return recepcionService.obtenerPorId(id)
                .map(ResponseEntity::ok)
                .orElse(ResponseEntity.notFound().build());
    }

    /**
     * POST /api/recepciones/{id}/items — Upsert an item (producto, cantidad_recibida, fecha_vencimiento).
     */
    @PostMapping("/{id}/items")
    @PreAuthorize("hasRole('ADMIN') or hasAuthority('PERM_CREAR_RECEPCION')")
    public ResponseEntity<RecepcionDetalleResponseDTO> upsertItem(
            @PathVariable Long id,
            @Valid @RequestBody RecepcionDetalleRequestDTO request) {
        log.info("📝 Upsert item recepción id={}", id);
        return ResponseEntity.ok(recepcionService.upsertItem(id, request));
    }

    /**
     * POST /api/recepciones/{id}/comprobante — Save supplier voucher data.
     */
    @PostMapping("/{id}/comprobante")
    @PreAuthorize("hasRole('ADMIN') or hasAuthority('PERM_CREAR_RECEPCION')")
    public ResponseEntity<RecepcionResponseDTO> guardarComprobante(
            @PathVariable Long id,
            @Valid @RequestBody ComprobanteProveedorDTO dto) {
        log.info("🧾 Guardando comprobante recepción id={}", id);
        return ResponseEntity.ok(recepcionService.guardarComprobante(id, dto));
    }

    /**
     * POST /api/recepciones/{id}/confirmar — Confirm recepcion, validate, and generate inventory movements.
     */
    @PostMapping("/{id}/confirmar")
    @PreAuthorize("hasRole('ADMIN') or hasAuthority('PERM_CONFIRMAR_RECEPCION')")
    public ResponseEntity<RecepcionResponseDTO> confirmar(@PathVariable Long id) {
        Long usuarioId = TenantContext.getCurrentUserId();
        log.info("✅ Confirmando recepción id={}", id);
        return ResponseEntity.ok(recepcionService.confirmar(id, usuarioId));
    }
}
