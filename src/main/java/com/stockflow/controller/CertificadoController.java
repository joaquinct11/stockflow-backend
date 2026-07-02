package com.stockflow.controller;

import com.stockflow.dto.CertificadoDTO;
import com.stockflow.repository.TipoCertificadoRepository;
import com.stockflow.service.CertificadoService;
import com.stockflow.util.TenantContext;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;

@Slf4j
@RestController
@RequestMapping("/certificados")
@RequiredArgsConstructor
public class CertificadoController {

    private final CertificadoService certificadoService;
    private final TipoCertificadoRepository tipoCertificadoRepository;

    @GetMapping("/tipos")
    @PreAuthorize("isAuthenticated()")
    public ResponseEntity<List<String>> listarTipos() {
        List<String> nombres = tipoCertificadoRepository.findByActivoTrueOrderByNombreAsc()
                .stream().map(t -> t.getNombre()).toList();
        return ResponseEntity.ok(nombres);
    }

    @GetMapping
    @PreAuthorize("hasRole('ADMIN') or hasAuthority('PERM_VER_CERTIFICADOS')")
    public ResponseEntity<List<CertificadoDTO>> listar(@RequestParam(required = false) Long sucursalId) {
        String tenantId = TenantContext.getCurrentTenant();
        return ResponseEntity.ok(certificadoService.listar(tenantId, sucursalId));
    }

    @GetMapping("/alertas")
    @PreAuthorize("isAuthenticated()")
    public ResponseEntity<Map<String, Long>> contarAlertas() {
        String tenantId = TenantContext.getCurrentTenant();
        long total = certificadoService.contarAlertas(tenantId);
        return ResponseEntity.ok(Map.of("total", total));
    }

    @PostMapping
    @PreAuthorize("hasRole('ADMIN') or hasAuthority('PERM_CREAR_CERTIFICADO')")
    public ResponseEntity<CertificadoDTO> crear(@Valid @RequestBody CertificadoDTO dto) {
        String tenantId = TenantContext.getCurrentTenant();
        log.info("Creando certificado tipo={} para tenant={}", dto.getTipo(), tenantId);
        return ResponseEntity.status(HttpStatus.CREATED).body(certificadoService.crear(dto, tenantId));
    }

    @PutMapping("/{id}")
    @PreAuthorize("hasRole('ADMIN') or hasAuthority('PERM_EDITAR_CERTIFICADO')")
    public ResponseEntity<CertificadoDTO> actualizar(@PathVariable Long id, @Valid @RequestBody CertificadoDTO dto) {
        String tenantId = TenantContext.getCurrentTenant();
        return ResponseEntity.ok(certificadoService.actualizar(id, dto, tenantId));
    }

    @DeleteMapping("/{id}")
    @PreAuthorize("hasRole('ADMIN') or hasAuthority('PERM_ELIMINAR_CERTIFICADO')")
    public ResponseEntity<Void> eliminar(@PathVariable Long id) {
        String tenantId = TenantContext.getCurrentTenant();
        certificadoService.eliminar(id, tenantId);
        return ResponseEntity.noContent().build();
    }
}
