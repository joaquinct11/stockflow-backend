package com.stockflow.repository;

import com.stockflow.entity.Devolucion;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public interface DevolucionRepository extends JpaRepository<Devolucion, Long> {

    @Query("SELECT DISTINCT d FROM Devolucion d JOIN FETCH d.venta LEFT JOIN FETCH d.usuario LEFT JOIN FETCH d.detalles WHERE d.tenantId = :tenantId ORDER BY d.fechaDevolucion DESC")
    List<Devolucion> findByTenantIdOrderByFechaDevolucionDesc(@Param("tenantId") String tenantId);

    @Query("SELECT DISTINCT d FROM Devolucion d JOIN FETCH d.venta LEFT JOIN FETCH d.usuario LEFT JOIN FETCH d.detalles WHERE d.venta.id = :ventaId AND d.tenantId = :tenantId")
    List<Devolucion> findByVentaIdAndTenantId(@Param("ventaId") Long ventaId, @Param("tenantId") String tenantId);

    // ── Dashboard: top N devoluciones recientes (evita full-table-scan) ──
    @Query(value = "SELECT * FROM devoluciones WHERE tenant_id = :tenantId AND (:sucursalId IS NULL OR sucursal_id = :sucursalId) ORDER BY fecha_devolucion DESC LIMIT :limit", nativeQuery = true)
    List<Devolucion> findTopNRecentesByTenantId(@Param("tenantId") String tenantId, @Param("sucursalId") Long sucursalId, @Param("limit") int limit);

    Optional<Devolucion> findByIdAndTenantId(Long id, String tenantId);

    @Modifying
    @Query(value = "UPDATE devoluciones SET sucursal_id = :sucursalId WHERE tenant_id = :tenantId AND sucursal_id IS NULL", nativeQuery = true)
    void asignarSucursalDondeEsNulo(@Param("sucursalId") Long sucursalId, @Param("tenantId") String tenantId);

    // F-13: suma de unidades ya devueltas por productoId para una venta dada
    @Query(value = """
        SELECT dd.producto_id, SUM(dd.cantidad_devuelta * dd.factor)
        FROM devolucion_detalle dd
        JOIN devoluciones d ON d.id = dd.devolucion_id
        WHERE d.venta_id = :ventaId AND d.tenant_id = :tenantId
        GROUP BY dd.producto_id
        """, nativeQuery = true)
    List<Object[]> sumarDevueltoPorProducto(@Param("ventaId") Long ventaId, @Param("tenantId") String tenantId);
}
