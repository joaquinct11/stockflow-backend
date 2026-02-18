package com.stockflow.service.impl;

import com.stockflow.entity.Rol;
import com.stockflow.repository.RolRepository;
import com.stockflow.service.RolService;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import java.util.List;
import java.util.Optional;

@Service
@RequiredArgsConstructor
public class RolServiceImpl implements RolService {

    private final RolRepository rolRepository;

    @Override
    public Rol crearRol(Rol rol) {
        return rolRepository.save(rol);
    }

    @Override
    public Optional<Rol> obtenerRolPorId(Long id) {
        return rolRepository.findById(id);
    }

    @Override
    public Optional<Rol> obtenerRolPorNombre(String nombre) {
        return rolRepository.findByNombre(nombre);
    }

    @Override
    public List<Rol> obtenerTodosRoles() {
        return rolRepository.findAll();
    }

    @Override
    public Rol actualizarRol(Long id, Rol rol) {
        return rolRepository.findById(id)
                .map(rolExistente -> {
                    rolExistente.setNombre(rol.getNombre());
                    rolExistente.setDescripcion(rol.getDescripcion());
                    return rolRepository.save(rolExistente);
                })
                .orElseThrow(() -> new RuntimeException("Rol no encontrado"));
    }

    @Override
    public void eliminarRol(Long id) {
        rolRepository.deleteById(id);
    }
}