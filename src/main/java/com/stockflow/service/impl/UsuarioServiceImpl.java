package com.stockflow.service.impl;

import com.stockflow.dto.DeleteAccountValidationDTO;
import com.stockflow.dto.DatosEliminacionDTO;
import com.stockflow.entity.Suscripcion;
import com.stockflow.entity.Usuario;
import com.stockflow.repository.SuscripcionRepository;
import com.stockflow.repository.UsuarioRepository;
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

@Slf4j
@Service
@RequiredArgsConstructor
public class UsuarioServiceImpl implements UsuarioService {

    private final UsuarioRepository usuarioRepository;
    private final SuscripcionRepository suscripcionRepository;
    private final PasswordEncoder passwordEncoder;
    private final TenantService tenantService;

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
    public List<Usuario> obtenerTodos() {
        return usuarioRepository.findAll();
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
                    usuario.setDeletedAt(LocalDateTime.now());
                    usuarioRepository.save(usuario);
                    log.info("🔒 Usuario desactivado: {}", usuario.getEmail());
                });
    }

    @Override
    public void activarUsuario(Long id) {
        Usuario usuario = obtenerUsuarioPorId(id)
                .orElseThrow(() -> new RuntimeException("Usuario no encontrado"));
        usuario.setActivo(true);
        usuario.setDeletedAt(null);
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

    @Override
    public void eliminarUsuario(Long id) {
        usuarioRepository.deleteById(id);
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
}