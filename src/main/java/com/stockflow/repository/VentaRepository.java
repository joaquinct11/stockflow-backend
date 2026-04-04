package com.stockflow.repository;

import com.stockflow.entity.Venta;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;
import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;

@Repository
public interface VentaRepository extends JpaRepository<Venta, Long> {
    List<Venta> findByVendedorId(Long vendedorId);
    List<Venta> findByTenantIdAndVendedorId(String tenantId, Long vendedorId);
    List<Venta> findByTenantId(String tenantId);
    List<Venta> findByCreatedAtBetween(LocalDateTime fechaInicio, LocalDateTime fechaFin);
    long countByTenantId(String tenantId);
    @Query("SELECT v FROM Venta v WHERE v.tenantId = :tenantId AND v.createdAt BETWEEN :inicio AND :fin")
    List<Venta> findVentasPorPeriodo(
            @Param("tenantId") String tenantId,
            @Param("inicio") LocalDateTime inicio,
            @Param("fin") LocalDateTime fin
    );

    @Query("SELECT COUNT(v) FROM Venta v WHERE v.tenantId = :tenantId AND v.createdAt BETWEEN :inicio AND :fin")
    long countByTenantIdAndPeriodo(
            @Param("tenantId") String tenantId,
            @Param("inicio") LocalDateTime inicio,
            @Param("fin") LocalDateTime fin
    );

    @Query("SELECT COALESCE(SUM(v.total), 0) FROM Venta v WHERE v.tenantId = :tenantId AND v.createdAt BETWEEN :inicio AND :fin")
    BigDecimal sumTotalByTenantIdAndPeriodo(
            @Param("tenantId") String tenantId,
            @Param("inicio") LocalDateTime inicio,
            @Param("fin") LocalDateTime fin
    );

    @Query("SELECT dv.producto.id, dv.producto.nombre, SUM(dv.cantidad), SUM(dv.subtotal) " +
           "FROM DetalleVenta dv " +
           "JOIN dv.venta v " +
           "WHERE v.tenantId = :tenantId AND v.createdAt BETWEEN :inicio AND :fin " +
           "GROUP BY dv.producto.id, dv.producto.nombre " +
           "ORDER BY SUM(dv.cantidad) DESC")
    List<Object[]> findTopProductosVendidos(
            @Param("tenantId") String tenantId,
            @Param("inicio") LocalDateTime inicio,
            @Param("fin") LocalDateTime fin
    );

    @Query("SELECT COALESCE(SUM(dv.cantidad * dv.producto.costoUnitario), null) " +
           "FROM DetalleVenta dv " +
           "JOIN dv.venta v " +
           "WHERE v.tenantId = :tenantId AND v.createdAt BETWEEN :inicio AND :fin")
    BigDecimal sumCostoVentasByTenantIdAndPeriodo(
            @Param("tenantId") String tenantId,
            @Param("inicio") LocalDateTime inicio,
            @Param("fin") LocalDateTime fin
    );
}