package com.stockflow.service;

import com.stockflow.entity.Permiso;
import java.util.List;
import java.util.Optional;

public interface PermisoService {

    Permiso crearPermiso(Permiso permiso);

    Optional<Permiso> obtenerPermisoPorId(Long id);

    List<Permiso> obtenerTodosPermisos();

    List<Permiso> obtenerPermisosPorRol(Long rolId);

    Optional<Permiso> obtenerPermisoPorNombre(String nombre);

    Permiso actualizarPermiso(Long id, Permiso permiso);

    void eliminarPermiso(Long id);
}