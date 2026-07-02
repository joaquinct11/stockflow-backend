package com.stockflow.service.impl;

import com.stockflow.dto.DatosEliminacionDTO;
import com.stockflow.entity.Tenant;
import com.stockflow.repository.TenantRepository;
import com.stockflow.repository.UsuarioRepository;
import com.stockflow.repository.ProductoRepository;
import com.stockflow.repository.VentaRepository;
import com.stockflow.repository.ProveedorRepository;
import com.stockflow.repository.SuscripcionRepository;
import com.stockflow.service.TenantService;
import jakarta.persistence.EntityManager;
import jakarta.persistence.PersistenceContext;
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

    @PersistenceContext
    private EntityManager em;

    private final TenantRepository tenantRepository;
    private final UsuarioRepository usuarioRepository;
    private final ProductoRepository productoRepository;
    private final VentaRepository ventaRepository;
    private final ProveedorRepository proveedorRepository;
    private final SuscripcionRepository suscripcionRepository;

    @Override
    public Tenant crearTenant(String nombreFarmacia, String rubro) {
        String tenantId = generarTenantId(nombreFarmacia);

        log.info("📱 Creando nuevo tenant: {}", tenantId);

        Tenant tenant = Tenant.builder()
                .tenantId(tenantId)
                .nombre(nombreFarmacia)
                .rubro(rubro != null && !rubro.isBlank() ? rubro : "OTRO")
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

        tenantRepository.findByTenantId(tenantId).ifPresent(tenant -> {

            // ── 1. Facturación ─────────────────────────────────────────────────
            // (webhook_logs no tiene tenant_id — es tabla global de idempotencia MP, se omite)
            nativeDelete("DELETE FROM comprobante_series WHERE tenant_id = :t", tenantId);
            nativeDelete("DELETE FROM comprobantes WHERE tenant_id = :t", tenantId);

            // ── 3. Detalles de venta (sin tenant_id directo → subquery) ────────
            nativeDelete(
                "DELETE FROM detalles_venta WHERE venta_id IN " +
                "(SELECT id FROM ventas WHERE tenant_id = :t)", tenantId);

            // ── 3b. Devoluciones y notas de credito (antes de ventas) ─────────
            nativeDelete(
                "DELETE FROM devolucion_detalle WHERE devolucion_id IN " +
                "(SELECT id FROM devoluciones WHERE tenant_id = :t)", tenantId);
            nativeDelete("DELETE FROM devoluciones WHERE tenant_id = :t", tenantId);

            // ── 4. Ventas y cajas (ventas referencia cajas → ventas primero) ──
            // Primero limpiar FK de ventas hacia notas_credito
            nativeDelete("UPDATE ventas SET nota_credito_id = NULL WHERE tenant_id = :t", tenantId);
            nativeDelete("DELETE FROM notas_credito WHERE tenant_id = :t", tenantId);
            nativeDelete("DELETE FROM ventas WHERE tenant_id = :t", tenantId);
            nativeDelete("DELETE FROM cajas WHERE tenant_id = :t", tenantId);

            // ── 5. Recepciones y OC (con sus detalles primero) ─────────────────
            nativeDelete(
                "DELETE FROM recepcion_detalle WHERE recepcion_id IN " +
                "(SELECT id FROM recepcion WHERE tenant_id = :t)", tenantId);
            nativeDelete("DELETE FROM recepcion WHERE tenant_id = :t", tenantId);

            nativeDelete(
                "DELETE FROM orden_compra_detalle WHERE orden_compra_id IN " +
                "(SELECT id FROM orden_compra WHERE tenant_id = :t)", tenantId);
            nativeDelete("DELETE FROM orden_compra WHERE tenant_id = :t", tenantId);

            // ── 6. Inventario y catálogo ───────────────────────────────────────
            nativeDelete("DELETE FROM movimientos_inventario WHERE tenant_id = :t", tenantId);
            nativeDelete("DELETE FROM productos WHERE tenant_id = :t", tenantId);
            nativeDelete("DELETE FROM proveedores WHERE tenant_id = :t", tenantId);
            // Solo categorías propias del tenant (las globales tienen tenant_id NULL)
            nativeDelete("DELETE FROM categorias WHERE tenant_id = :t", tenantId);

            // ── 7. Usuarios y sesiones ─────────────────────────────────────────
            nativeDelete(
                "DELETE FROM refresh_tokens WHERE usuario_id IN " +
                "(SELECT id FROM usuarios WHERE tenant_id = :t)", tenantId);
            nativeDelete(
                "DELETE FROM usuario_permisos WHERE usuario_id IN " +
                "(SELECT id FROM usuarios WHERE tenant_id = :t)", tenantId);
            nativeDelete("DELETE FROM suscripciones WHERE tenant_id = :t", tenantId);
            nativeDelete("DELETE FROM usuarios WHERE tenant_id = :t", tenantId);

            // ── 8. Tenant (último) ─────────────────────────────────────────────
            tenantRepository.delete(tenant);
            log.warn("🗑️ Tenant eliminado permanentemente: {}", tenantId);
        });
    }

    /** Helper para ejecutar un DELETE nativo con el parámetro :t = tenantId */
    private void nativeDelete(String sql, String tenantId) {
        int rows = em.createNativeQuery(sql)
                .setParameter("t", tenantId)
                .executeUpdate();
        log.debug("🗑️ [{}] → {} filas eliminadas", sql.substring(0, Math.min(sql.length(), 60)), rows);
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

        return nombre + "-" + codigo;
    }
    @Override
    public Optional<Tenant> obtenerTenant(String tenantId) {
        log.debug("Buscando tenant: {}", tenantId);
        return tenantRepository.findByTenantId(tenantId);
    }
}