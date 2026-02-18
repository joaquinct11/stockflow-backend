package com.stockflow.service;

import com.stockflow.entity.Rol;
import java.util.List;
import java.util.Optional;

public interface RolService {

    Rol crearRol(Rol rol);

    Optional<Rol> obtenerRolPorId(Long id);

    Optional<Rol> obtenerRolPorNombre(String nombre);

    List<Rol> obtenerTodosRoles();

    Rol actualizarRol(Long id, Rol rol);

    void eliminarRol(Long id);
}