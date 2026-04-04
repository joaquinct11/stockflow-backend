package com.stockflow.repository;

import com.stockflow.entity.MovimientoInventario;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;
import java.time.LocalDateTime;
import java.util.List;

@Repository
public interface MovimientoInventarioRepository extends JpaRepository<MovimientoInventario, Long> {

    List<MovimientoInventario> findByProductoId(Long productoId);

    List<MovimientoInventario> findByUsuarioId(Long usuarioId);

    List<MovimientoInventario> findByTenantId(String tenantId);

    List<MovimientoInventario> findByTipo(String tipo);

    @Query("SELECT m FROM MovimientoInventario m WHERE m.producto.id = :productoId AND m.createdAt BETWEEN :inicio AND :fin")
    List<MovimientoInventario> findMovimientosPorProductoYPeriodo(
            @Param("productoId") Long productoId,
            @Param("inicio") LocalDateTime inicio,
            @Param("fin") LocalDateTime fin
    );

    List<MovimientoInventario> findByTipoAndTenantId(String tipo, String tenantId);

    @Query("SELECT COALESCE(SUM(m.cantidad), 0) FROM MovimientoInventario m " +
           "WHERE m.tenantId = :tenantId AND m.tipo = :tipo " +
           "AND m.createdAt BETWEEN :inicio AND :fin")
    long sumCantidadByTenantIdAndTipoAndPeriodo(
            @Param("tenantId") String tenantId,
            @Param("tipo") String tipo,
            @Param("inicio") LocalDateTime inicio,
            @Param("fin") LocalDateTime fin
    );

    @Query("SELECT m.producto.id, m.producto.nombre, m.tipo, SUM(m.cantidad) AS total " +
           "FROM MovimientoInventario m " +
           "WHERE m.tenantId = :tenantId AND m.tipo IN ('ENTRADA','SALIDA') " +
           "AND m.createdAt BETWEEN :inicio AND :fin " +
           "GROUP BY m.producto.id, m.producto.nombre, m.tipo " +
           "ORDER BY total DESC")
    List<Object[]> findTopMovimientosProductos(
            @Param("tenantId") String tenantId,
            @Param("inicio") LocalDateTime inicio,
            @Param("fin") LocalDateTime fin
    );

    // ── Salidas por producto en rango (para cálculo de cobertura) ──

    @Query("SELECT m.producto.id, m.producto.nombre, m.producto.stockActual, SUM(m.cantidad) " +
           "FROM MovimientoInventario m " +
           "WHERE m.tenantId = :tenantId AND m.tipo = 'SALIDA' AND m.createdAt BETWEEN :inicio AND :fin " +
           "GROUP BY m.producto.id, m.producto.nombre, m.producto.stockActual " +
           "ORDER BY SUM(m.cantidad) DESC")
    List<Object[]> findSalidasPorProductoEnRango(
            @Param("tenantId") String tenantId,
            @Param("inicio") LocalDateTime inicio,
            @Param("fin") LocalDateTime fin
    );
}