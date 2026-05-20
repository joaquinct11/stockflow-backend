package com.stockflow.service.impl;

import com.stockflow.entity.Proveedor;
import com.stockflow.exception.ResourceNotFoundException;
import com.stockflow.repository.ProveedorRepository;
import com.stockflow.service.ProveedorService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

@Slf4j
@Service
@RequiredArgsConstructor
public class ProveedorServiceImpl implements ProveedorService {

    private final ProveedorRepository proveedorRepository;

    @Override
    public Proveedor crearProveedor(Proveedor proveedor) {
        log.info("➕ Creando proveedor: {}", proveedor.getNombre());
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
    public Proveedor actualizarProveedor(Long id, Proveedor proveedor) {
        log.info("✏️ Actualizando proveedor ID: {}", id);

        return proveedorRepository.findById(id)
                .map(proveedorExistente -> {
                    proveedorExistente.setNombre(proveedor.getNombre());
                    proveedorExistente.setRuc(proveedor.getRuc());
                    proveedorExistente.setContacto(proveedor.getContacto());
                    proveedorExistente.setTelefono(proveedor.getTelefono());
                    proveedorExistente.setEmail(proveedor.getEmail());
                    proveedorExistente.setDireccion(proveedor.getDireccion());
                    return proveedorRepository.save(proveedorExistente);
                })
                .orElseThrow(() -> new ResourceNotFoundException("Proveedor no encontrado"));
    }

    @Override
    public Proveedor activarProveedor(Long id) {
        log.info("✅ Activando proveedor ID: {}", id);

        return proveedorRepository.findById(id)
                .map(proveedor -> {
                    proveedor.setActivo(true);
                    proveedor.setDeletedAt(null);
                    return proveedorRepository.save(proveedor);
                })
                .orElseThrow(() -> new ResourceNotFoundException("Proveedor no encontrado"));
    }

    @Override
    public Proveedor desactivarProveedor(Long id) {
        log.info("🔒 Desactivando proveedor ID: {}", id);

        return proveedorRepository.findById(id)
                .map(proveedor -> {
                    proveedor.setActivo(false);
                    proveedor.setDeletedAt(LocalDateTime.now());
                    return proveedorRepository.save(proveedor);
                })
                .orElseThrow(() -> new ResourceNotFoundException("Proveedor no encontrado"));
    }

    @Override
    public void eliminarProveedor(Long id) {
        log.warn("🗑️ Eliminando proveedor ID: {}", id);
        proveedorRepository.deleteById(id);
    }

    @Override
    public List<Proveedor> obtenerProveedoresPorTenant(String tenantId) {
        log.info("🔍 Obteniendo proveedores para tenant: {}", tenantId);
        return proveedorRepository.findByTenantId(tenantId);
    }

    @Override
    public List<Proveedor> obtenerProveedoresActivosPorTenant(String tenantId) {
        log.info("✅ Obteniendo proveedores activos para tenant: {}", tenantId);
        return proveedorRepository.findByTenantIdAndActivoTrue(tenantId);
    }
}