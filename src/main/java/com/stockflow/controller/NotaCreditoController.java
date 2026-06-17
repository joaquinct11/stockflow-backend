package com.stockflow.controller;

import com.stockflow.dto.NotaCreditoDTO;
import com.stockflow.dto.ValidarNotaCreditoResponseDTO;
import com.stockflow.entity.Tenant;
import com.stockflow.exception.ResourceNotFoundException;
import com.stockflow.repository.TenantRepository;
import com.stockflow.service.NotaCreditoService;
import com.stockflow.util.TenantContext;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@Slf4j
@RestController
@RequestMapping("/notas-credito")
@RequiredArgsConstructor
public class NotaCreditoController {

    private final NotaCreditoService notaCreditoService;
    private final TenantRepository tenantRepository;

    @GetMapping
    @PreAuthorize("hasAnyRole('ADMIN', 'GERENTE') or hasAuthority('PERM_VER_NOTAS_CREDITO')")
    public ResponseEntity<List<NotaCreditoDTO>> getAll() {
        String tenantId = TenantContext.getCurrentTenant();
        log.info("Listando notas de credito para tenant: {}", tenantId);
        return ResponseEntity.ok(notaCreditoService.getAll(tenantId));
    }

    @GetMapping("/validar")
    @PreAuthorize("hasAnyRole('ADMIN', 'GERENTE', 'VENDEDOR') or hasAuthority('PERM_VER_NOTAS_CREDITO')")
    public ResponseEntity<ValidarNotaCreditoResponseDTO> validar(@RequestParam String codigo) {
        String tenantId = TenantContext.getCurrentTenant();
        log.info("Validando nota de credito {} para tenant: {}", codigo, tenantId);
        return ResponseEntity.ok(notaCreditoService.validar(codigo, tenantId));
    }

    @GetMapping("/{id}/pdf")
    @PreAuthorize("hasAnyRole('ADMIN', 'GERENTE') or hasAuthority('PERM_EMITIR_NOTA_CREDITO')")
    public ResponseEntity<byte[]> generarPdf(
            @PathVariable Long id,
            @RequestParam(defaultValue = "A4") String formato) {
        String tenantId = TenantContext.getCurrentTenant();
        log.info("Generando PDF ({}) de nota de credito {} para tenant: {}", formato, id, tenantId);

        Tenant tenant = tenantRepository.findByTenantId(tenantId)
                .orElseThrow(() -> new ResourceNotFoundException("Tenant no encontrado: " + tenantId));

        byte[] pdf = notaCreditoService.generarPdf(id, tenantId, tenant, formato);

        String filename = "TICKET".equalsIgnoreCase(formato)
                ? "nc-ticket-" + id + ".pdf"
                : "nota-credito-" + id + ".pdf";

        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.APPLICATION_PDF);
        headers.setContentDispositionFormData("attachment", filename);

        return ResponseEntity.ok()
                .headers(headers)
                .body(pdf);
    }
}
