package com.stockflow.controller;

import com.stockflow.dto.UsuarioDTO;
import com.stockflow.dto.UsuarioUpdateDTO;
import com.stockflow.entity.Rol;
import com.stockflow.entity.Usuario;
import com.stockflow.repository.RolRepository;
import com.stockflow.service.UsuarioService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import jakarta.validation.Valid;
import java.util.List;
import java.util.stream.Collectors;

@Slf4j
@RestController
@RequestMapping("/usuarios")
@RequiredArgsConstructor
public class UsuarioController {

    private final UsuarioService usuarioService;
    private final RolRepository rolRepository;

    private UsuarioDTO convertToDTO(Usuario usuario) {
        return UsuarioDTO.builder()
                .id(usuario.getId())
                .email(usuario.getEmail())
                .nombre(usuario.getNombre())
                .rolNombre(usuario.getRol().getNombre())
                .activo(usuario.getActivo())
                .tenantId(usuario.getTenantId())
                .build();
    }

    @GetMapping
    public ResponseEntity<List<UsuarioDTO>> obtenerTodos() {
        List<Usuario> usuarios = usuarioService.obtenerTodos();
        List<UsuarioDTO> usuariosDTO = usuarios.stream()
                .map(this::convertToDTO)
                .collect(Collectors.toList());
        return ResponseEntity.ok(usuariosDTO);
    }

    @GetMapping("/{id}")
    public ResponseEntity<UsuarioDTO> obtenerPorId(@PathVariable Long id) {
        return usuarioService.obtenerUsuarioPorId(id)
                .map(this::convertToDTO)
                .map(ResponseEntity::ok)
                .orElse(ResponseEntity.notFound().build());
    }

    @GetMapping("/email/{email}")
    public ResponseEntity<UsuarioDTO> obtenerPorEmail(@PathVariable String email) {
        return usuarioService.obtenerUsuarioPorEmail(email)
                .map(this::convertToDTO)
                .map(ResponseEntity::ok)
                .orElse(ResponseEntity.notFound().build());
    }

    @GetMapping("/tenant/{tenantId}")
    public ResponseEntity<List<UsuarioDTO>> obtenerPorTenant(@PathVariable String tenantId) {
        List<Usuario> usuarios = usuarioService.obtenerUsuariosPorTenant(tenantId);
        List<UsuarioDTO> usuariosDTO = usuarios.stream()
                .map(this::convertToDTO)
                .collect(Collectors.toList());
        return ResponseEntity.ok(usuariosDTO);
    }

    @PostMapping
    public ResponseEntity<UsuarioDTO> crear(@Valid @RequestBody UsuarioDTO usuarioDTO) {
        try {
            log.info("📝 Creando nuevo usuario: {}", usuarioDTO.getEmail());

            // Buscar el rol
            Rol rol = rolRepository.findByNombre(usuarioDTO.getRolNombre())
                    .orElseThrow(() -> new RuntimeException("Rol no encontrado: " + usuarioDTO.getRolNombre()));

            // Crear entidad Usuario
            Usuario usuario = Usuario.builder()
                    .email(usuarioDTO.getEmail())
                    .contraseña(usuarioDTO.getContraseña()) // Se encriptará en el service
                    .nombre(usuarioDTO.getNombre())
                    .rol(rol)
                    .activo(usuarioDTO.getActivo() != null ? usuarioDTO.getActivo() : true)
                    .tenantId(usuarioDTO.getTenantId())
                    .build();

            // Guardar en BD
            Usuario usuarioCreado = usuarioService.crearUsuario(usuario);

            log.info("✅ Usuario creado exitosamente: {}", usuarioCreado.getEmail());

            return ResponseEntity.status(HttpStatus.CREATED).body(convertToDTO(usuarioCreado));
        } catch (RuntimeException e) {
            log.error("❌ Error al crear usuario: {}", e.getMessage());
            return ResponseEntity.badRequest().build();
        }
    }

    @PutMapping("/{id}")
    public ResponseEntity<UsuarioDTO> actualizar(
            @PathVariable Long id,
            @Valid @RequestBody UsuarioUpdateDTO updateDTO) { // ← Cambiar aquí
        try {
            log.info("📝 Actualizando usuario ID: {}", id);
            log.info("📦 Datos: nombre={}, rol={}, activo={}, tenant={}",
                    updateDTO.getNombre(),
                    updateDTO.getRolNombre(),
                    updateDTO.getActivo(),
                    updateDTO.getTenantId());

            Usuario usuario = usuarioService.obtenerUsuarioPorId(id)
                    .orElseThrow(() -> new RuntimeException("Usuario no encontrado"));

            // Actualizar campos
            usuario.setNombre(updateDTO.getNombre());
            usuario.setTenantId(updateDTO.getTenantId());
            usuario.setActivo(updateDTO.getActivo());

            // Actualizar rol
            Rol rol = rolRepository.findByNombre(updateDTO.getRolNombre())
                    .orElseThrow(() -> new RuntimeException("Rol no encontrado: " + updateDTO.getRolNombre()));
            usuario.setRol(rol);

            Usuario usuarioActualizado = usuarioService.actualizarUsuario(id, usuario);

            log.info("✅ Usuario actualizado exitosamente");

            return ResponseEntity.ok(convertToDTO(usuarioActualizado));
        } catch (RuntimeException e) {
            log.error("❌ Error al actualizar: {}", e.getMessage());
            return ResponseEntity.badRequest().build();
        }
    }

    @PatchMapping("/{id}/desactivar")
    public ResponseEntity<Void> desactivar(@PathVariable Long id) {
        usuarioService.desactivarUsuario(id);
        return ResponseEntity.noContent().build();
    }

    @PatchMapping("/{id}/activar")
    public ResponseEntity<Void> activar(@PathVariable Long id) {
        log.info("✅ Activando usuario ID: {}", id);
        usuarioService.activarUsuario(id);
        return ResponseEntity.noContent().build();
    }

    @DeleteMapping("/{id}")
    public ResponseEntity<Void> eliminar(@PathVariable Long id) {
        usuarioService.eliminarUsuario(id);
        return ResponseEntity.noContent().build();
    }
}