package com.stockflow.service.impl;

import com.stockflow.dto.DatosEliminacionDTO;
import com.stockflow.entity.Tenant;
import com.stockflow.repository.*;
import com.stockflow.service.TenantService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import java.time.LocalDateTime;
import java.util.Optional;
import java.util.UUID;

@Slf4j
@Service
@RequiredArgsConstructor
public class TenantServiceImpl implements TenantService {

    private final TenantRepository tenantRepository;
    private final UsuarioRepository usuarioRepository;
    private final ProductoRepository productoRepository;
    private final VentaRepository ventaRepository;
    private final ProveedorRepository proveedorRepository;
    private final SuscripcionRepository suscripcionRepository;

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
    @Transactional
    public void eliminarPermanentemente(String tenantId) {
        log.warn("⚠️ ELIMINACIÓN PERMANENTE de tenant: {}", tenantId);

        tenantRepository.findByTenantId(tenantId)
                .ifPresent(tenant -> {
                    // ON DELETE CASCADE se encarga de eliminar:
                    // - usuarios
                    // - productos
                    // - proveedores
                    // - ventas
                    // - suscripciones
                    // - movimientos_inventario

                    tenantRepository.delete(tenant);
                    log.warn("🗑️ Tenant eliminado permanentemente: {}", tenantId);
                });
    }

    @Override
    public DatosEliminacionDTO obtenerDatosEliminacion(String tenantId) {
        log.info("📊 Obteniendo datos de eliminación para tenant: {}", tenantId);

        Tenant tenant = tenantRepository.findByTenantId(tenantId)
                .orElseThrow(() -> new RuntimeException("Tenant no encontrado"));

        long usuarios = usuarioRepository.countByTenantId(tenantId);
        long productos = productoRepository.countByTenantId(tenantId);
        long ventas = ventaRepository.countByTenantId(tenantId);
        long proveedores = proveedorRepository.countByTenantId(tenantId);
        long suscripciones = suscripcionRepository.countByTenantId(tenantId);

        return DatosEliminacionDTO.builder()
                .usuarios(usuarios)
                .productos(productos)
                .ventas(ventas)
                .proveedores(proveedores)
                .suscripciones(suscripciones)
                .tenantId(tenantId)
                .nombreFarmacia(tenant.getNombre())
                .build();
    }

    private String generarTenantId(String nombreFarmacia) {
        String nombre = nombreFarmacia
                .toLowerCase()
                .replace(" ", "-")
                .replaceAll("[^a-z0-9-]", "");

        String codigo = UUID.randomUUID().toString().substring(0, 4);

        return "farmacia-" + nombre + "-" + codigo;
    }
}