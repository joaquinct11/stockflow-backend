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

    List<Devolucion> findByTenantIdOrderByFechaDevolucionDesc(String tenantId);

    List<Devolucion> findByVentaIdAndTenantId(Long ventaId, String tenantId);

    // ── Dashboard: top N devoluciones recientes (evita full-table-scan) ──
    @Query(value = "SELECT * FROM devoluciones WHERE tenant_id = :tenantId AND (:sucursalId IS NULL OR sucursal_id = :sucursalId) ORDER BY fecha_devolucion DESC LIMIT :limit", nativeQuery = true)
    List<Devolucion> findTopNRecentesByTenantId(@Param("tenantId") String tenantId, @Param("sucursalId") Long sucursalId, @Param("limit") int limit);

    Optional<Devolucion> findByIdAndTenantId(Long id, String tenantId);

    @Modifying
    @Query(value = "UPDATE devoluciones SET sucursal_id = :sucursalId WHERE tenant_id = :tenantId AND sucursal_id IS NULL", nativeQuery = true)
    void asignarSucursalDondeEsNulo(@Param("sucursalId") Long sucursalId, @Param("tenantId") String tenantId);
}
