package com.stockflow.repository;

import com.stockflow.entity.Venta;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
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
    @Query(value = "SELECT * FROM ventas WHERE tenant_id = :tenantId AND sucursal_id = :sucursalId", nativeQuery = true)
    List<Venta> findByTenantIdAndSucursalId(@Param("tenantId") String tenantId, @Param("sucursalId") Long sucursalId);
    List<Venta> findByCreatedAtBetween(LocalDateTime fechaInicio, LocalDateTime fechaFin);
    long countByTenantId(String tenantId);
    @Query("SELECT DISTINCT v FROM Venta v LEFT JOIN FETCH v.detalles WHERE v.tenantId = :tenantId AND v.createdAt BETWEEN :inicio AND :fin")
    List<Venta> findVentasPorPeriodo(
            @Param("tenantId") String tenantId,
            @Param("inicio") LocalDateTime inicio,
            @Param("fin") LocalDateTime fin
    );

    @Query("SELECT COUNT(v) FROM Venta v WHERE v.tenantId = :tenantId AND v.createdAt BETWEEN :inicio AND :fin " +
           "AND (:sucursalId IS NULL OR v.sucursalId = :sucursalId)")
    long countByTenantIdAndPeriodo(
            @Param("tenantId") String tenantId,
            @Param("inicio") LocalDateTime inicio,
            @Param("fin") LocalDateTime fin,
            @Param("sucursalId") Long sucursalId
    );

    @Query("SELECT COALESCE(SUM(v.total), 0) FROM Venta v WHERE v.tenantId = :tenantId AND v.createdAt BETWEEN :inicio AND :fin " +
           "AND (:sucursalId IS NULL OR v.sucursalId = :sucursalId)")
    BigDecimal sumTotalByTenantIdAndPeriodo(
            @Param("tenantId") String tenantId,
            @Param("inicio") LocalDateTime inicio,
            @Param("fin") LocalDateTime fin,
            @Param("sucursalId") Long sucursalId
    );

    @Query("SELECT dv.producto.id, dv.producto.nombre, SUM(dv.cantidad), SUM(dv.subtotal) " +
           "FROM DetalleVenta dv " +
           "JOIN dv.venta v " +
           "WHERE v.tenantId = :tenantId AND v.createdAt BETWEEN :inicio AND :fin " +
           "AND (:sucursalId IS NULL OR v.sucursalId = :sucursalId) " +
           "GROUP BY dv.producto.id, dv.producto.nombre " +
           "ORDER BY SUM(dv.cantidad) DESC")
    List<Object[]> findTopProductosVendidos(
            @Param("tenantId") String tenantId,
            @Param("inicio") LocalDateTime inicio,
            @Param("fin") LocalDateTime fin,
            @Param("sucursalId") Long sucursalId
    );

    @Query("SELECT COALESCE(SUM(dv.cantidad * dv.producto.costoUnitario), null) " +
           "FROM DetalleVenta dv " +
           "JOIN dv.venta v " +
           "WHERE v.tenantId = :tenantId AND v.createdAt BETWEEN :inicio AND :fin " +
           "AND (:sucursalId IS NULL OR v.sucursalId = :sucursalId)")
    BigDecimal sumCostoVentasByTenantIdAndPeriodo(
            @Param("tenantId") String tenantId,
            @Param("inicio") LocalDateTime inicio,
            @Param("fin") LocalDateTime fin,
            @Param("sucursalId") Long sucursalId
    );

    // ── Tendencia de ventas (nativeQuery=true para usar DATE_TRUNC de PostgreSQL) ──

    @Query(value = "SELECT TO_CHAR(DATE_TRUNC('day', created_at), 'YYYY-MM-DD') AS periodo, " +
                   "COUNT(id) AS ventas_count, SUM(total) AS ingresos_total " +
                   "FROM ventas " +
                   "WHERE tenant_id = :tenantId AND created_at BETWEEN :inicio AND :fin " +
                   "AND (:sucursalId IS NULL OR sucursal_id = :sucursalId) " +
                   "GROUP BY DATE_TRUNC('day', created_at) " +
                   "ORDER BY DATE_TRUNC('day', created_at)",
           nativeQuery = true)
    List<Object[]> findTendenciaDiaria(
            @Param("tenantId") String tenantId,
            @Param("inicio") LocalDateTime inicio,
            @Param("fin") LocalDateTime fin,
            @Param("sucursalId") Long sucursalId
    );

    @Query(value = "SELECT TO_CHAR(DATE_TRUNC('week', created_at), 'YYYY-MM-DD') AS periodo, " +
                   "COUNT(id) AS ventas_count, SUM(total) AS ingresos_total " +
                   "FROM ventas " +
                   "WHERE tenant_id = :tenantId AND created_at BETWEEN :inicio AND :fin " +
                   "AND (:sucursalId IS NULL OR sucursal_id = :sucursalId) " +
                   "GROUP BY DATE_TRUNC('week', created_at) " +
                   "ORDER BY DATE_TRUNC('week', created_at)",
           nativeQuery = true)
    List<Object[]> findTendenciaSemanal(
            @Param("tenantId") String tenantId,
            @Param("inicio") LocalDateTime inicio,
            @Param("fin") LocalDateTime fin,
            @Param("sucursalId") Long sucursalId
    );

    @Query(value = "SELECT TO_CHAR(DATE_TRUNC('month', created_at), 'YYYY-MM') AS periodo, " +
                   "COUNT(id) AS ventas_count, SUM(total) AS ingresos_total " +
                   "FROM ventas " +
                   "WHERE tenant_id = :tenantId AND created_at BETWEEN :inicio AND :fin " +
                   "AND (:sucursalId IS NULL OR sucursal_id = :sucursalId) " +
                   "GROUP BY DATE_TRUNC('month', created_at) " +
                   "ORDER BY DATE_TRUNC('month', created_at)",
           nativeQuery = true)
    List<Object[]> findTendenciaMensual(
            @Param("tenantId") String tenantId,
            @Param("inicio") LocalDateTime inicio,
            @Param("fin") LocalDateTime fin,
            @Param("sucursalId") Long sucursalId
    );

    // ── Ventas por vendedor ──

    @Query("SELECT v.vendedor.id, v.vendedor.nombre, COUNT(v), SUM(v.total) " +
           "FROM Venta v " +
           "WHERE v.tenantId = :tenantId AND v.createdAt BETWEEN :inicio AND :fin " +
           "AND (:sucursalId IS NULL OR v.sucursalId = :sucursalId) " +
           "GROUP BY v.vendedor.id, v.vendedor.nombre " +
           "ORDER BY SUM(v.total) DESC")
    List<Object[]> findVentasPorVendedor(
            @Param("tenantId") String tenantId,
            @Param("inicio") LocalDateTime inicio,
            @Param("fin") LocalDateTime fin,
            @Param("sucursalId") Long sucursalId
    );

    // ── Ventas por categoría (nativeQuery para NULLIF/TRIM) ──

    @Query(value = "SELECT COALESCE(c.nombre, 'Sin categoría') AS cat, " +
                   "SUM(dv.cantidad) AS unidades, " +
                   "SUM(dv.subtotal) AS ingresos_total, " +
                   "COUNT(DISTINCT dv.venta_id) AS ventas_count " +
                   "FROM detalles_venta dv " +
                   "JOIN ventas v ON v.id = dv.venta_id " +
                   "JOIN productos p ON p.id = dv.producto_id " +
                   "LEFT JOIN categorias c ON c.id = p.categoria_id " +
                   "WHERE v.tenant_id = :tenantId AND v.created_at BETWEEN :inicio AND :fin " +
                   "AND (:sucursalId IS NULL OR v.sucursal_id = :sucursalId) " +
                   "GROUP BY COALESCE(c.nombre, 'Sin categoría') " +
                   "ORDER BY SUM(dv.subtotal) DESC",
           nativeQuery = true)
    List<Object[]> findVentasPorCategoria(
            @Param("tenantId") String tenantId,
            @Param("inicio") LocalDateTime inicio,
            @Param("fin") LocalDateTime fin,
            @Param("sucursalId") Long sucursalId
    );

    // ── Ventas por método de pago ──

    @Query("SELECT v.metodoPago, COUNT(v), SUM(v.total) " +
           "FROM Venta v " +
           "WHERE v.tenantId = :tenantId AND v.createdAt BETWEEN :inicio AND :fin " +
           "AND (:sucursalId IS NULL OR v.sucursalId = :sucursalId) " +
           "GROUP BY v.metodoPago " +
           "ORDER BY SUM(v.total) DESC")
    List<Object[]> findVentasPorMetodoPago(
            @Param("tenantId") String tenantId,
            @Param("inicio") LocalDateTime inicio,
            @Param("fin") LocalDateTime fin,
            @Param("sucursalId") Long sucursalId
    );

    // ── Top/bottom productos vendidos por ingresos o unidades ──

    @Query("SELECT dv.producto.id, dv.producto.nombre, SUM(dv.cantidad), SUM(dv.subtotal) " +
           "FROM DetalleVenta dv " +
           "JOIN dv.venta v " +
           "WHERE v.tenantId = :tenantId AND v.createdAt BETWEEN :inicio AND :fin " +
           "AND (:sucursalId IS NULL OR v.sucursalId = :sucursalId) " +
           "GROUP BY dv.producto.id, dv.producto.nombre " +
           "ORDER BY SUM(dv.subtotal) DESC")
    List<Object[]> findTopProductosVendidosPorIngresos(
            @Param("tenantId") String tenantId,
            @Param("inicio") LocalDateTime inicio,
            @Param("fin") LocalDateTime fin,
            @Param("sucursalId") Long sucursalId
    );

    @Query("SELECT dv.producto.id, dv.producto.nombre, SUM(dv.cantidad), SUM(dv.subtotal) " +
           "FROM DetalleVenta dv " +
           "JOIN dv.venta v " +
           "WHERE v.tenantId = :tenantId AND v.createdAt BETWEEN :inicio AND :fin " +
           "AND (:sucursalId IS NULL OR v.sucursalId = :sucursalId) " +
           "GROUP BY dv.producto.id, dv.producto.nombre " +
           "ORDER BY SUM(dv.cantidad) ASC")
    List<Object[]> findBottomProductosVendidosPorUnidades(
            @Param("tenantId") String tenantId,
            @Param("inicio") LocalDateTime inicio,
            @Param("fin") LocalDateTime fin,
            @Param("sucursalId") Long sucursalId
    );

    @Query("SELECT dv.producto.id, dv.producto.nombre, SUM(dv.cantidad), SUM(dv.subtotal) " +
           "FROM DetalleVenta dv " +
           "JOIN dv.venta v " +
           "WHERE v.tenantId = :tenantId AND v.createdAt BETWEEN :inicio AND :fin " +
           "AND (:sucursalId IS NULL OR v.sucursalId = :sucursalId) " +
           "GROUP BY dv.producto.id, dv.producto.nombre " +
           "ORDER BY SUM(dv.subtotal) ASC")
    List<Object[]> findBottomProductosVendidosPorIngresos(
            @Param("tenantId") String tenantId,
            @Param("inicio") LocalDateTime inicio,
            @Param("fin") LocalDateTime fin,
            @Param("sucursalId") Long sucursalId
    );

    @Query("SELECT DISTINCT v FROM Venta v LEFT JOIN FETCH v.detalles WHERE v.tenantId = :tenantId AND v.vendedor.id = :vendedorId AND v.createdAt BETWEEN :inicio AND :fin")
    List<Venta> findByTenantIdAndVendedorIdAndPeriodo(
            @Param("tenantId")    String tenantId,
            @Param("vendedorId")  Long vendedorId,
            @Param("inicio")      LocalDateTime inicio,
            @Param("fin")         LocalDateTime fin);

    List<Venta> findByCajaIdAndTenantId(Long cajaId, String tenantId);

    // ── Top clientes por monto comprado ──

    @Query(value = "SELECT v.cliente_id, c.nombre, COUNT(v.id), SUM(v.total), MAX(v.created_at) " +
                   "FROM ventas v " +
                   "JOIN clientes c ON c.id = v.cliente_id " +
                   "WHERE v.tenant_id = :tenantId AND v.created_at BETWEEN :inicio AND :fin " +
                   "  AND v.cliente_id IS NOT NULL " +
                   "AND (:sucursalId IS NULL OR v.sucursal_id = :sucursalId) " +
                   "GROUP BY v.cliente_id, c.nombre " +
                   "ORDER BY SUM(v.total) DESC",
           nativeQuery = true)
    List<Object[]> findTopClientesPorGasto(
            @Param("tenantId") String tenantId,
            @Param("inicio") LocalDateTime inicio,
            @Param("fin") LocalDateTime fin,
            @Param("sucursalId") Long sucursalId
    );

    @Modifying
    @Query(value = "UPDATE ventas SET sucursal_id = :sucursalId WHERE tenant_id = :tenantId AND sucursal_id IS NULL", nativeQuery = true)
    void asignarSucursalDondeEsNulo(@Param("sucursalId") Long sucursalId, @Param("tenantId") String tenantId);
}
