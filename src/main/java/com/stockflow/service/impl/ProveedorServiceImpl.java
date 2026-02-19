package com.stockflow.service.impl;

import com.stockflow.entity.Proveedor;
import com.stockflow.repository.ProveedorRepository;
import com.stockflow.service.ProveedorService;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.Optional;

@Service
@RequiredArgsConstructor
public class ProveedorServiceImpl implements ProveedorService {

    private final ProveedorRepository proveedorRepository;

    @Override
    public Proveedor crearProveedor(Proveedor proveedor) {
        return proveedorRepository.save(proveedor);
    }

    @Override
    public Optional<Proveedor> obtenerProveedorPorId(Long id) {
        return proveedorRepository.findById(id);
    }

    @Override
    public Optional<Proveedor> obtenerProveedorPorRuc(String ruc) {
        return proveedorRepository.findByRuc(ruc);
    }

    @Override
    public List<Proveedor> buscarProveedoresPorNombre(String nombre) {
        return proveedorRepository.findByNombreContainingIgnoreCase(nombre);
    }

    @Override
    public List<Proveedor> obtenerProveedoresPorTenant(String tenantId) {
        return proveedorRepository.findByTenantId(tenantId);
    }

    @Override
    public List<Proveedor> obtenerProveedoresActivos() {
        return proveedorRepository.findByActivoTrue();
    }

    @Override
    public List<Proveedor> obtenerTodosProveedores() {
        return proveedorRepository.findAll();
    }

    @Override
    public Proveedor actualizarProveedor(Long id, Proveedor proveedorActualizado) {
        return proveedorRepository.findById(id)
                .map(proveedor -> {
                    proveedor.setNombre(proveedorActualizado.getNombre());
                    proveedor.setRuc(proveedorActualizado.getRuc());
                    proveedor.setContacto(proveedorActualizado.getContacto());
                    proveedor.setTelefono(proveedorActualizado.getTelefono());
                    proveedor.setEmail(proveedorActualizado.getEmail());
                    proveedor.setDireccion(proveedorActualizado.getDireccion());
                    proveedor.setActivo(proveedorActualizado.getActivo());
                    return proveedorRepository.save(proveedor);
                })
                .orElseThrow(() -> new RuntimeException("Proveedor no encontrado"));
    }

    @Override
    public Proveedor activarProveedor(Long id) {
        return proveedorRepository.findById(id)
                .map(proveedor -> {
                    proveedor.setActivo(true);
                    return proveedorRepository.save(proveedor);
                })
                .orElseThrow(() -> new RuntimeException("Proveedor no encontrado"));
    }

    @Override
    public Proveedor desactivarProveedor(Long id) {
        return proveedorRepository.findById(id)
                .map(proveedor -> {
                    proveedor.setActivo(false);
                    return proveedorRepository.save(proveedor);
                })
                .orElseThrow(() -> new RuntimeException("Proveedor no encontrado"));
    }

    @Override
    public void eliminarProveedor(Long id) {
        proveedorRepository.deleteById(id);
    }
}