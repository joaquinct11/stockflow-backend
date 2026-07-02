package com.stockflow.controller;

import com.stockflow.dto.ComprobanteDTO;
import com.stockflow.dto.EmitirComprobanteRequest;
import com.stockflow.service.ComprobanteService;
import com.stockflow.service.impl.PdfComprobanteService;
import com.stockflow.util.TenantContext;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.ContentDisposition;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;

import java.time.LocalDateTime;
import java.util.List;

@Slf4j
@RestController
@RequestMapping("/facturacion/comprobantes")
@RequiredArgsConstructor
public class ComprobanteController {

    private final ComprobanteService    comprobanteService;
    private final PdfComprobanteService pdfComprobanteService;

    /**
     * List comprobantes for the current tenant with optional filters.
     * PERM_VER_FACTURACION → todos; PERM_VER_MIS_FACTURACION → solo los propios.
     */
    @GetMapping
    @PreAuthorize("hasRole('ADMIN') or hasAuthority('PERM_VER_FACTURACION') or hasAuthority('PERM_VER_MIS_FACTURACION')")
    public ResponseEntity<List<ComprobanteDTO>> listar(
            @RequestParam(required = false) String tipo,
            @RequestParam(required = false) String estado,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) LocalDateTime from,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) LocalDateTime to,
            @RequestParam(required = false) Long ventaId,
            @RequestParam(required = false) String search,
            @RequestParam(required = false) Long sucursalId,
            Authentication authentication) {

        String tenantId = TenantContext.getCurrentTenant();
        boolean verTodos = authentication.getAuthorities().stream()
                .anyMatch(a -> a.getAuthority().equals("PERM_VER_FACTURACION") || a.getAuthority().equals("ROLE_ADMIN"));
        Long vendedorId = verTodos ? null : TenantContext.getCurrentUserId();

        log.info("📋 Listando comprobantes para tenant: {} vendedorId: {} sucursal: {}", tenantId, vendedorId, sucursalId);
        return ResponseEntity.ok(
                comprobanteService.listar(tenantId, sucursalId, vendedorId, tipo, estado, from, to, ventaId, search));
    }

    /**
     * Emit a new comprobante (boleta/factura) for an existing venta.
     */
    @PostMapping
    @PreAuthorize("hasRole('ADMIN') or hasAuthority('PERM_EMITIR_COMPROBANTE')")
    public ResponseEntity<ComprobanteDTO> emitir(@Valid @RequestBody EmitirComprobanteRequest request) {
        String tenantId = TenantContext.getCurrentTenant();
        log.info("🧾 Emitiendo comprobante tipo {} para venta {} (tenant {})",
                request.getTipo(), request.getVentaId(), tenantId);
        ComprobanteDTO dto = comprobanteService.emitir(request, tenantId);
        return ResponseEntity.status(HttpStatus.CREATED).body(dto);
    }

    /**
     * Get a single comprobante by its ID.
     */
    @GetMapping("/{id}")
    @PreAuthorize("hasRole('ADMIN') or hasAuthority('PERM_VER_COMPROBANTE')")
    public ResponseEntity<ComprobanteDTO> obtenerPorId(@PathVariable Long id) {
        String tenantId = TenantContext.getCurrentTenant();
        return comprobanteService.obtenerPorId(id, tenantId)
                .map(ResponseEntity::ok)
                .orElse(ResponseEntity.notFound().build());
    }

    /**
     * Descarga el PDF de un comprobante generado localmente.
     * Devuelve application/pdf con Content-Disposition: attachment.
     */
    @GetMapping("/{id}/pdf")
    @PreAuthorize("hasRole('ADMIN') or hasAuthority('PERM_VER_COMPROBANTE')")
    public ResponseEntity<byte[]> descargarPdf(@PathVariable Long id) {
        String tenantId = TenantContext.getCurrentTenant();
        log.info("📄 Descargando PDF comprobante ID: {} (tenant {})", id, tenantId);

        byte[] pdf = pdfComprobanteService.generate(id, tenantId);

        // Resolve filename from DTO (fallback to generic name)
        String filename = comprobanteService.obtenerPorId(id, tenantId)
                .map(dto -> (dto.getNumero() != null ? dto.getNumero() : "comprobante-" + id) + ".pdf")
                .orElse("comprobante-" + id + ".pdf");

        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.APPLICATION_PDF);
        headers.setContentDisposition(
                ContentDisposition.attachment().filename(filename).build());

        return ResponseEntity.ok().headers(headers).body(pdf);
    }

    /**
     * Envía el comprobante al OSE configurado por el tenant (Nubefact, Efact, etc.)
     * y actualiza su estado SUNAT, pdf_url y xml_url con la respuesta.
     *
     * Precondiciones:
     *  - El comprobante debe estar EMITIDO (no ANULADO)
     *  - El tenant debe tener oseUrl y oseToken configurados en Ajustes → Facturación
     */
    @PostMapping("/{id}/enviar-sunat")
    @PreAuthorize("hasRole('ADMIN') or hasAuthority('PERM_ENVIAR_SUNAT')")
    public ResponseEntity<ComprobanteDTO> enviarASunat(@PathVariable Long id) {
        String tenantId = TenantContext.getCurrentTenant();
        log.info("🏛️ Enviando a SUNAT comprobante ID: {} (tenant {})", id, tenantId);
        return ResponseEntity.ok(comprobanteService.enviarASunat(id, tenantId));
    }

    /**
     * Anular (void) a comprobante.
     */
    @PostMapping("/{id}/anular")
    @PreAuthorize("hasRole('ADMIN') or hasAuthority('PERM_ANULAR_COMPROBANTE')")
    public ResponseEntity<ComprobanteDTO> anular(@PathVariable Long id) {
        String tenantId = TenantContext.getCurrentTenant();
        log.info("🗑️ Anulando comprobante ID: {} (tenant {})", id, tenantId);
        return ResponseEntity.ok(comprobanteService.anular(id, tenantId));
    }
}
