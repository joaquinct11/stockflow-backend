package com.stockflow.controller;

import com.stockflow.dto.GastoDTO;
import com.stockflow.entity.Gasto;
import com.stockflow.mapper.GastoMapper;
import com.stockflow.service.GastoService;
import com.stockflow.util.TenantContext;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import java.util.Map;

@Slf4j
@RestController
@RequestMapping("/gastos")
@RequiredArgsConstructor
public class GastoController {

    private final GastoService gastoService;
    private final GastoMapper gastoMapper;

    @GetMapping
    @PreAuthorize("hasRole('ADMIN') or hasAuthority('PERM_VER_GASTOS')")
    public ResponseEntity<List<GastoDTO>> obtenerTodos(@RequestParam(required = false) Long sucursalId) {
        String tenantId = TenantContext.getCurrentTenant();
        return ResponseEntity.ok(gastoMapper.toDTOList(gastoService.obtenerTodos(tenantId, sucursalId)));
    }

    @GetMapping("/activos")
    @PreAuthorize("hasRole('ADMIN') or hasAuthority('PERM_VER_GASTOS')")
    public ResponseEntity<List<GastoDTO>> obtenerActivos(@RequestParam(required = false) Long sucursalId) {
        String tenantId = TenantContext.getCurrentTenant();
        return ResponseEntity.ok(gastoMapper.toDTOList(gastoService.obtenerActivos(tenantId, sucursalId)));
    }

    @GetMapping("/{id}")
    @PreAuthorize("hasRole('ADMIN') or hasAuthority('PERM_VER_GASTOS')")
    public ResponseEntity<GastoDTO> obtenerPorId(@PathVariable Long id) {
        String tenantId = TenantContext.getCurrentTenant();
        return gastoService.obtenerPorId(id)
                .filter(g -> tenantId.equals(g.getTenantId()))
                .map(gastoMapper::toDTO)
                .map(ResponseEntity::ok)
                .orElse(ResponseEntity.notFound().build());
    }

    @GetMapping("/categoria/{categoria}")
    @PreAuthorize("hasRole('ADMIN') or hasAuthority('PERM_VER_GASTOS')")
    public ResponseEntity<List<GastoDTO>> obtenerPorCategoria(@PathVariable String categoria) {
        String tenantId = TenantContext.getCurrentTenant();
        return ResponseEntity.ok(gastoMapper.toDTOList(gastoService.obtenerPorCategoria(tenantId, categoria)));
    }

    @GetMapping("/rango")
    @PreAuthorize("hasRole('ADMIN') or hasAuthority('PERM_VER_GASTOS')")
    public ResponseEntity<List<GastoDTO>> obtenerPorRango(
            @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate inicio,
            @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate fin) {
        String tenantId = TenantContext.getCurrentTenant();
        return ResponseEntity.ok(gastoMapper.toDTOList(gastoService.obtenerPorRangoFecha(tenantId, inicio, fin)));
    }

    @GetMapping("/buscar")
    @PreAuthorize("hasRole('ADMIN') or hasAuthority('PERM_VER_GASTOS')")
    public ResponseEntity<List<GastoDTO>> buscar(@RequestParam String q) {
        String tenantId = TenantContext.getCurrentTenant();
        return ResponseEntity.ok(gastoMapper.toDTOList(gastoService.buscar(tenantId, q)));
    }

    /** Total de gastos para el período — útil para el dashboard. */
    @GetMapping("/total")
    @PreAuthorize("hasRole('ADMIN') or hasAuthority('PERM_VER_GASTOS')")
    public ResponseEntity<Map<String, BigDecimal>> totalPorPeriodo(
            @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate inicio,
            @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate fin,
            @RequestParam(required = false) Long sucursalId) {
        String tenantId = TenantContext.getCurrentTenant();
        BigDecimal total = gastoService.totalPorPeriodo(tenantId, sucursalId, inicio, fin);
        return ResponseEntity.ok(Map.of("total", total));
    }

    @PostMapping
    @PreAuthorize("hasAnyRole('ADMIN', 'GERENTE') or hasAuthority('PERM_CREAR_GASTO')")
    public ResponseEntity<GastoDTO> crear(
            @Valid @RequestBody GastoDTO dto,
            Authentication auth) {
        String tenantId = TenantContext.getCurrentTenant();
        dto.setTenantId(tenantId);
        dto.setRegistradoPor(auth != null ? auth.getName() : "sistema");

        Gasto gasto = gastoMapper.toEntity(dto);
        gasto.setSucursalId(dto.getSucursalId());
        Gasto creado = gastoService.crear(gasto);
        log.info("💸 Gasto creado ID: {} para tenant {}", creado.getId(), tenantId);
        return ResponseEntity.status(HttpStatus.CREATED).body(gastoMapper.toDTO(creado));
    }

    @PutMapping("/{id}")
    @PreAuthorize("hasAnyRole('ADMIN', 'GERENTE') or hasAuthority('PERM_EDITAR_GASTO')")
    public ResponseEntity<GastoDTO> actualizar(
            @PathVariable Long id,
            @Valid @RequestBody GastoDTO dto) {
        String tenantId = TenantContext.getCurrentTenant();
        return gastoService.obtenerPorId(id)
                .filter(g -> tenantId.equals(g.getTenantId()))
                .map(existente -> {
                    gastoMapper.updateEntityFromDTO(dto, existente);
                    Gasto actualizado = gastoService.actualizar(id, existente);
                    return ResponseEntity.ok(gastoMapper.toDTO(actualizado));
                })
                .orElse(ResponseEntity.notFound().build());
    }

    @DeleteMapping("/{id}")
    @PreAuthorize("hasRole('ADMIN') or hasAuthority('PERM_ELIMINAR_GASTO')")
    public ResponseEntity<Void> eliminar(@PathVariable Long id) {
        String tenantId = TenantContext.getCurrentTenant();
        gastoService.obtenerPorId(id)
                .filter(g -> tenantId.equals(g.getTenantId()))
                .ifPresent(g -> gastoService.eliminar(id));
        return ResponseEntity.noContent().build();
    }
}
