package com.stockflow.service.impl;

import com.stockflow.entity.Proveedor;
import com.stockflow.exception.ResourceNotFoundException;
import com.stockflow.repository.ProveedorRepository;
import com.stockflow.service.ProveedorService;
import com.stockflow.util.TenantContext;
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
        String tenantId = TenantContext.getCurrentTenant();
        return proveedorRepository.findByIdAndTenantId(id, tenantId);
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
        String tenantId = TenantContext.getCurrentTenant();
        return proveedorRepository.findByIdAndTenantId(id, tenantId)
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
        String tenantId = TenantContext.getCurrentTenant();
        return proveedorRepository.findByIdAndTenantId(id, tenantId)
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
        String tenantId = TenantContext.getCurrentTenant();
        return proveedorRepository.findByIdAndTenantId(id, tenantId)
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
        String tenantId = TenantContext.getCurrentTenant();
        Proveedor proveedor = proveedorRepository.findByIdAndTenantId(id, tenantId)
                .orElseThrow(() -> new ResourceNotFoundException("Proveedor no encontrado"));
        proveedorRepository.delete(proveedor);
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