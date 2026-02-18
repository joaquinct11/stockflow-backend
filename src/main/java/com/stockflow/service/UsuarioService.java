package com.stockflow.service;

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

    void eliminarUsuario(Long id);
}