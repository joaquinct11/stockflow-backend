package com.stockflow.service;

import com.stockflow.dto.DeleteAccountValidationDTO;
import com.stockflow.entity.Usuario;
import java.util.List;
import java.util.Optional;

public interface UsuarioService {

    Usuario crearUsuario(Usuario usuario);

    Optional<Usuario> obtenerUsuarioPorId(Long id);

    Optional<Usuario> obtenerUsuarioPorEmail(String email);

    List<Usuario> obtenerUsuariosPorTenant(String tenantId);

    List<Usuario> obtenerTodos();

    Usuario actualizarUsuario(Long id, Usuario usuarioActualizado);

    void desactivarUsuario(Long id);

    void activarUsuario(Long id);

    DeleteAccountValidationDTO validarEliminacion(Long id);  // ✅ NUEVO

    void eliminarUsuario(Long id);

    void eliminarCuentaCompleta(Long id);  // ✅ NUEVO (elimina tenant)
}