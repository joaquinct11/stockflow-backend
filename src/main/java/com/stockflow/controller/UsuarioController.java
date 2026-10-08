package com.stockflow.controller;

import com.stockflow.dto.CrearUsuarioResult;
import com.stockflow.dto.DeleteAccountValidationDTO;
import com.stockflow.dto.UsuarioDTO;
import com.stockflow.dto.UsuarioUpdateDTO;
import com.stockflow.entity.Rol;
import com.stockflow.entity.Usuario;
import com.stockflow.exception.BadRequestException;
import com.stockflow.exception.ResourceNotFoundException;
import com.stockflow.mapper.UsuarioMapper;
import com.stockflow.repository.RolRepository;
import com.stockflow.repository.UsuarioTenantRepository;
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
    private final UsuarioTenantRepository usuarioTenantRepository;

    /**
     * ✅ ACTUALIZADO: Obtiene usuarios del tenant actual
     */
    @GetMapping
    @PreAuthorize("hasRole('ADMIN') or hasAuthority('PERM_VER_USUARIOS')")
    public ResponseEntity<List<UsuarioDTO>> obtenerTodos() {
        String tenantId = TenantContext.getCurrentTenant();
        log.info("👥 Obteniendo usuarios para tenant: {}", tenantId);

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

    @GetMapping("/{id}")
    @PreAuthorize("hasRole('ADMIN') or hasAuthority('PERM_VER_USUARIOS')")
    public ResponseEntity<UsuarioDTO> obtenerPorId(@PathVariable Long id) {
        String tenantId = TenantContext.getCurrentTenant();
        return usuarioTenantRepository.findByUsuarioIdAndTenantIdAndActivoTrue(id, tenantId)
                .map(ut -> {
                    UsuarioDTO dto = usuarioMapper.toDTO(ut.getUsuario());
                    dto.setRolNombre(ut.getRol().getNombre());
                    dto.setSucursalId(ut.getSucursalId());
                    dto.setTenantId(tenantId);
                    return dto;
                })
                .map(ResponseEntity::ok)
                .orElse(ResponseEntity.notFound().build());
    }

    @GetMapping("/email/{email}")
    @PreAuthorize("hasRole('ADMIN') or hasAuthority('PERM_VER_USUARIOS')")
    public ResponseEntity<UsuarioDTO> obtenerPorEmail(@PathVariable String email) {
        String tenantId = TenantContext.getCurrentTenant();
        return usuarioService.obtenerUsuarioPorEmail(email)
                .flatMap(u -> usuarioTenantRepository.findByUsuarioIdAndTenantIdAndActivoTrue(u.getId(), tenantId))
                .map(ut -> {
                    UsuarioDTO dto = usuarioMapper.toDTO(ut.getUsuario());
                    dto.setRolNombre(ut.getRol().getNombre());
                    dto.setSucursalId(ut.getSucursalId());
                    dto.setTenantId(tenantId);
                    return dto;
                })
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

        // Convertir DTO a Entity — tenantId y rol los gestiona usuario_tenant, no Usuario
        Usuario usuario = usuarioMapper.toEntity(usuarioDTO);
        // Generar contraseña temporal aleatoria — el usuario la reemplazará al activar su cuenta
        usuario.setContraseña(UUID.randomUUID().toString());
        usuario.setCreatedAt(LocalDateTime.now());

        if (usuarioDTO.getTipoDocumento() != null && !usuarioDTO.getTipoDocumento().isBlank())
            usuario.setTipoDocumento(usuarioDTO.getTipoDocumento());
        if (usuarioDTO.getNumeroDocumento() != null && !usuarioDTO.getNumeroDocumento().isBlank())
            usuario.setNumeroDocumento(usuarioDTO.getNumeroDocumento());
        if (usuarioDTO.getNumeroCelular() != null && !usuarioDTO.getNumeroCelular().isBlank())
            usuario.setNumeroCelular(usuarioDTO.getNumeroCelular());

        // Tenant siempre desde TenantContext; rol y sucursalId van directo a usuario_tenant
        CrearUsuarioResult resultado = usuarioService.crearUsuario(usuario, usuarioDTO.getSucursalId(), rol);
        Usuario usuarioCreado = resultado.usuario();

        if (resultado.esNuevo()) {
            // CASO A: usuario nuevo — generar token de activación y enviar email
            String activationToken = UUID.randomUUID().toString();
            usuarioCreado.setTokenActivacion(activationToken);
            usuarioCreado.setTokenActivacionExpira(LocalDateTime.now().plusHours(48));
            usuarioService.guardarUsuario(usuarioCreado);
            emailService.enviarBienvenidaUsuarioNuevo(
                    usuarioCreado.getEmail(), usuarioCreado.getNombre(), tenantId, activationToken);
            log.info("✅ Usuario nuevo creado y email de activación enviado: {}", usuarioCreado.getEmail());
        } else {
            // CASO B: usuario existente incorporado a este tenant — email informativo sin token
            emailService.enviarIncorporacionNuevoNegocio(
                    usuarioCreado.getEmail(), usuarioCreado.getNombre(), tenantId);
            log.info("✅ Usuario existente {} incorporado al tenant: {}", usuarioCreado.getEmail(), tenantId);
        }

        UsuarioDTO dtoCreado = usuarioMapper.toDTO(usuarioCreado);
        dtoCreado.setRolNombre(rol.getNombre());
        dtoCreado.setSucursalId(usuarioDTO.getSucursalId());
        dtoCreado.setTenantId(tenantId);
        return ResponseEntity.status(HttpStatus.CREATED).body(dtoCreado);
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

        // Seguridad cross-tenant: solo se puede editar un usuario que pertenece al tenant activo
        usuarioTenantRepository.findByUsuarioIdAndTenantIdAndActivoTrue(id, tenantId)
                .orElseThrow(() -> new ResourceNotFoundException("Usuario no encontrado"));

        // Resolver Rol — sigue necesario para pasarlo explícitamente al servicio
        Rol rol = rolRepository.findByNombre(updateDTO.getRolNombre())
                .orElseThrow(() -> new BadRequestException("Rol no encontrado: " + updateDTO.getRolNombre()));

        // Pasar DTO + Rol directamente — no se muta la entidad Usuario aquí
        Usuario usuarioActualizado = usuarioService.actualizarUsuario(id, updateDTO, rol);
        log.info("✅ Usuario actualizado exitosamente");
        UsuarioDTO dtoActualizado = usuarioMapper.toDTO(usuarioActualizado);
        dtoActualizado.setRolNombre(rol.getNombre());
        dtoActualizado.setSucursalId(updateDTO.getSucursalId());
        dtoActualizado.setTenantId(tenantId);
        return ResponseEntity.ok(dtoActualizado);
    }

    @PatchMapping("/{id}/desactivar")
    @PreAuthorize("hasRole('ADMIN') or hasAuthority('PERM_CAMBIAR_ESTADO_USUARIO') or authentication.details == #id")
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
    @PreAuthorize("hasRole('ADMIN') or authentication.details == #id")
    public ResponseEntity<DeleteAccountValidationDTO> validarEliminacion(@PathVariable Long id) {
        log.info("🔍 Validando eliminación de usuario ID: {}", id);
        DeleteAccountValidationDTO validacion = usuarioService.validarEliminacion(id);
        return ResponseEntity.ok(validacion);
    }

    @DeleteMapping("/{id}")
    @PreAuthorize("hasRole('ADMIN')")
    public ResponseEntity<Void> eliminar(@PathVariable Long id) {
        log.info("🗑️ Eliminando usuario ID: {}", id);
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