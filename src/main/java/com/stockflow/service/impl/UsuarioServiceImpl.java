package com.stockflow.service.impl;

import com.stockflow.dto.DeleteAccountValidationDTO;
import com.stockflow.dto.DatosEliminacionDTO;
import com.stockflow.entity.Suscripcion;
import com.stockflow.entity.Usuario;
import com.stockflow.exception.BadRequestException;
import com.stockflow.exception.ResourceNotFoundException;
import com.stockflow.repository.SuscripcionRepository;
import com.stockflow.repository.UsuarioRepository;
import com.stockflow.service.EmailService;
import com.stockflow.service.TenantService;
import com.stockflow.service.UsuarioService;
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
    private final SuscripcionRepository suscripcionRepository;
    private final PasswordEncoder passwordEncoder;
    private final TenantService tenantService;
    private final EmailService emailService;

    @Override
    public Usuario crearUsuario(Usuario usuario) {
        usuario.setContraseña(passwordEncoder.encode(usuario.getContraseña()));
        return usuarioRepository.save(usuario);
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
        return usuarioRepository.findByTenantId(tenantId);
    }

    @Override
    public Usuario actualizarUsuario(Long id, Usuario usuarioActualizado) {
        return usuarioRepository.findById(id)
                .map(usuario -> {
                    if (usuarioActualizado.getNombre() != null) {
                        usuario.setNombre(usuarioActualizado.getNombre());
                    }
                    if (usuarioActualizado.getRol() != null) {
                        usuario.setRol(usuarioActualizado.getRol());
                    }
                    if (usuarioActualizado.getActivo() != null) {
                        usuario.setActivo(usuarioActualizado.getActivo());
                    }
                    return usuarioRepository.save(usuario);
                })
                .orElseThrow(() -> new RuntimeException("Usuario no encontrado"));
    }

    @Override
    public void desactivarUsuario(Long id) {
        usuarioRepository.findById(id)
                .ifPresent(usuario -> {
                    usuario.setActivo(false);
                    usuarioRepository.save(usuario);
                    log.info("🔒 Usuario desactivado: {}", usuario.getEmail());
                });
    }

    @Override
    public void activarUsuario(Long id) {
        Usuario usuario = obtenerUsuarioPorId(id)
                .orElseThrow(() -> new RuntimeException("Usuario no encontrado"));
        usuario.setActivo(true);
        usuarioRepository.save(usuario);
        log.info("✅ Usuario activado: {}", usuario.getEmail());
    }

    @Override
    public DeleteAccountValidationDTO validarEliminacion(Long id) {
        log.info("🔍 Validando eliminación de usuario ID: {}", id);

        Usuario usuario = usuarioRepository.findById(id)
                .orElseThrow(() -> new RuntimeException("Usuario no encontrado"));

        // Verificar si es el usuario principal (dueño del tenant)
        Optional<Suscripcion> suscripcion = suscripcionRepository.findByUsuarioPrincipalId(usuario.getId());

        if (suscripcion.isPresent()) {
            // Es el OWNER del tenant
            log.warn("⚠️ Usuario ID {} es el OWNER del tenant {}", id, usuario.getTenantId());

            DatosEliminacionDTO datos = tenantService.obtenerDatosEliminacion(usuario.getTenantId());

            return DeleteAccountValidationDTO.builder()
                    .requiereConfirmacion(true)
                    .tipo("TENANT_OWNER")
                    .mensaje("Esta acción eliminará TODA la información de tu farmacia de forma PERMANENTE")
                    .datosAEliminar(datos)
                    .build();
        } else {
            // Es un usuario normal del tenant
            log.info("ℹ️ Usuario ID {} es un usuario normal", id);

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
    public void eliminarUsuario(Long id) {
        usuarioRepository.findById(id).ifPresent(usuario -> {
            log.info("🗑️ Hard delete de usuario: {} ({})", usuario.getEmail(), id);
            usuarioRepository.deleteById(id);
        });
    }

    @Override
    @Transactional
    public void eliminarCuentaCompleta(Long id) {
        log.warn("⚠️ ELIMINACIÓN COMPLETA de cuenta de usuario ID: {}", id);

        Usuario usuario = usuarioRepository.findById(id)
                .orElseThrow(() -> new RuntimeException("Usuario no encontrado"));

        // Verificar que sea el owner
        Optional<Suscripcion> suscripcion = suscripcionRepository.findByUsuarioPrincipalId(usuario.getId());

        if (suscripcion.isEmpty()) {
            throw new RuntimeException("Solo el propietario puede eliminar la cuenta completa");
        }

        String tenantId = usuario.getTenantId();

        // Eliminar el tenant (CASCADE eliminará todo)
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
        if (!usuario.getTenantId().equals(tenantId)) {
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