package com.stockflow.service.impl;

import com.stockflow.dto.CrearUsuarioResult;
import com.stockflow.dto.DeleteAccountValidationDTO;
import com.stockflow.dto.DatosEliminacionDTO;
import com.stockflow.dto.UsuarioUpdateDTO;
import com.stockflow.entity.Rol;
import com.stockflow.entity.Suscripcion;
import com.stockflow.entity.Usuario;
import com.stockflow.entity.UsuarioTenant;
import com.stockflow.exception.BadRequestException;
import com.stockflow.exception.ConflictException;
import com.stockflow.exception.ForbiddenException;
import com.stockflow.exception.ResourceNotFoundException;
import com.stockflow.repository.SuscripcionRepository;
import com.stockflow.repository.UsuarioRepository;
import com.stockflow.repository.UsuarioTenantRepository;
import com.stockflow.service.EmailService;
import com.stockflow.service.TenantService;
import com.stockflow.service.UsuarioService;
import com.stockflow.util.TenantContext;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Slf4j
@Service
@RequiredArgsConstructor
public class UsuarioServiceImpl implements UsuarioService {

    private final UsuarioRepository usuarioRepository;
    private final UsuarioTenantRepository usuarioTenantRepository;
    private final SuscripcionRepository suscripcionRepository;
    private final PasswordEncoder passwordEncoder;
    private final TenantService tenantService;
    private final EmailService emailService;

    @Override
    @Transactional
    public CrearUsuarioResult crearUsuario(Usuario usuario, Long sucursalId, Rol rol) {
        // tenantId siempre desde TenantContext (fuente de verdad del JWT activo)
        String tenantId = TenantContext.getCurrentTenant();

        Optional<Usuario> existente = usuarioRepository.findByEmail(usuario.getEmail());

        if (existente.isEmpty()) {
            // CASO A: email no existe globalmente → crear usuario nuevo
            usuario.setContraseña(passwordEncoder.encode(usuario.getContraseña()));
            Usuario guardado = usuarioRepository.save(usuario);
            if (tenantId != null && rol != null) {
                crearOReactivarUsuarioTenant(guardado, tenantId, rol, sucursalId);
            }
            log.info("✅ Usuario nuevo creado: {} → tenant={}", guardado.getEmail(), tenantId);
            return new CrearUsuarioResult(guardado, true);
        }

        // CASO B: email ya existe globalmente → incorporar al tenant actual sin tocar password
        Usuario usuarioExistente = existente.get();
        if (tenantId == null || rol == null) {
            throw new BadRequestException("Tenant y rol son requeridos para incorporar un usuario");
        }

        crearOReactivarUsuarioTenant(usuarioExistente, tenantId, rol, sucursalId);
        log.info("✅ Usuario existente {} incorporado al tenant={}", usuarioExistente.getEmail(), tenantId);
        return new CrearUsuarioResult(usuarioExistente, false);
    }

    private void crearOReactivarUsuarioTenant(Usuario usuario, String tenantId, com.stockflow.entity.Rol rol, Long sucursalId) {
        usuarioTenantRepository.findByUsuarioIdAndTenantId(usuario.getId(), tenantId)
                .ifPresentOrElse(ut -> {
                    if (ut.getActivo()) {
                        throw new ConflictException("El usuario ya pertenece a este negocio");
                    }
                    // Relación inactiva: reactivar actualizando rol y sucursal
                    ut.setRol(rol);
                    ut.setSucursalId(sucursalId);
                    ut.setActivo(true);
                    usuarioTenantRepository.save(ut);
                    log.info("♻️ usuario_tenant reactivado: usuario={} tenant={} rol={}",
                            usuario.getEmail(), tenantId, rol.getNombre());
                }, () -> {
                    UsuarioTenant ut = UsuarioTenant.builder()
                            .usuario(usuario)
                            .tenantId(tenantId)
                            .rol(rol)
                            .sucursalId(sucursalId)
                            .activo(true)
                            .build();
                    usuarioTenantRepository.save(ut);
                    log.info("✅ usuario_tenant creado: usuario={} tenant={} rol={}",
                            usuario.getEmail(), tenantId, rol.getNombre());
                });
    }

    @Override
    public Optional<Usuario> obtenerUsuarioPorId(Long id) {
        return usuarioRepository.findById(id);
    }

    @Override
    public Optional<Usuario> obtenerUsuarioPorEmail(String email) {
        return usuarioRepository.findByEmail(email);
    }

    @Override
    public List<Usuario> obtenerUsuariosPorTenant(String tenantId) {
        return usuarioTenantRepository.findByTenantIdAndActivoTrue(tenantId)
                .stream()
                .map(UsuarioTenant::getUsuario)
                .toList();
    }

    @Override
    @Transactional
    public Usuario actualizarUsuario(Long id, UsuarioUpdateDTO updateDTO, Rol rol) {
        String tenantId = TenantContext.getCurrentTenant();

        return usuarioRepository.findById(id)
                .map(usuario -> {
                    // ── Campos globales (identidad) — van a usuarios ────────────────
                    if (updateDTO.getNombre() != null)
                        usuario.setNombre(updateDTO.getNombre());
                    if (updateDTO.getApellido() != null)
                        usuario.setApellido(updateDTO.getApellido());
                    if (updateDTO.getActivo() != null)
                        usuario.setActivo(updateDTO.getActivo());
                    if (updateDTO.getTipoDocumento() != null)
                        usuario.setTipoDocumento(updateDTO.getTipoDocumento());
                    if (updateDTO.getNumeroDocumento() != null)
                        usuario.setNumeroDocumento(updateDTO.getNumeroDocumento());
                    if (updateDTO.getNumeroCelular() != null)
                        usuario.setNumeroCelular(updateDTO.getNumeroCelular());
                    // NOT: setRol()        → fuente de verdad en usuario_tenant
                    // NOT: setSucursalId() → fuente de verdad en usuario_tenant
                    Usuario guardado = usuarioRepository.save(usuario);

                    // ── Campos tenant-scoped — van a usuario_tenant ─────────────────
                    if (tenantId != null) {
                        usuarioTenantRepository
                                .findByUsuarioIdAndTenantIdAndActivoTrue(id, tenantId)
                                .ifPresent(ut -> {
                                    if (rol != null) ut.setRol(rol);
                                    ut.setSucursalId(updateDTO.getSucursalId());
                                    usuarioTenantRepository.save(ut);
                                    log.info("✅ usuario_tenant actualizado: usuario={} tenant={} rol={} sucursal={}",
                                            usuario.getEmail(), tenantId,
                                            ut.getRol().getNombre(), ut.getSucursalId());
                                });
                    }
                    return guardado;
                })
                .orElseThrow(() -> new RuntimeException("Usuario no encontrado"));
    }

    @Override
    @Transactional
    public void desactivarUsuario(Long id) {
        String tenantId = TenantContext.getCurrentTenant();

        if (tenantId == null || tenantId.isBlank()) {
            throw new com.stockflow.exception.ForbiddenException("No hay tenant activo en el contexto de la sesión");
        }
        // Desactivar solo la relación de este tenant (tenant isolation)
        usuarioTenantRepository.findByUsuarioIdAndTenantIdAndActivoTrue(id, tenantId)
                .ifPresent(ut -> {
                    ut.setActivo(false);
                    usuarioTenantRepository.save(ut);
                    log.info("🔒 usuario_tenant desactivado: usuario={} tenant={}", id, tenantId);
                });
    }

    @Override
    @Transactional
    public void activarUsuario(Long id) {
        String tenantId = TenantContext.getCurrentTenant();

        if (tenantId == null || tenantId.isBlank()) {
            throw new com.stockflow.exception.ForbiddenException("No hay tenant activo en el contexto de la sesión");
        }
        // Activar solo la relación de este tenant (tenant isolation)
        usuarioTenantRepository.findByUsuarioIdAndTenantId(id, tenantId)
                .ifPresent(ut -> {
                    ut.setActivo(true);
                    usuarioTenantRepository.save(ut);
                    log.info("✅ usuario_tenant activado: usuario={} tenant={}", id, tenantId);
                });
    }

    @Override
    public DeleteAccountValidationDTO validarEliminacion(Long id) {
        log.info("🔍 Validando eliminación de usuario ID: {}", id);

        String tenantId = TenantContext.getCurrentTenant();
        if (tenantId == null || tenantId.isBlank()) {
            throw new ForbiddenException("No hay tenant activo en el contexto de la sesión");
        }

        Usuario usuario = usuarioRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Usuario no encontrado"));

        // Verificar si el usuario es propietario del tenant activo específico
        boolean esOwner = suscripcionRepository
                .findByTenantIdAndUsuarioPrincipalId(tenantId, usuario.getId())
                .isPresent();

        if (esOwner) {
            log.warn("⚠️ Usuario ID {} es el OWNER del tenant {}", id, tenantId);

            DatosEliminacionDTO datos = tenantService.obtenerDatosEliminacion(tenantId);

            return DeleteAccountValidationDTO.builder()
                    .requiereConfirmacion(true)
                    .tipo("TENANT_OWNER")
                    .mensaje("Esta acción eliminará TODA la información de tu farmacia de forma PERMANENTE")
                    .datosAEliminar(datos)
                    .build();
        } else {
            // Es un usuario normal del tenant
            log.info("ℹ️ Usuario ID {} es un usuario normal del tenant {}", id, tenantId);

            return DeleteAccountValidationDTO.builder()
                    .requiereConfirmacion(false)
                    .tipo("USUARIO_NORMAL")
                    .mensaje("El usuario será desactivado pero puede recuperarse después")
                    .build();
        }
    }

    /**
     * Elimina el registro del usuario de la BD (hard delete).
     * Las FKs en ventas, movimientos, cajas, etc. tienen ON DELETE SET NULL,
     * por lo que todo el historial se conserva con usuario_id = NULL.
     * usuario_permisos y refresh_tokens se borran en cascada automáticamente.
     */
    @Override
    @Transactional
    public void eliminarUsuario(Long id) {
        String tenantId = TenantContext.getCurrentTenant();

        if (tenantId == null || tenantId.isBlank()) {
            throw new ForbiddenException("No hay tenant activo en el contexto de la sesión");
        }

        // Desactivar la relación usuario↔tenant actual (soft-delete de usuario_tenant)
        UsuarioTenant ut = usuarioTenantRepository.findByUsuarioIdAndTenantId(id, tenantId)
                .orElseThrow(() -> new ResourceNotFoundException("Usuario no encontrado en este tenant"));

        ut.setActivo(false);
        usuarioTenantRepository.save(ut);
        log.info("🗑️ usuario_tenant desactivado: usuario={} tenant={}", id, tenantId);

        // Si el usuario ya no tiene ningún tenant activo, eliminar el registro global
        // Las FK de ventas/cajas/movimientos tienen ON DELETE SET NULL → historial se preserva
        // usuario_permisos y refresh_tokens se eliminan en cascada automáticamente
        long tenantsActivos = usuarioTenantRepository.countByUsuarioIdAndActivoTrue(id);
        if (tenantsActivos == 0) {
            log.info("🗑️ Usuario {} sin tenants activos — eliminando registro global", id);
            usuarioRepository.deleteById(id);
        }
    }

    @Override
    @Transactional
    public void eliminarCuentaCompleta(Long id) {
        log.warn("⚠️ ELIMINACIÓN COMPLETA de cuenta de usuario ID: {}", id);

        String tenantId = TenantContext.getCurrentTenant();
        if (tenantId == null || tenantId.isBlank()) {
            throw new ForbiddenException("No hay tenant activo en el contexto de la sesión");
        }

        Usuario usuario = usuarioRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Usuario no encontrado"));

        // Verificar que el usuario sea propietario del tenant activo específico
        boolean esOwner = suscripcionRepository
                .findByTenantIdAndUsuarioPrincipalId(tenantId, usuario.getId())
                .isPresent();

        if (!esOwner) {
            throw new ForbiddenException("Solo el propietario puede eliminar la cuenta completa");
        }

        // Eliminar el tenant activo (CASCADE eliminará usuario_tenant automáticamente)
        tenantService.eliminarPermanentemente(tenantId);

        log.warn("🗑️ Cuenta completa eliminada: Tenant {} y todos sus datos", tenantId);
    }

    @Override
    public Usuario guardarUsuario(Usuario usuario) {
        return usuarioRepository.save(usuario);
    }

    @Override
    @Transactional
    public void reenviarActivacion(Long usuarioId, String tenantId) {
        Usuario usuario = usuarioRepository.findById(usuarioId)
                .orElseThrow(() -> new ResourceNotFoundException("Usuario no encontrado"));

        // Solo re-enviar si el usuario aún no se ha activado (tiene token pendiente o expirado)
        // Si el usuario ya inició sesión con éxito (token limpiado), no tiene sentido
        // pero lo permitimos para que el admin pueda reenviar ante cualquier duda
        if (!usuarioTenantRepository.existsByUsuarioIdAndTenantIdAndActivoTrue(usuario.getId(), tenantId)) {
            throw new BadRequestException("No autorizado para gestionar este usuario");
        }

        String nuevoToken = UUID.randomUUID().toString();
        usuario.setTokenActivacion(nuevoToken);
        usuario.setTokenActivacionExpira(LocalDateTime.now().plusHours(48));
        usuarioRepository.save(usuario);

        emailService.enviarBienvenidaUsuarioNuevo(
                usuario.getEmail(), usuario.getNombre(), tenantId, nuevoToken);

        log.info("📧 Link de activación reenviado a: {}", usuario.getEmail());
    }
}