package com.stockflow.controller;

import com.stockflow.dto.ComisionDTO;
import com.stockflow.entity.Comision;
import com.stockflow.exception.ResourceNotFoundException;
import com.stockflow.repository.ComisionRepository;
import com.stockflow.util.TenantContext;
import jakarta.validation.Valid;
import org.springframework.security.core.context.SecurityContextHolder;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;

@Slf4j
@RestController
@RequestMapping("/comisiones")
@RequiredArgsConstructor
public class ComisionController {

    private final ComisionRepository comisionRepository;

    @GetMapping
    @PreAuthorize("hasRole('ADMIN') or hasAuthority('PERM_VER_COMISIONES')")
    public ResponseEntity<List<ComisionDTO>> listar(@RequestParam(required = false) Long sucursalId) {
        String tenantId = TenantContext.getCurrentTenant();
        List<ComisionDTO> lista = (sucursalId != null
                ? comisionRepository.findByTenantIdAndSucursalIdAndDeletedAtIsNull(tenantId, sucursalId)
                : comisionRepository.findByTenantIdAndDeletedAtIsNullOrderByFechaDesc(tenantId))
                .stream().map(this::toDTO).toList();
        return ResponseEntity.ok(lista);
    }

    @GetMapping("/{id}")
    @PreAuthorize("hasRole('ADMIN') or hasAuthority('PERM_VER_COMISIONES')")
    public ResponseEntity<ComisionDTO> obtenerPorId(@PathVariable Long id) {
        String tenantId = TenantContext.getCurrentTenant();
        return comisionRepository.findByIdAndTenantId(id, tenantId)
                .map(this::toDTO)
                .map(ResponseEntity::ok)
                .orElse(ResponseEntity.notFound().build());
    }

    @PostMapping
    @PreAuthorize("hasRole('ADMIN') or hasAuthority('PERM_CREAR_COMISION')")
    public ResponseEntity<ComisionDTO> crear(@Valid @RequestBody ComisionDTO dto) {
        String tenantId = TenantContext.getCurrentTenant();
        String usuario  = SecurityContextHolder.getContext().getAuthentication().getName();

        Comision comision = Comision.builder()
                .tenantId(tenantId)
                .concepto(dto.getConcepto().trim())
                .pagador(dto.getPagador().trim())
                .monto(dto.getMonto())
                .fecha(dto.getFecha() != null ? dto.getFecha() : LocalDate.now())
                .metodoPago(dto.getMetodoPago())
                .numeroComprobante(dto.getNumeroComprobante())
                .notas(dto.getNotas())
                .registradoPor(usuario)
                .sucursalId(dto.getSucursalId())
                .build();

        Comision guardada = comisionRepository.save(comision);
        log.info("✅ Comisión registrada id={} monto={} pagador={} tenant={}",
                guardada.getId(), guardada.getMonto(), guardada.getPagador(), tenantId);
        return ResponseEntity.status(HttpStatus.CREATED).body(toDTO(guardada));
    }

    @PutMapping("/{id}")
    @PreAuthorize("hasRole('ADMIN') or hasAuthority('PERM_EDITAR_COMISION')")
    public ResponseEntity<ComisionDTO> actualizar(@PathVariable Long id, @Valid @RequestBody ComisionDTO dto) {
        String tenantId = TenantContext.getCurrentTenant();
        Comision comision = comisionRepository.findByIdAndTenantId(id, tenantId)
                .orElseThrow(() -> new ResourceNotFoundException("Comisión no encontrada"));

        comision.setConcepto(dto.getConcepto().trim());
        comision.setPagador(dto.getPagador().trim());
        comision.setMonto(dto.getMonto());
        comision.setFecha(dto.getFecha());
        comision.setMetodoPago(dto.getMetodoPago());
        comision.setNumeroComprobante(dto.getNumeroComprobante());
        comision.setNotas(dto.getNotas());

        return ResponseEntity.ok(toDTO(comisionRepository.save(comision)));
    }

    @DeleteMapping("/{id}")
    @PreAuthorize("hasRole('ADMIN') or hasAuthority('PERM_ELIMINAR_COMISION')")
    public ResponseEntity<Void> eliminar(@PathVariable Long id) {
        String tenantId = TenantContext.getCurrentTenant();
        comisionRepository.findByIdAndTenantId(id, tenantId).ifPresent(c -> {
            c.setDeletedAt(LocalDateTime.now());
            comisionRepository.save(c);
        });
        return ResponseEntity.noContent().build();
    }

    private ComisionDTO toDTO(Comision c) {
        return ComisionDTO.builder()
                .id(c.getId())
                .concepto(c.getConcepto())
                .pagador(c.getPagador())
                .monto(c.getMonto())
                .fecha(c.getFecha())
                .metodoPago(c.getMetodoPago())
                .numeroComprobante(c.getNumeroComprobante())
                .notas(c.getNotas())
                .tenantId(c.getTenantId())
                .registradoPor(c.getRegistradoPor())
                .createdAt(c.getCreatedAt())
                .sucursalId(c.getSucursalId())
                .build();
    }
}
