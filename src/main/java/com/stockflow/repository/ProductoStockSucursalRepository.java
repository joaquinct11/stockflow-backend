package com.stockflow.repository;

import com.stockflow.entity.ProductoStockSucursal;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;
import java.util.Optional;

public interface ProductoStockSucursalRepository extends JpaRepository<ProductoStockSucursal, Long> {

    Optional<ProductoStockSucursal> findByProductoIdAndSucursalId(Long productoId, Long sucursalId);

    List<ProductoStockSucursal> findBySucursalIdAndTenantId(Long sucursalId, String tenantId);

    List<ProductoStockSucursal> findByProductoIdAndTenantId(Long productoId, String tenantId);

    /** Suma total del stock de un producto en todos los locales del tenant. */
    @Query("SELECT COALESCE(SUM(p.stockActual), 0) FROM ProductoStockSucursal p WHERE p.producto.id = :productoId AND p.tenantId = :tenantId")
    int sumStockByProductoIdAndTenantId(@Param("productoId") Long productoId, @Param("tenantId") String tenantId);

    /**
     * Productos bajo stock en una sucursal. Para productos CON variantes usa la suma
     * de producto_variante_stock_sucursal; para los demás usa producto_stock_sucursal.
     * Devuelve filas: [producto_id, nombre, stock_efectivo, stock_minimo, tipo]
     */
    @Query(value = """
            WITH stock_efectivo AS (
                SELECT p.id AS producto_id,
                       COALESCE(pss.stock_actual, 0) AS stock
                FROM productos p
                LEFT JOIN producto_stock_sucursal pss
                       ON pss.producto_id = p.id
                      AND pss.sucursal_id = :sucursalId
                      AND pss.tenant_id   = :tenantId
                WHERE p.tenant_id = :tenantId
                  AND p.activo    = true
                  AND NOT EXISTS (
                      SELECT 1 FROM producto_variantes pv2
                      WHERE pv2.producto_id = p.id
                        AND pv2.activo = true
                        AND pv2.tenant_id = :tenantId
                  )
                UNION ALL
                SELECT p.id AS producto_id,
                       COALESCE(SUM(pvss.stock_actual), 0) AS stock
                FROM productos p
                JOIN producto_variantes pv
                     ON pv.producto_id = p.id
                    AND pv.activo      = true
                    AND pv.tenant_id   = :tenantId
                LEFT JOIN producto_variante_stock_sucursal pvss
                       ON pvss.variante_id = pv.id
                      AND pvss.sucursal_id = :sucursalId
                      AND pvss.tenant_id   = :tenantId
                WHERE p.tenant_id = :tenantId
                  AND p.activo    = true
                GROUP BY p.id
            )
            SELECT p.id, p.nombre, se.stock AS stock_actual, p.stock_minimo, p.tipo
            FROM stock_efectivo se
            JOIN productos p ON p.id = se.producto_id
            WHERE p.activo = true
              AND se.stock < p.stock_minimo
            ORDER BY (p.stock_minimo - se.stock) DESC
            LIMIT 10
            """, nativeQuery = true)
    List<Object[]> findBajoStockEfectivoBySucursal(
            @Param("sucursalId") Long sucursalId,
            @Param("tenantId") String tenantId);

    /** Inicialización masiva para sucursal PRINCIPAL: migra el stock_actual existente de cada producto. */
    @Modifying
    @Query(value = """
        INSERT INTO producto_stock_sucursal (producto_id, sucursal_id, tenant_id, stock_actual)
        SELECT p.id, :sucursalId, p.tenant_id, p.stock_actual
        FROM productos p
        WHERE p.tenant_id = :tenantId
          AND p.activo = true
        ON CONFLICT (producto_id, sucursal_id) DO NOTHING
        """, nativeQuery = true)
    void inicializarStockParaSucursal(@Param("sucursalId") Long sucursalId, @Param("tenantId") String tenantId);

    /** Resync de stock de la sucursal PRINCIPAL con productos.stock_actual (corrige desincronización). */
    @Modifying
    @Query(value = """
        UPDATE producto_stock_sucursal
        SET stock_actual = (
            SELECT p.stock_actual FROM productos p WHERE p.id = producto_id
        )
        WHERE sucursal_id = :sucursalId
          AND tenant_id = :tenantId
        """, nativeQuery = true)
    void resyncStockPrincipal(@Param("sucursalId") Long sucursalId, @Param("tenantId") String tenantId);

    /** Inicialización masiva para sucursales NUEVAS: todas las filas comienzan con stock 0. */
    @Modifying
    @Query(value = """
        INSERT INTO producto_stock_sucursal (producto_id, sucursal_id, tenant_id, stock_actual)
        SELECT p.id, :sucursalId, p.tenant_id, 0
        FROM productos p
        WHERE p.tenant_id = :tenantId
          AND p.activo = true
        ON CONFLICT (producto_id, sucursal_id) DO NOTHING
        """, nativeQuery = true)
    void inicializarStockVacioParaSucursal(@Param("sucursalId") Long sucursalId, @Param("tenantId") String tenantId);
}
