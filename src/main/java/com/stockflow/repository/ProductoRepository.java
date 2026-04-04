package com.stockflow.repository;

import com.stockflow.entity.Producto;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;
import java.math.BigDecimal;
import java.util.List;
import java.util.Optional;

@Repository
public interface ProductoRepository extends JpaRepository<Producto, Long> {
    Optional<Producto> findByCodigoBarras(String codigoBarras);
    List<Producto> findByNombreContainingIgnoreCase(String nombre);
    List<Producto> findByTenantId(String tenantId);
    List<Producto> findByActivoTrue();
    long countByTenantId(String tenantId);
    @Query("SELECT p FROM Producto p WHERE p.stockActual < p.stockMinimo AND p.tenantId = :tenantId")
    List<Producto> findProductosBajoStock(@Param("tenantId") String tenantId);

    @Query("SELECT COALESCE(SUM(p.stockActual * p.costoUnitario), null) FROM Producto p " +
           "WHERE p.tenantId = :tenantId AND p.activo = true")
    BigDecimal calcularValorizacionStock(@Param("tenantId") String tenantId);

    // ── Slow movers: activos con stock > 0 sin salidas desde una fecha ──

    @Query("SELECT p FROM Producto p WHERE p.tenantId = :tenantId AND p.activo = true AND p.stockActual > 0 " +
           "AND p.id NOT IN (" +
               "SELECT m.producto.id FROM MovimientoInventario m " +
               "WHERE m.tenantId = :tenantId AND m.tipo = 'SALIDA' AND m.createdAt >= :desde" +
           ")")
    List<Producto> findSlowMovers(
            @Param("tenantId") String tenantId,
            @Param("desde") java.time.LocalDateTime desde
    );
}