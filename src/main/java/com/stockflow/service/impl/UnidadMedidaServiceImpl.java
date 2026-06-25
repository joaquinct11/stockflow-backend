package com.stockflow.service.impl;

import com.stockflow.dto.UnidadMedidaRequestDTO;
import com.stockflow.dto.UnidadMedidaResponseDTO;
import com.stockflow.entity.UnidadMedida;
import com.stockflow.repository.UnidadMedidaRepository;
import com.stockflow.service.UnidadMedidaService;
import com.stockflow.util.TenantContext;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.util.List;

@Slf4j
@Service
@RequiredArgsConstructor
public class UnidadMedidaServiceImpl implements UnidadMedidaService {

    private final UnidadMedidaRepository repository;

    @Override
    public List<UnidadMedidaResponseDTO> listar() {
        String tenantId = TenantContext.getCurrentTenant();
        return repository.findAllByTenantIdOrGlobal(tenantId)
                .stream()
                .map(u -> new UnidadMedidaResponseDTO(u.getId(), u.getNombre()))
                .toList();
    }

    @Override
    public UnidadMedidaResponseDTO obtenerPorId(Long id) {
        String tenantId = TenantContext.getCurrentTenant();
        UnidadMedida unidad = repository.findAllByTenantIdOrGlobal(tenantId)
                .stream().filter(u -> u.getId().equals(id)).findFirst()
                .orElseThrow(() -> new RuntimeException("Unidad no encontrada"));
        return new UnidadMedidaResponseDTO(unidad.getId(), unidad.getNombre());
    }

    @Override
    public UnidadMedidaResponseDTO crear(UnidadMedidaRequestDTO request) {
        UnidadMedida unidad = new UnidadMedida();
        unidad.setNombre(request.getNombre());
        unidad.setTenantId(TenantContext.getCurrentTenant());
        return map(repository.save(unidad));
    }

    @Override
    public UnidadMedidaResponseDTO actualizar(Long id, UnidadMedidaRequestDTO request) {
        String tenantId = TenantContext.getCurrentTenant();
        UnidadMedida unidad = repository.findByTenantId(tenantId)
                .stream().filter(u -> u.getId().equals(id)).findFirst()
                .orElseThrow(() -> new RuntimeException("Unidad no encontrada o no pertenece a tu cuenta"));
        unidad.setNombre(request.getNombre());
        return map(repository.save(unidad));
    }

    @Override
    public void eliminar(Long id) {
        String tenantId = TenantContext.getCurrentTenant();
        UnidadMedida unidad = repository.findByTenantId(tenantId)
                .stream().filter(u -> u.getId().equals(id)).findFirst()
                .orElseThrow(() -> new RuntimeException("Unidad no encontrada o no pertenece a tu cuenta"));
        repository.deleteById(unidad.getId());
    }

    private UnidadMedidaResponseDTO map(UnidadMedida unidad) {
        return new UnidadMedidaResponseDTO(unidad.getId(), unidad.getNombre());
    }
}
