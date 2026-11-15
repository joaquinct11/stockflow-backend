package com.stockflow.controller;

import com.stockflow.config.RolePermissionDefaults;
import com.stockflow.dto.PermisoDTO;
import com.stockflow.dto.UsuarioDTO;
import com.stockflow.entity.Permiso;
import com.stockflow.mapper.UsuarioMapper;
import com.stockflow.repository.UsuarioTenantRepository;
import com.stockflow.service.PermisoService;
import com.stockflow.service.UsuarioPermisoService;
import com.stockflow.util.TenantContext;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.stream.Collectors;

@Slf4j
@RestController
@RequestMapping("/admin")
@RequiredArgsConstructor
@PreAuthorize("hasRole('ADMIN')")
@Tag(name = "Admin - Permisos", description = "Endpoints de administración de permisos por usuario")
public class AdminController {

    private final PermisoService permisoService;
    private final UsuarioPermisoService usuarioPermisoService;
    private final UsuarioMapper usuarioMapper;
    private final UsuarioTenantRepository usuarioTenantRepository;
    private final RolePermissionDefaults rolePermissionDefaults;

    @GetMapping("/permisos")
    @Operation(summary = "Listar todos los permisos", description = "Devuelve el catálogo completo de permisos disponibles")
    public ResponseEntity<List<PermisoDTO>> listarPermisos() {
        List<PermisoDTO> permisos = permisoService.obtenerTodosPermisos().stream()
                .map(this::toPermisoDTO)
                .collect(Collectors.toList());
        return ResponseEntity.ok(permisos);
    }

    @GetMapping("/usuarios")
    @Operation(summary = "Listar usuarios del tenant", description = "Devuelve usuarios del tenant actual para gestión de permisos")
    public ResponseEntity<List<UsuarioDTO>> listarUsuarios() {
        String tenantId = TenantContext.getCurrentTenant();
        log.info("👥 [Admin] Listando usuarios del tenant: {}", tenantId);
        List<UsuarioDTO> dtos = usuarioTenantRepository.findByTenantIdAndActivoTrue(tenantId)
                .stream()
                .map(ut -> {
                    UsuarioDTO dto = usuarioMapper.toDTO(ut.getUsuario());
                    dto.setRolNombre(ut.getRol().getNombre());
                    dto.setSucursalId(ut.getSucursalId());
                    dto.setTenantId(tenantId);
                    return dto;
                })
                .toList();
        return ResponseEntity.ok(dtos);
    }

    @GetMapping("/usuarios/{id}/permisos")
    @Operation(summary = "Obtener permisos de un usuario", description = "Devuelve los códigos de permisos asignados directamente al usuario")
    public ResponseEntity<List<String>> obtenerPermisosDeUsuario(@PathVariable Long id) {
        String tenantId = TenantContext.getCurrentTenant();
        log.info("🔑 [Admin] Obteniendo permisos del usuario {} en tenant {}", id, tenantId);
        List<String> codigos = usuarioPermisoService.obtenerPermisosCodigos(id, tenantId);
        return ResponseEntity.ok(codigos);
    }

    @PutMapping("/usuarios/{id}/permisos")
    @Operation(summary = "Asignar permisos a un usuario",
            description = "Reemplaza todos los permisos directos del usuario con la lista proporcionada")
    public ResponseEntity<List<String>> asignarPermisosAUsuario(
            @PathVariable Long id,
            @RequestBody List<String> permisoCodigos) {
        String tenantId = TenantContext.getCurrentTenant();
        log.info("🔑 [Admin] Asignando {} permisos al usuario {} en tenant {}", permisoCodigos.size(), id, tenantId);
        usuarioPermisoService.asignarPermisos(id, permisoCodigos, tenantId);
        List<String> asignados = usuarioPermisoService.obtenerPermisosCodigos(id, tenantId);
        return ResponseEntity.ok(asignados);
    }

    /**
     * Devuelve los códigos de permisos base (por defecto) para un rol.
     * El frontend los usa para pre-marcar los checkboxes en la pantalla de gestión de permisos.
     */
    @GetMapping("/permisos-defaults/{rolNombre}")
    @Operation(summary = "Permisos por defecto de un rol")
    public ResponseEntity<List<String>> obtenerPermisosDefaultRol(@PathVariable String rolNombre) {
        List<String> defaults = new java.util.ArrayList<>(
                rolePermissionDefaults.getBasePermissions(rolNombre.toUpperCase())
        );
        java.util.Collections.sort(defaults);
        return ResponseEntity.ok(defaults);
    }

    private PermisoDTO toPermisoDTO(Permiso permiso) {
        return PermisoDTO.builder()
                .id(permiso.getId())
                .nombre(permiso.getNombre())
                .descripcion(permiso.getDescripcion())
                .rolId(permiso.getRol() != null ? permiso.getRol().getId() : null)
                .build();
    }
}
