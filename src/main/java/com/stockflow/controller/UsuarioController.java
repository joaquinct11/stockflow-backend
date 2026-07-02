package com.stockflow.controller;

import com.stockflow.dto.DeleteAccountValidationDTO;
import com.stockflow.dto.UsuarioDTO;
import com.stockflow.dto.UsuarioUpdateDTO;
import com.stockflow.entity.Rol;
import com.stockflow.entity.Usuario;
import com.stockflow.exception.BadRequestException;
import com.stockflow.mapper.UsuarioMapper;
import com.stockflow.repository.RolRepository;
import com.stockflow.service.EmailService;
import com.stockflow.service.PlanLimitService;
import com.stockflow.service.UsuarioService;
import com.stockflow.util.TenantContext;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;
import jakarta.validation.Valid;

import java.time.LocalDateTime;
import java.util.List;
import java.util.UUID;

@Slf4j
@RestController
@RequestMapping("/usuarios")
@RequiredArgsConstructor
public class UsuarioController {

    private final UsuarioService usuarioService;
    private final UsuarioMapper usuarioMapper;
    private final RolRepository rolRepository;
    private final PlanLimitService planLimitService;
    private final EmailService emailService;

    /**
     * ✅ ACTUALIZADO: Obtiene usuarios del tenant actual
     */
    @GetMapping
    @PreAuthorize("hasRole('ADMIN') or hasAuthority('PERM_VER_USUARIOS')")
    public ResponseEntity<List<UsuarioDTO>> obtenerTodos() {
        String tenantId = TenantContext.getCurrentTenant();
        log.info("👥 Obteniendo usuarios para tenant: {}", tenantId);

        return ResponseEntity.ok(
                usuarioMapper.toDTOList(usuarioService.obtenerUsuariosPorTenant(tenantId))
        );
    }

    @GetMapping("/{id}")
    @PreAuthorize("hasRole('ADMIN') or hasAuthority('PERM_VER_USUARIOS')")
    public ResponseEntity<UsuarioDTO> obtenerPorId(@PathVariable Long id) {
        String tenantId = TenantContext.getCurrentTenant();
        return usuarioService.obtenerUsuarioPorId(id)
                .filter(u -> tenantId.equals(u.getTenantId()))
                .map(usuarioMapper::toDTO)
                .map(ResponseEntity::ok)
                .orElse(ResponseEntity.notFound().build());
    }

    @GetMapping("/email/{email}")
    @PreAuthorize("hasRole('ADMIN') or hasAuthority('PERM_VER_USUARIOS')")
    public ResponseEntity<UsuarioDTO> obtenerPorEmail(@PathVariable String email) {
        String tenantId = TenantContext.getCurrentTenant();
        return usuarioService.obtenerUsuarioPorEmail(email)
                .filter(u -> tenantId.equals(u.getTenantId()))
                .map(usuarioMapper::toDTO)
                .map(ResponseEntity::ok)
                .orElse(ResponseEntity.notFound().build());
    }

    /**
     * ✅ ACTUALIZADO: Setea tenantId automáticamente
     */
    @PostMapping
    @PreAuthorize("hasRole('ADMIN') or hasAuthority('PERM_CREAR_USUARIO')")
    public ResponseEntity<UsuarioDTO> crear(@Valid @RequestBody UsuarioDTO usuarioDTO) {
        String tenantId = TenantContext.getCurrentTenant();
        log.info("📝 Creando nuevo usuario: {} para tenant: {}", usuarioDTO.getEmail(), tenantId);

        // ── Validaciones de plan ────────────────────────────────────────────
        planLimitService.validarLimiteUsuarios(tenantId);
        planLimitService.validarRolPermitido(tenantId, usuarioDTO.getRolNombre());

        // Buscar el rol
        Rol rol = rolRepository.findByNombre(usuarioDTO.getRolNombre())
                .orElseThrow(() -> new BadRequestException("Rol no encontrado: " + usuarioDTO.getRolNombre()));

        // Convertir DTO a Entity
        Usuario usuario = usuarioMapper.toEntity(usuarioDTO);
        usuario.setRol(rol);
        // Generar contraseña temporal aleatoria — el usuario la reemplazará al activar su cuenta
        usuario.setContraseña(UUID.randomUUID().toString());
        usuario.setTenantId(tenantId);
        usuario.setCreatedAt(LocalDateTime.now());

        if (usuarioDTO.getTipoDocumento() != null && !usuarioDTO.getTipoDocumento().isBlank())
            usuario.setTipoDocumento(usuarioDTO.getTipoDocumento());
        if (usuarioDTO.getNumeroDocumento() != null && !usuarioDTO.getNumeroDocumento().isBlank())
            usuario.setNumeroDocumento(usuarioDTO.getNumeroDocumento());
        if (usuarioDTO.getNumeroCelular() != null && !usuarioDTO.getNumeroCelular().isBlank())
            usuario.setNumeroCelular(usuarioDTO.getNumeroCelular());

        Usuario usuarioCreado = usuarioService.crearUsuario(usuario);

        // Generar token de activación (48h) y enviar email de bienvenida
        String activationToken = UUID.randomUUID().toString();
        usuarioCreado.setTokenActivacion(activationToken);
        usuarioCreado.setTokenActivacionExpira(LocalDateTime.now().plusHours(48));
        usuarioService.guardarUsuario(usuarioCreado);

        emailService.enviarBienvenidaUsuarioNuevo(
                usuarioCreado.getEmail(), usuarioCreado.getNombre(), tenantId, activationToken);

        log.info("✅ Usuario creado y email de activación enviado: {}", usuarioCreado.getEmail());

        return ResponseEntity.status(HttpStatus.CREATED).body(usuarioMapper.toDTO(usuarioCreado));
    }

    @PutMapping("/{id}")
    @PreAuthorize("hasRole('ADMIN') or hasAuthority('PERM_EDITAR_USUARIO')")
    public ResponseEntity<UsuarioDTO> actualizar(
            @PathVariable Long id,
            @Valid @RequestBody UsuarioUpdateDTO updateDTO) {
        String tenantId = TenantContext.getCurrentTenant();
        log.info("📝 Actualizando usuario ID: {}", id);

        // ── Validar rol permitido por plan ──────────────────────────────────
        planLimitService.validarRolPermitido(tenantId, updateDTO.getRolNombre());

        return usuarioService.obtenerUsuarioPorId(id)
                .map(usuario -> {
                    usuario.setNombre(updateDTO.getNombre());
                    if (updateDTO.getApellido() != null) usuario.setApellido(updateDTO.getApellido());
                    usuario.setActivo(updateDTO.getActivo());

                    if (updateDTO.getTipoDocumento() != null)
                        usuario.setTipoDocumento(updateDTO.getTipoDocumento());
                    if (updateDTO.getNumeroDocumento() != null)
                        usuario.setNumeroDocumento(updateDTO.getNumeroDocumento());
                    if (updateDTO.getNumeroCelular() != null)
                        usuario.setNumeroCelular(updateDTO.getNumeroCelular());

                    // ADMIN (sin sucursal) puede asignar/cambiar sucursal al vendedor
                    usuario.setSucursalId(updateDTO.getSucursalId());

                    Rol rol = rolRepository.findByNombre(updateDTO.getRolNombre())
                            .orElseThrow(() -> new BadRequestException("Rol no encontrado: " + updateDTO.getRolNombre()));
                    usuario.setRol(rol);

                    Usuario usuarioActualizado = usuarioService.actualizarUsuario(id, usuario);
                    log.info("✅ Usuario actualizado exitosamente");
                    return ResponseEntity.ok(usuarioMapper.toDTO(usuarioActualizado));
                })
                .orElseGet(() -> {
                    log.error("❌ Usuario no encontrado: ID {}", id);
                    return ResponseEntity.notFound().build();
                });
    }

    @PatchMapping("/{id}/desactivar")
    @PreAuthorize("hasRole('ADMIN') or hasAuthority('PERM_CAMBIAR_ESTADO_USUARIO')")
    public ResponseEntity<Void> desactivar(@PathVariable Long id) {
        log.info("🔒 Desactivando usuario ID: {}", id);
        usuarioService.desactivarUsuario(id);
        return ResponseEntity.noContent().build();
    }

    @PatchMapping("/{id}/activar")
    @PreAuthorize("hasRole('ADMIN') or hasAuthority('PERM_CAMBIAR_ESTADO_USUARIO')")
    public ResponseEntity<Void> activar(@PathVariable Long id) {
        log.info("✅ Activando usuario ID: {}", id);
        usuarioService.activarUsuario(id);
        return ResponseEntity.noContent().build();
    }

    /**
     * Reenvía el email de activación al usuario (útil cuando el link original de 48h expiró).
     */
    @PostMapping("/{id}/reenviar-activacion")
    @PreAuthorize("hasRole('ADMIN')")
    public ResponseEntity<Void> reenviarActivacion(@PathVariable Long id) {
        String tenantId = TenantContext.getCurrentTenant();
        log.info("📧 Reenviando link de activación para usuario ID: {} (tenant: {})", id, tenantId);
        usuarioService.reenviarActivacion(id, tenantId);
        return ResponseEntity.noContent().build();
    }

    @GetMapping("/{id}/validar-eliminacion")
    @PreAuthorize("hasRole('ADMIN')")
    public ResponseEntity<DeleteAccountValidationDTO> validarEliminacion(@PathVariable Long id) {
        log.info("🔍 Validando eliminación de usuario ID: {}", id);
        DeleteAccountValidationDTO validacion = usuarioService.validarEliminacion(id);
        return ResponseEntity.ok(validacion);
    }

    @DeleteMapping("/{id}")
    @PreAuthorize("hasRole('ADMIN')")
    public ResponseEntity<Void> eliminar(@PathVariable Long id) {
        log.info("🗑️ Soft delete de usuario ID: {}", id);
        usuarioService.eliminarUsuario(id);
        return ResponseEntity.noContent().build();
    }

    @DeleteMapping("/{id}/cuenta-completa")
    @PreAuthorize("hasRole('ADMIN')")
    public ResponseEntity<Void> eliminarCuentaCompleta(@PathVariable Long id) {
        log.warn("⚠️ ELIMINACIÓN PERMANENTE de cuenta completa ID: {}", id);
        usuarioService.eliminarCuentaCompleta(id);
        return ResponseEntity.noContent().build();
    }
}