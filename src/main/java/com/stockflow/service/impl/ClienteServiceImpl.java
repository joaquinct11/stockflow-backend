package com.stockflow.service.impl;

import com.stockflow.dto.ClienteStatsDTO;
import com.stockflow.entity.Cliente;
import com.stockflow.exception.ConflictException;
import com.stockflow.exception.ResourceNotFoundException;
import com.stockflow.repository.ClienteRepository;
import com.stockflow.repository.MascotaRepository;
import com.stockflow.service.ClienteService;
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
public class ClienteServiceImpl implements ClienteService {

    private final ClienteRepository clienteRepository;
    private final MascotaRepository mascotaRepository;

    @Override
    public Cliente crearCliente(Cliente cliente) {
        log.info("➕ Creando cliente: {}", cliente.getNombre());
        return clienteRepository.save(cliente);
    }

    @Override
    public Optional<Cliente> obtenerClientePorId(Long id) {
        String tenantId = TenantContext.getCurrentTenant();
        return clienteRepository.findByIdAndTenantId(id, tenantId);
    }

    @Override
    public List<Cliente> buscarClientesPorNombre(String nombre, String tenantId) {
        return clienteRepository.findByNombreContainingIgnoreCaseAndTenantId(nombre, tenantId);
    }

    @Override
    public List<Cliente> buscarClientesPorDocumento(String numeroDocumento, String tenantId) {
        return clienteRepository.findByNumeroDocumentoAndTenantId(numeroDocumento, tenantId);
    }

    @Override
    public Cliente actualizarCliente(Long id, Cliente cliente) {
        log.info("✏️ Actualizando cliente ID: {}", id);
        String tenantId = TenantContext.getCurrentTenant();
        return clienteRepository.findByIdAndTenantId(id, tenantId)
                .map(existing -> {
                    existing.setNombre(cliente.getNombre());
                    existing.setTipoDocumento(cliente.getTipoDocumento());
                    existing.setNumeroDocumento(cliente.getNumeroDocumento());
                    existing.setTelefono(cliente.getTelefono());
                    existing.setEmail(cliente.getEmail());
                    existing.setDireccion(cliente.getDireccion());
                    return clienteRepository.save(existing);
                })
                .orElseThrow(() -> new ResourceNotFoundException("Cliente no encontrado"));
    }

    @Override
    public Cliente activarCliente(Long id) {
        log.info("✅ Activando cliente ID: {}", id);
        String tenantId = TenantContext.getCurrentTenant();
        return clienteRepository.findByIdAndTenantId(id, tenantId)
                .map(cliente -> {
                    cliente.setActivo(true);
                    cliente.setDeletedAt(null);
                    return clienteRepository.save(cliente);
                })
                .orElseThrow(() -> new ResourceNotFoundException("Cliente no encontrado"));
    }

    @Override
    public Cliente desactivarCliente(Long id) {
        log.info("🔒 Desactivando cliente ID: {}", id);
        String tenantId = TenantContext.getCurrentTenant();
        return clienteRepository.findByIdAndTenantId(id, tenantId)
                .map(cliente -> {
                    cliente.setActivo(false);
                    cliente.setDeletedAt(LocalDateTime.now());
                    return clienteRepository.save(cliente);
                })
                .orElseThrow(() -> new ResourceNotFoundException("Cliente no encontrado"));
    }

    @Override
    public void eliminarCliente(Long id) {
        log.warn("🗑️ Eliminando cliente ID: {}", id);
        String tenantId = TenantContext.getCurrentTenant();
        Cliente cliente = clienteRepository.findByIdAndTenantId(id, tenantId)
                .orElseThrow(() -> new ResourceNotFoundException("Cliente no encontrado"));

        long mascotas = mascotaRepository.countByCliente_IdAndTenantId(id, tenantId);
        if (mascotas > 0) {
            throw new ConflictException(
                    "No se puede eliminar el cliente porque tiene " + mascotas +
                    " mascota(s) registrada(s). Desactívelo en su lugar.");
        }

        clienteRepository.delete(cliente);
    }

    @Override
    public List<Cliente> obtenerClientesPorTenant(String tenantId) {
        log.info("🔍 Obteniendo clientes para tenant: {}", tenantId);
        return clienteRepository.findByTenantId(tenantId);
    }

    @Override
    public List<Cliente> obtenerClientesActivosPorTenant(String tenantId) {
        log.info("✅ Obteniendo clientes activos para tenant: {}", tenantId);
        return clienteRepository.findByTenantIdAndActivoTrue(tenantId);
    }

    @Override
    public ClienteStatsDTO getStats() {
        String tenantId = TenantContext.getCurrentTenant();
        long total   = clienteRepository.countByTenantId(tenantId);
        long activos = clienteRepository.countByTenantIdAndActivoTrue(tenantId);
        return new ClienteStatsDTO(total, activos);
    }
}
