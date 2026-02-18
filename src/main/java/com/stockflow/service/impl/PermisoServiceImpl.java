package com.stockflow.service.impl;

import com.stockflow.entity.Permiso;
import com.stockflow.exception.ResourceNotFoundException;
import com.stockflow.repository.PermisoRepository;
import com.stockflow.service.PermisoService;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import java.util.List;
import java.util.Optional;

@Service
@RequiredArgsConstructor
public class PermisoServiceImpl implements PermisoService {

    private final PermisoRepository permisoRepository;

    @Override
    public Permiso crearPermiso(Permiso permiso) {
        return permisoRepository.save(permiso);
    }

    @Override
    public Optional<Permiso> obtenerPermisoPorId(Long id) {
        return permisoRepository.findById(id);
    }

    @Override
    public List<Permiso> obtenerTodosPermisos() {
        return permisoRepository.findAll();
    }

    @Override
    public List<Permiso> obtenerPermisosPorRol(Long rolId) {
        return permisoRepository.findByRolId(rolId);
    }

    @Override
    public Optional<Permiso> obtenerPermisoPorNombre(String nombre) {
        return permisoRepository.findByNombre(nombre);
    }

    @Override
    public Permiso actualizarPermiso(Long id, Permiso permiso) {
        return permisoRepository.findById(id)
                .map(permisoExistente -> {
                    permisoExistente.setNombre(permiso.getNombre());
                    permisoExistente.setDescripcion(permiso.getDescripcion());
                    permisoExistente.setRol(permiso.getRol());
                    return permisoRepository.save(permisoExistente);
                })
                .orElseThrow(() -> new ResourceNotFoundException("Permiso no encontrado"));
    }

    @Override
    public void eliminarPermiso(Long id) {
        permisoRepository.deleteById(id);
    }
}