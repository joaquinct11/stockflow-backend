package com.stockflow.repository;

import com.stockflow.entity.MovimientoInventario;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;
import java.time.LocalDate;
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

    // ── Vencimientos: se consultan en movimientos (ENTRADA con lote) ──────────
    // El campo fechaVencimiento vive en MovimientoInventario, no en Producto.

    /** Productos distintos con stock > 0 cuyo lote ya está vencido. */
    @Query("""
            SELECT DISTINCT m.producto FROM MovimientoInventario m
            WHERE m.tenantId = :tenantId
              AND m.tipo = 'ENTRADA'
              AND m.fechaVencimiento IS NOT NULL
              AND m.fechaVencimiento < :hoy
              AND m.producto.stockActual > 0
              AND m.producto.activo = true
            """)
    List<com.stockflow.entity.Producto> findProductosConLoteVencido(
            @Param("tenantId") String tenantId,
            @Param("hoy")      LocalDate hoy
    );

    /** Productos distintos con stock > 0 cuyo lote vence entre :desde y :hasta. */
    @Query("""
            SELECT DISTINCT m.producto FROM MovimientoInventario m
            WHERE m.tenantId = :tenantId
              AND m.tipo = 'ENTRADA'
              AND m.fechaVencimiento IS NOT NULL
              AND m.fechaVencimiento >= :desde
              AND m.fechaVencimiento <= :hasta
              AND m.producto.stockActual > 0
              AND m.producto.activo = true
            """)
    List<com.stockflow.entity.Producto> findProductosConLotePorVencer(
            @Param("tenantId") String tenantId,
            @Param("desde")    LocalDate desde,
            @Param("hasta")    LocalDate hasta
    );

    // ── Lotes con fecha de vencimiento (para pestaña Lotes en Inventario) ──

    /**
     * Devuelve todos los movimientos de ENTRADA con fechaVencimiento para un tenant,
     * ordenados de más próximo a vencer al más lejano.
     * Usa JOIN FETCH para evitar N+1 al acceder a producto.
     * El mapeo a LoteVencimientoDTO y el cálculo de diasRestantes
     * se realizan en el controller.
     */
    @Query("""
            SELECT m FROM MovimientoInventario m
            JOIN FETCH m.producto p
            WHERE m.tenantId = :tenantId
              AND m.tipo = 'ENTRADA'
              AND m.fechaVencimiento IS NOT NULL
              AND p.activo = true
            ORDER BY m.fechaVencimiento ASC
            """)
    List<MovimientoInventario> findEntradasConVencimientoPorTenant(
            @Param("tenantId") String tenantId
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