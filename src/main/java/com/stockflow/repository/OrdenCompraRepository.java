package com.stockflow.repository;

import com.stockflow.entity.OrdenCompra;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.time.LocalDateTime;
import java.util.List;

@Repository
public interface OrdenCompraRepository extends JpaRepository<OrdenCompra, Long> {

    @Query("SELECT oc FROM OrdenCompra oc JOIN FETCH oc.proveedor LEFT JOIN FETCH oc.usuarioCreador WHERE oc.tenantId = :tenantId ORDER BY oc.createdAt DESC")
    List<OrdenCompra> findByTenantId(@Param("tenantId") String tenantId);

    @Query("SELECT oc FROM OrdenCompra oc JOIN FETCH oc.proveedor LEFT JOIN FETCH oc.usuarioCreador WHERE oc.tenantId = :tenantId AND oc.sucursalId = :sucursalId ORDER BY oc.createdAt DESC")
    List<OrdenCompra> findByTenantIdAndSucursalId(@Param("tenantId") String tenantId, @Param("sucursalId") Long sucursalId);

    @Query("SELECT oc FROM OrdenCompra oc JOIN FETCH oc.proveedor LEFT JOIN FETCH oc.usuarioCreador WHERE oc.tenantId = :tenantId AND oc.estado = :estado")
    List<OrdenCompra> findByTenantIdAndEstado(@Param("tenantId") String tenantId, @Param("estado") String estado);

    @Query("SELECT oc FROM OrdenCompra oc JOIN FETCH oc.proveedor LEFT JOIN FETCH oc.usuarioCreador WHERE oc.tenantId = :tenantId AND oc.proveedor.id = :proveedorId")
    List<OrdenCompra> findByTenantIdAndProveedorId(@Param("tenantId") String tenantId, @Param("proveedorId") Long proveedorId);

    @Query("SELECT oc FROM OrdenCompra oc JOIN FETCH oc.proveedor LEFT JOIN FETCH oc.usuarioCreador WHERE oc.tenantId = :tenantId AND oc.estado = :estado AND oc.proveedor.id = :proveedorId")
    List<OrdenCompra> findByTenantIdAndEstadoAndProveedorId(@Param("tenantId") String tenantId, @Param("estado") String estado, @Param("proveedorId") Long proveedorId);

    /** OCs en estado ENVIADA cuya fecha de creación es anterior a :limite — sin recepcionar. */
    @Query("""
            SELECT oc FROM OrdenCompra oc JOIN FETCH oc.proveedor LEFT JOIN FETCH oc.usuarioCreador
            WHERE oc.tenantId = :tenantId
              AND oc.estado = 'ENVIADA'
              AND oc.createdAt < :limite
            """)
    List<OrdenCompra> findEnviadasSinRecepcionar(
            @Param("tenantId") String tenantId,
            @Param("limite")   LocalDateTime limite
    );

    // ── Dashboard: top N órdenes recientes (evita full-table-scan) ──
    @Query(value = "SELECT * FROM orden_compra WHERE tenant_id = :tenantId AND (:sucursalId IS NULL OR sucursal_id = :sucursalId) ORDER BY created_at DESC LIMIT :limit", nativeQuery = true)
    List<OrdenCompra> findTopNRecentesByTenantId(@Param("tenantId") String tenantId, @Param("sucursalId") Long sucursalId, @Param("limit") int limit);

    @Modifying
    @Query(value = "UPDATE orden_compra SET sucursal_id = :sucursalId WHERE tenant_id = :tenantId AND sucursal_id IS NULL", nativeQuery = true)
    void asignarSucursalDondeEsNulo(@Param("sucursalId") Long sucursalId, @Param("tenantId") String tenantId);
}
