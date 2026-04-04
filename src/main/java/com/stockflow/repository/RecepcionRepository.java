package com.stockflow.repository;

import com.stockflow.entity.Recepcion;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

@Repository
public interface RecepcionRepository extends JpaRepository<Recepcion, Long> {

    List<Recepcion> findByTenantId(String tenantId);

    List<Recepcion> findByTenantIdAndEstado(String tenantId, String estado);

    List<Recepcion> findByOrdenCompraId(Long ocId);

    @EntityGraph(attributePaths = "detalles")
    Optional<Recepcion> findWithDetallesById(Long id);

    @Query("SELECT COUNT(r) FROM Recepcion r " +
           "WHERE r.tenantId = :tenantId AND r.estado = 'CONFIRMADA' " +
           "AND r.fechaConfirmacion BETWEEN :inicio AND :fin")
    long countConfirmadasByTenantIdAndPeriodo(
            @Param("tenantId") String tenantId,
            @Param("inicio") LocalDateTime inicio,
            @Param("fin") LocalDateTime fin
    );

    @Query("SELECT COALESCE(SUM(rd.cantidadRecibida), 0) " +
           "FROM RecepcionDetalle rd " +
           "JOIN rd.recepcion r " +
           "WHERE r.tenantId = :tenantId AND r.estado = 'CONFIRMADA' " +
           "AND r.fechaConfirmacion BETWEEN :inicio AND :fin")
    long sumUnidadesRecibidasByTenantIdAndPeriodo(
            @Param("tenantId") String tenantId,
            @Param("inicio") LocalDateTime inicio,
            @Param("fin") LocalDateTime fin
    );

    @Query("SELECT COALESCE(SUM(rd.cantidadRecibida * rd.producto.costoUnitario), null) " +
           "FROM RecepcionDetalle rd " +
           "JOIN rd.recepcion r " +
           "WHERE r.tenantId = :tenantId AND r.estado = 'CONFIRMADA' " +
           "AND r.fechaConfirmacion BETWEEN :inicio AND :fin")
    java.math.BigDecimal sumMontoComprasByTenantIdAndPeriodo(
            @Param("tenantId") String tenantId,
            @Param("inicio") LocalDateTime inicio,
            @Param("fin") LocalDateTime fin
    );

    // ── Compras agrupadas por proveedor ──

    @Query("SELECT r.proveedor.id, r.proveedor.nombre, COUNT(DISTINCT r.id), " +
           "COALESCE(SUM(rd.cantidadRecibida), 0), " +
           "SUM(rd.cantidadRecibida * rd.producto.costoUnitario) " +
           "FROM Recepcion r " +
           "LEFT JOIN r.detalles rd " +
           "WHERE r.tenantId = :tenantId AND r.estado = 'CONFIRMADA' " +
           "AND r.fechaConfirmacion BETWEEN :inicio AND :fin " +
           "GROUP BY r.proveedor.id, r.proveedor.nombre " +
           "ORDER BY COALESCE(SUM(rd.cantidadRecibida * rd.producto.costoUnitario), 0) DESC")
    List<Object[]> findComprasPorProveedor(
            @Param("tenantId") String tenantId,
            @Param("inicio") LocalDateTime inicio,
            @Param("fin") LocalDateTime fin
    );
}
