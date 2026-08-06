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

    // ── Lookups puntuales ────────────────────────────────────────────────────
    Optional<Producto> findByCodigoBarras(String codigoBarras);
    long countByTenantId(String tenantId);
    long countByTenantIdAndActivoTrue(String tenantId);

    /**
     * findById con JOIN FETCH — usar después de save() para devolver el
     * producto con categoriaRef y unidadMedida completamente inicializados.
     * El findById estándar de JPA devuelve la entidad con el proxy lazy sin
     * cargar, lo que produce categoriaNombre = null en el DTO de respuesta.
     */
    @Query("""
            SELECT p FROM Producto p
            LEFT JOIN FETCH p.categoriaRef
            LEFT JOIN FETCH p.unidadMedida
            WHERE p.id = :id
            """)
    Optional<Producto> findByIdWithJoins(@Param("id") Long id);

    // ── Listados: siempre con JOIN FETCH para cargar categoría y unidad ───────
    // FetchType.LAZY en categoriaRef causa que el proxy de Hibernate no pueda
    // inicializarse fuera de transacción → categoriaNombre = null en el DTO.
    // LEFT JOIN FETCH garantiza que los datos se traigan en una sola query.

    @Query("""
            SELECT p FROM Producto p
            LEFT JOIN FETCH p.categoriaRef
            LEFT JOIN FETCH p.unidadMedida
            WHERE p.tenantId = :tenantId
              AND p.activo = true
            """)
    List<Producto> findByTenantId(@Param("tenantId") String tenantId);

    @Query("""
            SELECT p FROM Producto p
            LEFT JOIN FETCH p.categoriaRef
            LEFT JOIN FETCH p.unidadMedida
            WHERE p.tenantId = :tenantId
            """)
    List<Producto> findAllByTenantId(@Param("tenantId") String tenantId);

    @Query("""
            SELECT p FROM Producto p
            LEFT JOIN FETCH p.categoriaRef
            LEFT JOIN FETCH p.unidadMedida
            WHERE (LOWER(p.nombre) LIKE LOWER(CONCAT('%', :nombre, '%'))
               OR  LOWER(COALESCE(p.componentes,'')) LIKE LOWER(CONCAT('%', :nombre, '%')))
              AND p.tenantId = :tenantId
              AND p.activo = true
            """)
    List<Producto> findByNombreContainingIgnoreCaseAndTenantId(
            @Param("nombre") String nombre,
            @Param("tenantId") String tenantId);

    @Query("""
            SELECT p FROM Producto p
            LEFT JOIN FETCH p.categoriaRef
            LEFT JOIN FETCH p.unidadMedida
            WHERE p.activo = true
            """)
    List<Producto> findByActivoTrue();

    @Query("""
            SELECT p FROM Producto p
            LEFT JOIN FETCH p.categoriaRef
            LEFT JOIN FETCH p.unidadMedida
            WHERE p.stockActual < p.stockMinimo
              AND p.tenantId = :tenantId
            """)
    List<Producto> findProductosBajoStock(@Param("tenantId") String tenantId);

    @Query("SELECT COALESCE(SUM(p.stockActual * p.costoUnitario), null) FROM Producto p " +
           "WHERE p.tenantId = :tenantId AND p.activo = true")
    BigDecimal calcularValorizacionStock(@Param("tenantId") String tenantId);

    /** Suma el stock actual de todos los productos del tenant — usado en onboarding. */
    @Query("SELECT COALESCE(SUM(p.stockActual), 0) FROM Producto p WHERE p.tenantId = :tenantId")
    long sumStockActualByTenantId(@Param("tenantId") String tenantId);

    /** True si el producto tiene ventas o devoluciones asociadas (impide borrado físico). */
    @Query(value = """
            SELECT EXISTS (
                SELECT 1 FROM detalles_venta WHERE producto_id = :id
                UNION ALL
                SELECT 1 FROM devolucion_detalle WHERE producto_id = :id
            )
            """, nativeQuery = true)
    boolean tieneRegistrosRelacionados(@Param("id") Long id);

    // ── Slow movers: activos con stock > 0 sin salidas desde una fecha ──

    @Query("""
            SELECT p FROM Producto p
            LEFT JOIN FETCH p.categoriaRef
            LEFT JOIN FETCH p.unidadMedida
            WHERE p.tenantId = :tenantId
              AND p.activo = true
              AND p.stockActual > 0
              AND p.id NOT IN (
                  SELECT m.producto.id FROM MovimientoInventario m
                  WHERE m.tenantId = :tenantId AND m.tipo = 'SALIDA' AND m.createdAt >= :desde
              )
            """)
    List<Producto> findSlowMovers(
            @Param("tenantId") String tenantId,
            @Param("desde") java.time.LocalDateTime desde
    );
}