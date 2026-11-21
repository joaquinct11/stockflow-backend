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

    @Query("SELECT r FROM Recepcion r JOIN FETCH r.proveedor LEFT JOIN FETCH r.usuarioReceptor LEFT JOIN FETCH r.ordenCompra WHERE r.tenantId = :tenantId ORDER BY r.createdAt DESC")
    List<Recepcion> findByTenantId(@Param("tenantId") String tenantId);

    @Query("SELECT r FROM Recepcion r JOIN FETCH r.proveedor LEFT JOIN FETCH r.usuarioReceptor LEFT JOIN FETCH r.ordenCompra WHERE r.tenantId = :tenantId AND r.sucursalId = :sucursalId ORDER BY r.createdAt DESC")
    List<Recepcion> findByTenantIdAndSucursalId(@Param("tenantId") String tenantId, @Param("sucursalId") Long sucursalId);

    @Query("SELECT r FROM Recepcion r JOIN FETCH r.proveedor LEFT JOIN FETCH r.usuarioReceptor LEFT JOIN FETCH r.ordenCompra WHERE r.tenantId = :tenantId AND r.estado = :estado")
    List<Recepcion> findByTenantIdAndEstado(@Param("tenantId") String tenantId, @Param("estado") String estado);

    @Query("SELECT r FROM Recepcion r JOIN FETCH r.proveedor LEFT JOIN FETCH r.usuarioReceptor LEFT JOIN FETCH r.ordenCompra WHERE r.ordenCompra.id = :ocId")
    List<Recepcion> findByOrdenCompraId(@Param("ocId") Long ocId);

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
