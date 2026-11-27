package com.stockflow.controller;

import com.stockflow.dto.gre.*;
import com.stockflow.service.GuiaRemisionService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.web.PageableDefault;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

@Slf4j
@RestController
@RequestMapping("/guias-remision")
@RequiredArgsConstructor
public class GuiaRemisionController {

    private final GuiaRemisionService guiaService;

    @GetMapping
    @PreAuthorize("hasAnyRole('ADMIN', 'GERENTE') or hasAuthority('PERM_VER_GRE')")
    public ResponseEntity<Page<GuiaRemisionListResponse>> listar(
            @RequestParam(required = false) String q,
            @RequestParam(required = false) com.stockflow.entity.gre.EstadoTributarioGRE estado,
            @RequestParam(required = false) Long sucursalId,
            @PageableDefault(size = 10, sort = "createdAt", direction = org.springframework.data.domain.Sort.Direction.DESC) Pageable pageable) {
        return ResponseEntity.ok(guiaService.listar(q, estado, sucursalId, pageable));
    }

    @GetMapping("/stats")
    @PreAuthorize("hasAnyRole('ADMIN', 'GERENTE') or hasAuthority('PERM_VER_GRE')")
    public ResponseEntity<GuiaRemisionStatsResponse> stats(
            @RequestParam(required = false) Long sucursalId) {
        return ResponseEntity.ok(guiaService.stats(sucursalId));
    }

    @GetMapping("/{id}")
    @PreAuthorize("hasAnyRole('ADMIN', 'GERENTE') or hasAuthority('PERM_VER_GRE')")
    public ResponseEntity<GuiaRemisionResponse> obtener(@PathVariable Long id) {
        return ResponseEntity.ok(guiaService.obtener(id));
    }

    @PostMapping
    @PreAuthorize("hasAnyRole('ADMIN', 'GERENTE') or hasAuthority('PERM_EMITIR_GRE')")
    public ResponseEntity<GuiaRemisionResponse> crearBorrador(
            @Valid @RequestBody CrearGuiaRemisionRequest request) {
        return ResponseEntity.status(HttpStatus.CREATED).body(guiaService.crearBorrador(request));
    }

    @PutMapping("/{id}")
    @PreAuthorize("hasRole('ADMIN') or hasAuthority('PERM_EDITAR_GRE_BORRADOR')")
    public ResponseEntity<GuiaRemisionResponse> editar(
            @PathVariable Long id,
            @Valid @RequestBody EditarBorradorRequest request) {
        return ResponseEntity.ok(guiaService.editarBorrador(id, request));
    }

    @DeleteMapping("/{id}")
    @PreAuthorize("hasRole('ADMIN') or hasAuthority('PERM_ELIMINAR_GRE_BORRADOR')")
    public ResponseEntity<Void> eliminar(@PathVariable Long id) {
        guiaService.eliminarBorrador(id);
        return ResponseEntity.noContent().build();
    }

    @PostMapping("/{id}/emitir")
    @PreAuthorize("hasAnyRole('ADMIN', 'GERENTE') or hasAuthority('PERM_EMITIR_GRE')")
    public ResponseEntity<GuiaRemisionResponse> emitir(@PathVariable Long id) {
        return ResponseEntity.ok(guiaService.emitirGRE(id));
    }

    @PostMapping("/{id}/confirmar-recepcion")
    @PreAuthorize("hasAnyRole('ADMIN', 'GERENTE') or hasAuthority('PERM_CONFIRMAR_RECEPCION_GRE')")
    public ResponseEntity<GuiaRemisionResponse> confirmarRecepcion(
            @PathVariable Long id,
            @RequestBody(required = false) ConfirmarRecepcionRequest request) {
        if (request == null) request = new ConfirmarRecepcionRequest();
        return ResponseEntity.ok(guiaService.confirmarRecepcion(id, request));
    }

    @PostMapping("/{id}/cancelar")
    @PreAuthorize("hasAnyRole('ADMIN', 'GERENTE') or hasAuthority('PERM_CANCELAR_GRE_BORRADOR')")
    public ResponseEntity<GuiaRemisionResponse> cancelar(@PathVariable Long id) {
        return ResponseEntity.ok(guiaService.cancelarBorrador(id));
    }

    @PostMapping("/{id}/recuperar")
    @PreAuthorize("hasRole('ADMIN') or hasAuthority('PERM_EDITAR_GRE_BORRADOR')")
    public ResponseEntity<GuiaRemisionResponse> recuperar(@PathVariable Long id) {
        return ResponseEntity.ok(guiaService.recuperarEmitiendo(id));
    }

    @PostMapping("/{id}/reconciliar")
    @PreAuthorize("hasRole('ADMIN')")
    public ResponseEntity<GuiaRemisionResponse> reconciliar(
            @PathVariable Long id,
            @Valid @RequestBody ReconciliarGRERequest request) {
        return ResponseEntity.ok(guiaService.reconciliarGREAceptada(id, request));
    }

    /**
     * F-22 Parte 4: consulta de solo lectura del estado de una GRE en Lucode.
     * Útil para diagnosticar GREs en ERROR antes de reconciliar.
     * No modifica ningún dato local.
     */
    @GetMapping("/{id}/consultar-lucode")
    @PreAuthorize("hasRole('ADMIN')")
    public ResponseEntity<com.stockflow.dto.gre.lucode.LucodeGREResponse> consultarEnLucode(
            @PathVariable Long id) {
        return ResponseEntity.ok(guiaService.consultarEstadoEnLucode(id));
    }

    @GetMapping("/{id}/pdf")
    @PreAuthorize("hasAnyRole('ADMIN', 'GERENTE') or hasAuthority('PERM_VER_GRE')")
    public ResponseEntity<byte[]> pdf(@PathVariable Long id) {
        GuiaRemisionResponse guia = guiaService.obtener(id);
        byte[] bytes = guiaService.generarPDF(id);
        String filename = "GRE-" + guia.getSerie() + "-"
                + String.format("%08d", guia.getCorrelativo()) + ".pdf";
        return ResponseEntity.ok()
                .header(HttpHeaders.CONTENT_DISPOSITION, "attachment; filename=\"" + filename + "\"")
                .contentType(MediaType.APPLICATION_PDF)
                .body(bytes);
    }
}
