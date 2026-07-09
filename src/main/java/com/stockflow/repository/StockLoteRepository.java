package com.stockflow.repository;

import com.stockflow.entity.StockLote;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.LocalDate;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.stream.Collectors;

public interface StockLoteRepository extends JpaRepository<StockLote, Long> {

    // Lotes vigentes para FEFO (sin filtro de sucursal — plan BÁSICO)
    @Query("""
            SELECT s FROM StockLote s
            WHERE s.productoId   = :productoId
              AND s.tenantId     = :tenantId
              AND s.fechaVencimiento >= :hoy
              AND s.stockActual  > 0
            ORDER BY s.fechaVencimiento ASC
            """)
    List<StockLote> findVigentesFefo(
            @Param("productoId") Long productoId,
            @Param("tenantId")   String tenantId,
            @Param("hoy")        LocalDate hoy
    );

    // Lotes vigentes para FEFO (con filtro de sucursal — plan PRO)
    @Query("""
            SELECT s FROM StockLote s
            WHERE s.productoId   = :productoId
              AND s.tenantId     = :tenantId
              AND s.sucursalId   = :sucursalId
              AND s.fechaVencimiento >= :hoy
              AND s.stockActual  > 0
            ORDER BY s.fechaVencimiento ASC
            """)
    List<StockLote> findVigentesFefoConSucursal(
            @Param("productoId") Long productoId,
            @Param("tenantId")   String tenantId,
            @Param("sucursalId") Long sucursalId,
            @Param("hoy")        LocalDate hoy
    );

    // Stock vigente total de un producto (sin sucursal)
    @Query("""
            SELECT COALESCE(SUM(s.stockActual), 0) FROM StockLote s
            WHERE s.productoId       = :productoId
              AND s.tenantId         = :tenantId
              AND s.fechaVencimiento >= :hoy
            """)
    Integer sumStockVigente(
            @Param("productoId") Long productoId,
            @Param("tenantId")   String tenantId,
            @Param("hoy")        LocalDate hoy
    );

    // Stock vigente total de un producto por sucursal
    @Query("""
            SELECT COALESCE(SUM(s.stockActual), 0) FROM StockLote s
            WHERE s.productoId       = :productoId
              AND s.tenantId         = :tenantId
              AND s.sucursalId       = :sucursalId
              AND s.fechaVencimiento >= :hoy
            """)
    Integer sumStockVigenteConSucursal(
            @Param("productoId") Long productoId,
            @Param("tenantId")   String tenantId,
            @Param("sucursalId") Long sucursalId,
            @Param("hoy")        LocalDate hoy
    );

    // Batch: stock vigente por lista de productos (para enriquecer el catálogo sin N+1)
    @Query("""
            SELECT s.productoId, COALESCE(SUM(s.stockActual), 0) FROM StockLote s
            WHERE s.productoId       IN :productoIds
              AND s.tenantId         = :tenantId
              AND s.fechaVencimiento >= :hoy
            GROUP BY s.productoId
            """)
    List<Object[]> sumStockVigenteByProductoIds(
            @Param("productoIds") List<Long> productoIds,
            @Param("tenantId")    String tenantId,
            @Param("hoy")         LocalDate hoy
    );

    // Batch por sucursal
    @Query("""
            SELECT s.productoId, COALESCE(SUM(s.stockActual), 0) FROM StockLote s
            WHERE s.productoId       IN :productoIds
              AND s.tenantId         = :tenantId
              AND s.sucursalId       = :sucursalId
              AND s.fechaVencimiento >= :hoy
            GROUP BY s.productoId
            """)
    List<Object[]> sumStockVigenteByProductoIdsConSucursal(
            @Param("productoIds") List<Long> productoIds,
            @Param("tenantId")    String tenantId,
            @Param("sucursalId")  Long sucursalId,
            @Param("hoy")         LocalDate hoy
    );

    // Para la página de Lotes — obtener stock_actual por movimientoId
    List<StockLote> findByMovimientoIdIn(List<Long> movimientoIds);

    Optional<StockLote> findByMovimientoId(Long movimientoId);

    // Lotes vigentes en orden INVERSO (vence más tarde primero) — para ajustes positivos
    @Query("""
            SELECT s FROM StockLote s
            WHERE s.productoId       = :productoId
              AND s.tenantId         = :tenantId
              AND s.fechaVencimiento >= :hoy
            ORDER BY s.fechaVencimiento DESC
            """)
    List<StockLote> findVigentesInverso(
            @Param("productoId") Long productoId,
            @Param("tenantId")   String tenantId,
            @Param("hoy")        LocalDate hoy
    );

    // ¿El producto tiene algún lote con vencimiento registrado?
    boolean existsByProductoIdAndTenantId(Long productoId, String tenantId);

    // Batch: ¿qué productos tienen lotes?
    @Query("""
            SELECT DISTINCT s.productoId FROM StockLote s
            WHERE s.productoId IN :productoIds AND s.tenantId = :tenantId
            """)
    List<Long> findProductosConLotes(
            @Param("productoIds") List<Long> productoIds,
            @Param("tenantId")    String tenantId
    );
}
