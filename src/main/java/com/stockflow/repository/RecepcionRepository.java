package com.stockflow.repository;

import com.stockflow.entity.Recepcion;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

@Repository
public interface RecepcionRepository extends JpaRepository<Recepcion, Long> {

    List<Recepcion> findByTenantId(String tenantId);

    @Query(value = "SELECT * FROM recepcion WHERE tenant_id = :tenantId AND sucursal_id = :sucursalId ORDER BY created_at DESC", nativeQuery = true)
    List<Recepcion> findByTenantIdAndSucursalId(@Param("tenantId") String tenantId, @Param("sucursalId") Long sucursalId);

    List<Recepcion> findByTenantIdAndEstado(String tenantId, String estado);

    List<Recepcion> findByOrdenCompraId(Long ocId);

    @EntityGraph(attributePaths = "detalles")
    Optional<Recepcion> findWithDetallesById(Long id);

    @Query("SELECT COUNT(r) FROM Recepcion r " +
           "WHERE r.tenantId = :tenantId AND r.estado = 'CONFIRMADA' " +
           "AND r.fechaConfirmacion BETWEEN :inicio AND :fin " +
           "AND (:sucursalId IS NULL OR r.sucursalId = :sucursalId)")
    long countConfirmadasByTenantIdAndPeriodo(
            @Param("tenantId") String tenantId,
            @Param("inicio") LocalDateTime inicio,
            @Param("fin") LocalDateTime fin,
            @Param("sucursalId") Long sucursalId
    );

    @Query("SELECT COALESCE(SUM(rd.cantidadRecibida), 0) " +
           "FROM RecepcionDetalle rd " +
           "JOIN rd.recepcion r " +
           "WHERE r.tenantId = :tenantId AND r.estado = 'CONFIRMADA' " +
           "AND r.fechaConfirmacion BETWEEN :inicio AND :fin " +
           "AND (:sucursalId IS NULL OR r.sucursalId = :sucursalId)")
    long sumUnidadesRecibidasByTenantIdAndPeriodo(
            @Param("tenantId") String tenantId,
            @Param("inicio") LocalDateTime inicio,
            @Param("fin") LocalDateTime fin,
            @Param("sucursalId") Long sucursalId
    );

    @Query("SELECT COALESCE(SUM(rd.cantidadRecibida * rd.producto.costoUnitario), null) " +
           "FROM RecepcionDetalle rd " +
           "JOIN rd.recepcion r " +
           "WHERE r.tenantId = :tenantId AND r.estado = 'CONFIRMADA' " +
           "AND r.fechaConfirmacion BETWEEN :inicio AND :fin " +
           "AND (:sucursalId IS NULL OR r.sucursalId = :sucursalId)")
    java.math.BigDecimal sumMontoComprasByTenantIdAndPeriodo(
            @Param("tenantId") String tenantId,
            @Param("inicio") LocalDateTime inicio,
            @Param("fin") LocalDateTime fin,
            @Param("sucursalId") Long sucursalId
    );

    // ── Compras agrupadas por proveedor ──

    @Query("SELECT r.proveedor.id, r.proveedor.nombre, COUNT(DISTINCT r.id), " +
           "COALESCE(SUM(rd.cantidadRecibida), 0), " +
           "SUM(rd.cantidadRecibida * rd.producto.costoUnitario) " +
           "FROM Recepcion r " +
           "LEFT JOIN r.detalles rd " +
           "WHERE r.tenantId = :tenantId AND r.estado = 'CONFIRMADA' " +
           "AND r.fechaConfirmacion BETWEEN :inicio AND :fin " +
           "AND (:sucursalId IS NULL OR r.sucursalId = :sucursalId) " +
           "GROUP BY r.proveedor.id, r.proveedor.nombre " +
           "ORDER BY COALESCE(SUM(rd.cantidadRecibida * rd.producto.costoUnitario), 0) DESC")
    List<Object[]> findComprasPorProveedor(
            @Param("tenantId") String tenantId,
            @Param("inicio") LocalDateTime inicio,
            @Param("fin") LocalDateTime fin,
            @Param("sucursalId") Long sucursalId
    );

    @Modifying
    @Query(value = "UPDATE recepcion SET sucursal_id = :sucursalId WHERE tenant_id = :tenantId AND sucursal_id IS NULL", nativeQuery = true)
    void asignarSucursalDondeEsNulo(@Param("sucursalId") Long sucursalId, @Param("tenantId") String tenantId);
}
