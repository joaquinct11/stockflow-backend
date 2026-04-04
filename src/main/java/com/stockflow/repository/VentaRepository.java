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

    // ── Tendencia de ventas (nativeQuery=true para usar DATE_TRUNC de PostgreSQL) ──

    @Query(value = "SELECT TO_CHAR(DATE_TRUNC('day', created_at), 'YYYY-MM-DD') AS periodo, " +
                   "COUNT(id) AS ventas_count, SUM(total) AS ingresos_total " +
                   "FROM ventas " +
                   "WHERE tenant_id = ?1 AND created_at BETWEEN ?2 AND ?3 " +
                   "GROUP BY DATE_TRUNC('day', created_at) " +
                   "ORDER BY DATE_TRUNC('day', created_at)",
           nativeQuery = true)
    List<Object[]> findTendenciaDiaria(String tenantId, LocalDateTime inicio, LocalDateTime fin);

    @Query(value = "SELECT TO_CHAR(DATE_TRUNC('week', created_at), 'YYYY-MM-DD') AS periodo, " +
                   "COUNT(id) AS ventas_count, SUM(total) AS ingresos_total " +
                   "FROM ventas " +
                   "WHERE tenant_id = ?1 AND created_at BETWEEN ?2 AND ?3 " +
                   "GROUP BY DATE_TRUNC('week', created_at) " +
                   "ORDER BY DATE_TRUNC('week', created_at)",
           nativeQuery = true)
    List<Object[]> findTendenciaSemanal(String tenantId, LocalDateTime inicio, LocalDateTime fin);

    @Query(value = "SELECT TO_CHAR(DATE_TRUNC('month', created_at), 'YYYY-MM') AS periodo, " +
                   "COUNT(id) AS ventas_count, SUM(total) AS ingresos_total " +
                   "FROM ventas " +
                   "WHERE tenant_id = ?1 AND created_at BETWEEN ?2 AND ?3 " +
                   "GROUP BY DATE_TRUNC('month', created_at) " +
                   "ORDER BY DATE_TRUNC('month', created_at)",
           nativeQuery = true)
    List<Object[]> findTendenciaMensual(String tenantId, LocalDateTime inicio, LocalDateTime fin);

    // ── Ventas por vendedor ──

    @Query("SELECT v.vendedor.id, v.vendedor.nombre, COUNT(v), SUM(v.total) " +
           "FROM Venta v " +
           "WHERE v.tenantId = :tenantId AND v.createdAt BETWEEN :inicio AND :fin " +
           "GROUP BY v.vendedor.id, v.vendedor.nombre " +
           "ORDER BY SUM(v.total) DESC")
    List<Object[]> findVentasPorVendedor(
            @Param("tenantId") String tenantId,
            @Param("inicio") LocalDateTime inicio,
            @Param("fin") LocalDateTime fin
    );

    // ── Ventas por categoría (nativeQuery para NULLIF/TRIM) ──

    @Query(value = "SELECT COALESCE(NULLIF(TRIM(p.categoria), ''), 'Sin categoría') AS cat, " +
                   "SUM(dv.cantidad) AS unidades, " +
                   "SUM(dv.subtotal) AS ingresos_total, " +
                   "COUNT(DISTINCT dv.venta_id) AS ventas_count " +
                   "FROM detalles_venta dv " +
                   "JOIN ventas v ON v.id = dv.venta_id " +
                   "JOIN productos p ON p.id = dv.producto_id " +
                   "WHERE v.tenant_id = ?1 AND v.created_at BETWEEN ?2 AND ?3 " +
                   "GROUP BY COALESCE(NULLIF(TRIM(p.categoria), ''), 'Sin categoría') " +
                   "ORDER BY SUM(dv.subtotal) DESC",
           nativeQuery = true)
    List<Object[]> findVentasPorCategoria(String tenantId, LocalDateTime inicio, LocalDateTime fin);

    // ── Ventas por método de pago ──

    @Query("SELECT v.metodoPago, COUNT(v), SUM(v.total) " +
           "FROM Venta v " +
           "WHERE v.tenantId = :tenantId AND v.createdAt BETWEEN :inicio AND :fin " +
           "GROUP BY v.metodoPago " +
           "ORDER BY SUM(v.total) DESC")
    List<Object[]> findVentasPorMetodoPago(
            @Param("tenantId") String tenantId,
            @Param("inicio") LocalDateTime inicio,
            @Param("fin") LocalDateTime fin
    );

    // ── Top/bottom productos vendidos por ingresos o unidades ──

    @Query("SELECT dv.producto.id, dv.producto.nombre, SUM(dv.cantidad), SUM(dv.subtotal) " +
           "FROM DetalleVenta dv " +
           "JOIN dv.venta v " +
           "WHERE v.tenantId = :tenantId AND v.createdAt BETWEEN :inicio AND :fin " +
           "GROUP BY dv.producto.id, dv.producto.nombre " +
           "ORDER BY SUM(dv.subtotal) DESC")
    List<Object[]> findTopProductosVendidosPorIngresos(
            @Param("tenantId") String tenantId,
            @Param("inicio") LocalDateTime inicio,
            @Param("fin") LocalDateTime fin
    );

    @Query("SELECT dv.producto.id, dv.producto.nombre, SUM(dv.cantidad), SUM(dv.subtotal) " +
           "FROM DetalleVenta dv " +
           "JOIN dv.venta v " +
           "WHERE v.tenantId = :tenantId AND v.createdAt BETWEEN :inicio AND :fin " +
           "GROUP BY dv.producto.id, dv.producto.nombre " +
           "ORDER BY SUM(dv.cantidad) ASC")
    List<Object[]> findBottomProductosVendidosPorUnidades(
            @Param("tenantId") String tenantId,
            @Param("inicio") LocalDateTime inicio,
            @Param("fin") LocalDateTime fin
    );

    @Query("SELECT dv.producto.id, dv.producto.nombre, SUM(dv.cantidad), SUM(dv.subtotal) " +
           "FROM DetalleVenta dv " +
           "JOIN dv.venta v " +
           "WHERE v.tenantId = :tenantId AND v.createdAt BETWEEN :inicio AND :fin " +
           "GROUP BY dv.producto.id, dv.producto.nombre " +
           "ORDER BY SUM(dv.subtotal) ASC")
    List<Object[]> findBottomProductosVendidosPorIngresos(
            @Param("tenantId") String tenantId,
            @Param("inicio") LocalDateTime inicio,
            @Param("fin") LocalDateTime fin
    );
}