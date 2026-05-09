package com.stockflow.controller;

import com.stockflow.dto.AbrirCajaRequestDTO;
import com.stockflow.dto.CajaDTO;
import com.stockflow.dto.CerrarCajaRequestDTO;
import com.stockflow.service.CajaService;
import com.stockflow.service.UsuarioService;
import com.stockflow.util.TenantContext;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@Slf4j
@RestController
@RequestMapping("/cajas")
@RequiredArgsConstructor
public class CajaController {

    private final CajaService cajaService;
    private final UsuarioService usuarioService;

    /** GET /cajas/activa — la caja abierta del tenant (compartida, 404 si no hay ninguna) */
    @GetMapping("/activa")
    @PreAuthorize("hasAnyRole('ADMIN','GERENTE','VENDEDOR') or hasAuthority('PERM_CREAR_VENTA')")
    public ResponseEntity<CajaDTO> getActiva() {
        String tenantId = TenantContext.getCurrentTenant();
        log.info("📦 Buscando caja activa para tenant={}", tenantId);
        return cajaService.getActiva(tenantId)
                .map(ResponseEntity::ok)
                .orElse(ResponseEntity.notFound().build());
    }

    /** GET /cajas — historial de cajas del tenant (ADMIN/GERENTE) */
    @GetMapping
    @PreAuthorize("hasAnyRole('ADMIN','GERENTE','VENDEDOR') or hasAuthority('PERM_VER_REPORTES')")
    public ResponseEntity<List<CajaDTO>> getAll() {
        String tenantId = TenantContext.getCurrentTenant();
        return ResponseEntity.ok(cajaService.getAll(tenantId));
    }

    /** GET /cajas/{id} */
    @GetMapping("/{id}")
    @PreAuthorize("hasAnyRole('ADMIN','GERENTE','VENDEDOR') or hasAuthority('PERM_CREAR_VENTA')")
    public ResponseEntity<CajaDTO> getById(@PathVariable Long id) {
        String tenantId = TenantContext.getCurrentTenant();
        return cajaService.getById(id, tenantId)
                .map(ResponseEntity::ok)
                .orElse(ResponseEntity.notFound().build());
    }

    /** POST /cajas/abrir */
    @PostMapping("/abrir")
    @PreAuthorize("hasAnyRole('ADMIN','GERENTE','VENDEDOR') or hasAuthority('PERM_CREAR_VENTA')")
    public ResponseEntity<CajaDTO> abrir(@RequestBody AbrirCajaRequestDTO request, Authentication auth) {
        String tenantId = TenantContext.getCurrentTenant();
        Long usuarioId = TenantContext.getCurrentUserId();

        // Intentar obtener el nombre real desde el servicio de usuarios
        String nombre = usuarioService.obtenerUsuarioPorId(usuarioId)
                .map(u -> u.getNombre())
                .orElse(auth.getName());

        log.info("🔓 Abriendo caja: usuario={} monto_apertura={} tenant={}", usuarioId, request.getMontoApertura(), tenantId);
        CajaDTO caja = cajaService.abrir(request, usuarioId, nombre, tenantId);
        return ResponseEntity.status(HttpStatus.CREATED).body(caja);
    }

    /** POST /cajas/{id}/cerrar */
    @PostMapping("/{id}/cerrar")
    @PreAuthorize("hasAnyRole('ADMIN','GERENTE','VENDEDOR') or hasAuthority('PERM_CREAR_VENTA')")
    public ResponseEntity<CajaDTO> cerrar(@PathVariable Long id,
                                          @Valid @RequestBody CerrarCajaRequestDTO request) {
        String tenantId = TenantContext.getCurrentTenant();
        log.info("🔒 Cerrando caja ID={} tenant={}", id, tenantId);
        CajaDTO caja = cajaService.cerrar(id, request, tenantId);
        return ResponseEntity.ok(caja);
    }
}
