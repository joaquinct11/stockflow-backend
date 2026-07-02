package com.stockflow.repository;

import com.stockflow.entity.ProductoVarianteStockSucursal;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;
import java.util.Optional;

public interface ProductoVarianteStockSucursalRepository extends JpaRepository<ProductoVarianteStockSucursal, Long> {

    Optional<ProductoVarianteStockSucursal> findByVarianteIdAndSucursalId(Long varianteId, Long sucursalId);

    List<ProductoVarianteStockSucursal> findByVarianteIdInAndSucursalId(List<Long> varianteIds, Long sucursalId);

    List<ProductoVarianteStockSucursal> findByVarianteIdAndTenantId(Long varianteId, String tenantId);

    List<ProductoVarianteStockSucursal> findBySucursalIdAndTenantId(Long sucursalId, String tenantId);

    /** Suma del stock de una variante en todos los locales del tenant. */
    @Query("SELECT COALESCE(SUM(p.stockActual), 0) FROM ProductoVarianteStockSucursal p WHERE p.varianteId = :varianteId AND p.tenantId = :tenantId")
    int sumStockByVarianteIdAndTenantId(@Param("varianteId") Long varianteId, @Param("tenantId") String tenantId);

    /**
     * Suma el stock de todas las variantes de cada producto para una sucursal dada.
     * Devuelve filas [producto_id (Long), stock_total (BigDecimal)].
     */
    @Query(value = """
        SELECT pv.producto_id, COALESCE(SUM(pvss.stock_actual), 0)
        FROM producto_variante_stock_sucursal pvss
        JOIN producto_variantes pv ON pv.id = pvss.variante_id
        WHERE pvss.sucursal_id = :sucursalId
          AND pvss.tenant_id   = :tenantId
        GROUP BY pv.producto_id
        """, nativeQuery = true)
    List<Object[]> sumStockPorProductoYSucursal(@Param("sucursalId") Long sucursalId, @Param("tenantId") String tenantId);

    /** Inicializa filas para una sucursal nueva: copia stock=0 para todas las variantes activas del tenant. */
    @Modifying
    @Query(value = """
        INSERT INTO producto_variante_stock_sucursal (variante_id, sucursal_id, tenant_id, stock_actual, stock_minimo)
        SELECT pv.id, :sucursalId, pv.tenant_id, 0, pv.stock_minimo
        FROM producto_variantes pv
        WHERE pv.tenant_id = :tenantId
          AND pv.activo = true
        ON CONFLICT (variante_id, sucursal_id) DO NOTHING
        """, nativeQuery = true)
    void inicializarStockVacioParaSucursal(@Param("sucursalId") Long sucursalId, @Param("tenantId") String tenantId);

    /** Inicializa filas para la sucursal PRINCIPAL: copia el stock_actual global de cada variante. */
    @Modifying
    @Query(value = """
        INSERT INTO producto_variante_stock_sucursal (variante_id, sucursal_id, tenant_id, stock_actual, stock_minimo)
        SELECT pv.id, :sucursalId, pv.tenant_id, pv.stock_actual, pv.stock_minimo
        FROM producto_variantes pv
        WHERE pv.tenant_id = :tenantId
          AND pv.activo = true
        ON CONFLICT (variante_id, sucursal_id) DO NOTHING
        """, nativeQuery = true)
    void inicializarStockPrincipalParaSucursal(@Param("sucursalId") Long sucursalId, @Param("tenantId") String tenantId);
}
