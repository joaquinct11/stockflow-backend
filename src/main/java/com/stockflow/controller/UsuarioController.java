package com.stockflow.controller;

import com.stockflow.dto.DeleteAccountValidationDTO;
import com.stockflow.dto.UsuarioDTO;
import com.stockflow.dto.UsuarioUpdateDTO;
import com.stockflow.entity.Rol;
import com.stockflow.entity.Usuario;
import com.stockflow.mapper.UsuarioMapper;
import com.stockflow.repository.RolRepository;
import com.stockflow.service.UsuarioService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import jakarta.validation.Valid;
import java.util.List;

@Slf4j
@RestController
@RequestMapping("/usuarios")
@RequiredArgsConstructor
public class UsuarioController {

    private final UsuarioService usuarioService;
    private final UsuarioMapper usuarioMapper;
    private final RolRepository rolRepository;

    @GetMapping
    public ResponseEntity<List<UsuarioDTO>> obtenerTodos() {
        return ResponseEntity.ok(
                usuarioMapper.toDTOList(usuarioService.obtenerTodos())
        );
    }

    @GetMapping("/{id}")
    public ResponseEntity<UsuarioDTO> obtenerPorId(@PathVariable Long id) {
        return usuarioService.obtenerUsuarioPorId(id)
                .map(usuarioMapper::toDTO)
                .map(ResponseEntity::ok)
                .orElse(ResponseEntity.notFound().build());
    }

    @GetMapping("/email/{email}")
    public ResponseEntity<UsuarioDTO> obtenerPorEmail(@PathVariable String email) {
        return usuarioService.obtenerUsuarioPorEmail(email)
                .map(usuarioMapper::toDTO)
                .map(ResponseEntity::ok)
                .orElse(ResponseEntity.notFound().build());
    }

    @GetMapping("/tenant/{tenantId}")
    public ResponseEntity<List<UsuarioDTO>> obtenerPorTenant(@PathVariable String tenantId) {
        return ResponseEntity.ok(
                usuarioMapper.toDTOList(usuarioService.obtenerUsuariosPorTenant(tenantId))
        );
    }

    @PostMapping
    public ResponseEntity<UsuarioDTO> crear(@Valid @RequestBody UsuarioDTO usuarioDTO) {
        try {
            log.info("📝 Creando nuevo usuario: {}", usuarioDTO.getEmail());

            // Buscar el rol
            Rol rol = rolRepository.findByNombre(usuarioDTO.getRolNombre())
                    .orElseThrow(() -> new RuntimeException("Rol no encontrado: " + usuarioDTO.getRolNombre()));

            // Convertir DTO a Entity
            Usuario usuario = usuarioMapper.toEntity(usuarioDTO);
            usuario.setRol(rol);
            usuario.setContraseña(usuarioDTO.getContraseña()); // Se encriptará en el service

            // Guardar en BD
            Usuario usuarioCreado = usuarioService.crearUsuario(usuario);

            log.info("✅ Usuario creado exitosamente: {}", usuarioCreado.getEmail());

            return ResponseEntity.status(HttpStatus.CREATED)
                    .body(usuarioMapper.toDTO(usuarioCreado));
        } catch (RuntimeException e) {
            log.error("❌ Error al crear usuario: {}", e.getMessage());
            return ResponseEntity.badRequest().build();
        }
    }

    @PutMapping("/{id}")
    public ResponseEntity<UsuarioDTO> actualizar(
            @PathVariable Long id,
            @Valid @RequestBody UsuarioUpdateDTO updateDTO) {
        try {
            log.info("📝 Actualizando usuario ID: {}", id);
            log.info("📦 Datos: nombre={}, rol={}, activo={}, tenant={}",
                    updateDTO.getNombre(),
                    updateDTO.getRolNombre(),
                    updateDTO.getActivo(),
                    updateDTO.getTenantId());

            return usuarioService.obtenerUsuarioPorId(id)
                    .map(usuario -> {
                        // Actualizar campos básicos
                        usuario.setNombre(updateDTO.getNombre());
                        usuario.setTenantId(updateDTO.getTenantId());
                        usuario.setActivo(updateDTO.getActivo());

                        // Actualizar rol
                        Rol rol = rolRepository.findByNombre(updateDTO.getRolNombre())
                                .orElseThrow(() -> new RuntimeException("Rol no encontrado: " + updateDTO.getRolNombre()));
                        usuario.setRol(rol);

                        Usuario usuarioActualizado = usuarioService.actualizarUsuario(id, usuario);

                        log.info("✅ Usuario actualizado exitosamente");

                        return ResponseEntity.ok(usuarioMapper.toDTO(usuarioActualizado));
                    })
                    .orElseGet(() -> {
                        log.error("❌ Usuario no encontrado: ID {}", id);
                        return ResponseEntity.notFound().build();
                    });
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

    @GetMapping("/{id}/validar-eliminacion")
    public ResponseEntity<DeleteAccountValidationDTO> validarEliminacion(@PathVariable Long id) {
        log.info("🔍 Validando eliminación de usuario ID: {}", id);
        DeleteAccountValidationDTO validacion = usuarioService.validarEliminacion(id);
        return ResponseEntity.ok(validacion);
    }

    @DeleteMapping("/{id}/cuenta-completa")
    public ResponseEntity<Void> eliminarCuentaCompleta(@PathVariable Long id) {
        log.warn("⚠️ ELIMINACIÓN PERMANENTE de cuenta completa ID: {}", id);
        usuarioService.eliminarCuentaCompleta(id);
        return ResponseEntity.noContent().build();
    }
}