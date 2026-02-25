package com.stockflow.service.impl;

import com.stockflow.entity.Tenant;
import com.stockflow.repository.TenantRepository;
import com.stockflow.service.TenantService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import java.time.LocalDateTime;
import java.util.Optional;
import java.util.UUID;

@Slf4j
@Service
@RequiredArgsConstructor
public class TenantServiceImpl implements TenantService {

    private final TenantRepository tenantRepository;

    @Override
    public Tenant crearTenant(String nombreFarmacia) {
        String tenantId = generarTenantId(nombreFarmacia);

        log.info("📱 Creando nuevo tenant: {}", tenantId);

        Tenant tenant = Tenant.builder()
                .tenantId(tenantId)
                .nombre(nombreFarmacia)
                .activo(true)
                .createdAt(LocalDateTime.now())
                .updatedAt(LocalDateTime.now())
                .build();

        Tenant tenantCreado = tenantRepository.save(tenant);
        log.info("✅ Tenant creado exitosamente: {}", tenantCreado.getTenantId());

        return tenantCreado;
    }

    @Override
    public Optional<Tenant> obtenerPorTenantId(String tenantId) {
        return tenantRepository.findByTenantId(tenantId);
    }

    @Override
    public boolean existeTenant(String tenantId) {
        return tenantRepository.existsByTenantId(tenantId);
    }

    @Override
    public void desactivarTenant(String tenantId) {
        tenantRepository.findByTenantId(tenantId)
                .ifPresent(tenant -> {
                    tenant.setActivo(false);
                    tenant.setUpdatedAt(LocalDateTime.now());
                    tenantRepository.save(tenant);
                    log.info("🔒 Tenant desactivado: {}", tenantId);
                });
    }

    @Override
    public void reactivarTenant(String tenantId) {
        tenantRepository.findByTenantId(tenantId)
                .ifPresent(tenant -> {
                    tenant.setActivo(true);
                    tenant.setDeletedAt(null);
                    tenant.setUpdatedAt(LocalDateTime.now());
                    tenantRepository.save(tenant);
                    log.info("✅ Tenant reactivado: {}", tenantId);
                });
    }

    @Override
    public void eliminarPermanentemente(String tenantId) {
        tenantRepository.findByTenantId(tenantId)
                .ifPresent(tenant -> {
                    log.warn("🗑️ Eliminando permanentemente tenant: {}", tenantId);
                    tenantRepository.delete(tenant);
                });
    }

    /**
     * Generar tenantId único
     * Formato: "farmacia-nombrefarmacia-xxxx"
     */
    private String generarTenantId(String nombreFarmacia) {
        String nombre = nombreFarmacia
                .toLowerCase()
                .replace(" ", "-")
                .replaceAll("[^a-z0-9-]", "");

        String codigo = UUID.randomUUID().toString().substring(0, 4);

        return "farmacia-" + nombre + "-" + codigo;
    }
}