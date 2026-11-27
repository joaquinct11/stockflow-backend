package com.stockflow.repository;

import com.stockflow.entity.gre.EstadoTributarioGRE;
import com.stockflow.entity.gre.GuiaRemision;
import jakarta.persistence.LockModeType;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;
import java.util.Optional;

public interface GuiaRemisionRepository extends JpaRepository<GuiaRemision, Long> {

    Page<GuiaRemision> findByTenantId(String tenantId, Pageable pageable);

    /**
     * Paginación con fetch explícito de sucursalOrigen y sucursalDestino
     * para evitar LazyInitializationException con open-in-view=false.
     */
    @Query(
        value = "SELECT g FROM GuiaRemision g " +
                "LEFT JOIN FETCH g.sucursalOrigen " +
                "LEFT JOIN FETCH g.sucursalDestino " +
                "WHERE g.tenantId = :tenantId",
        countQuery = "SELECT COUNT(g) FROM GuiaRemision g WHERE g.tenantId = :tenantId"
    )
    Page<GuiaRemision> findByTenantIdWithSucursales(
            @Param("tenantId") String tenantId, Pageable pageable);

    /** Lectura normal para consultas (sin lock). */
    Optional<GuiaRemision> findByIdAndTenantId(Long id, String tenantId);

    /** Lectura por ID con fetch explícito de sucursales para evitar LazyInitializationException. */
    @Query("SELECT g FROM GuiaRemision g " +
           "LEFT JOIN FETCH g.sucursalOrigen " +
           "LEFT JOIN FETCH g.sucursalDestino " +
           "WHERE g.id = :id AND g.tenantId = :tenantId")
    Optional<GuiaRemision> findByIdAndTenantIdWithSucursales(
            @Param("id") Long id, @Param("tenantId") String tenantId);

    /** Bloqueo pesimista para operaciones de escritura concurrente (emisión). */
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("SELECT g FROM GuiaRemision g WHERE g.id = :id AND g.tenantId = :tenantId")
    Optional<GuiaRemision> findByIdAndTenantIdForUpdate(@Param("id") Long id, @Param("tenantId") String tenantId);

    boolean existsByIdempotencyKey(String idempotencyKey);

    Optional<GuiaRemision> findByIdempotencyKey(String idempotencyKey);

    /** Correlativo máximo para la serie dada del tenant — para auto-incremento. */
    @Query("SELECT COALESCE(MAX(g.correlativo), 0) FROM GuiaRemision g WHERE g.tenantId = :tenantId AND g.serie = :serie")
    int findMaxCorrelativo(@Param("tenantId") String tenantId, @Param("serie") String serie);

    Page<GuiaRemision> findByTenantIdAndEstadoTributario(
            String tenantId, EstadoTributarioGRE estadoTributario, Pageable pageable);

    /**
     * Paginación con búsqueda full-text y filtro por estado tributario.
     * q=null/'' desactiva la búsqueda; estado=null desactiva el filtro de estado.
     */
    @Query(
        value = "SELECT g FROM GuiaRemision g " +
                "LEFT JOIN FETCH g.sucursalOrigen so " +
                "LEFT JOIN FETCH g.sucursalDestino sd " +
                "WHERE g.tenantId = :tenantId " +
                "AND (:q IS NULL OR :q = '' OR " +
                "  LOWER(CONCAT(g.serie, '-', CAST(g.correlativo AS string))) LIKE LOWER(CONCAT('%', :q, '%')) OR " +
                "  LOWER(COALESCE(g.destinatarioRazonSocial, '')) LIKE LOWER(CONCAT('%', :q, '%')) OR " +
                "  LOWER(COALESCE(so.nombre, '')) LIKE LOWER(CONCAT('%', :q, '%')) OR " +
                "  LOWER(COALESCE(sd.nombre, '')) LIKE LOWER(CONCAT('%', :q, '%'))) " +
                "AND (:#{#estado} IS NULL OR g.estadoTributario = :estado)",
        countQuery = "SELECT COUNT(g) FROM GuiaRemision g " +
                "LEFT JOIN g.sucursalOrigen so " +
                "LEFT JOIN g.sucursalDestino sd " +
                "WHERE g.tenantId = :tenantId " +
                "AND (:q IS NULL OR :q = '' OR " +
                "  LOWER(CONCAT(g.serie, '-', CAST(g.correlativo AS string))) LIKE LOWER(CONCAT('%', :q, '%')) OR " +
                "  LOWER(COALESCE(g.destinatarioRazonSocial, '')) LIKE LOWER(CONCAT('%', :q, '%')) OR " +
                "  LOWER(COALESCE(so.nombre, '')) LIKE LOWER(CONCAT('%', :q, '%')) OR " +
                "  LOWER(COALESCE(sd.nombre, '')) LIKE LOWER(CONCAT('%', :q, '%'))) " +
                "AND (:#{#estado} IS NULL OR g.estadoTributario = :estado)"
    )
    Page<GuiaRemision> findByTenantIdWithSearch(
            @Param("tenantId") String tenantId,
            @Param("q") String q,
            @Param("estado") EstadoTributarioGRE estado,
            Pageable pageable);

    /** Conteo global por estado tributario para los KPIs del listado. */
    @Query("SELECT g.estadoTributario, COUNT(g) FROM GuiaRemision g WHERE g.tenantId = :tenantId GROUP BY g.estadoTributario")
    List<Object[]> countByEstadoTributarioForTenant(@Param("tenantId") String tenantId);

    /**
     * F-24: Búsqueda paginada con filtro opcional de sucursal (origen O destino).
     * GREs legacy (sin sucursal) siempre se incluyen para no romper tenants sin sucursales.
     */
    @Query(
        value = "SELECT g FROM GuiaRemision g " +
                "LEFT JOIN FETCH g.sucursalOrigen so " +
                "LEFT JOIN FETCH g.sucursalDestino sd " +
                "WHERE g.tenantId = :tenantId " +
                "AND (:sucursalId IS NULL OR so.id = :sucursalId OR sd.id = :sucursalId) " +
                "AND (:q IS NULL OR :q = '' OR " +
                "  LOWER(CONCAT(g.serie, '-', CAST(g.correlativo AS string))) LIKE LOWER(CONCAT('%', :q, '%')) OR " +
                "  LOWER(COALESCE(g.destinatarioRazonSocial, '')) LIKE LOWER(CONCAT('%', :q, '%')) OR " +
                "  LOWER(COALESCE(so.nombre, '')) LIKE LOWER(CONCAT('%', :q, '%')) OR " +
                "  LOWER(COALESCE(sd.nombre, '')) LIKE LOWER(CONCAT('%', :q, '%'))) " +
                "AND (:#{#estado} IS NULL OR g.estadoTributario = :estado)",
        countQuery = "SELECT COUNT(g) FROM GuiaRemision g " +
                "LEFT JOIN g.sucursalOrigen so " +
                "LEFT JOIN g.sucursalDestino sd " +
                "WHERE g.tenantId = :tenantId " +
                "AND (:sucursalId IS NULL OR so.id = :sucursalId OR sd.id = :sucursalId) " +
                "AND (:q IS NULL OR :q = '' OR " +
                "  LOWER(CONCAT(g.serie, '-', CAST(g.correlativo AS string))) LIKE LOWER(CONCAT('%', :q, '%')) OR " +
                "  LOWER(COALESCE(g.destinatarioRazonSocial, '')) LIKE LOWER(CONCAT('%', :q, '%')) OR " +
                "  LOWER(COALESCE(so.nombre, '')) LIKE LOWER(CONCAT('%', :q, '%')) OR " +
                "  LOWER(COALESCE(sd.nombre, '')) LIKE LOWER(CONCAT('%', :q, '%'))) " +
                "AND (:#{#estado} IS NULL OR g.estadoTributario = :estado)"
    )
    Page<GuiaRemision> findByTenantIdWithSearchAndSucursal(
            @Param("tenantId") String tenantId,
            @Param("sucursalId") Long sucursalId,
            @Param("q") String q,
            @Param("estado") EstadoTributarioGRE estado,
            Pageable pageable);

    /** F-24: Conteo por estado para KPIs, filtrado por sucursal opcional. */
    @Query("SELECT g.estadoTributario, COUNT(g) FROM GuiaRemision g " +
           "LEFT JOIN g.sucursalOrigen so LEFT JOIN g.sucursalDestino sd " +
           "WHERE g.tenantId = :tenantId " +
           "AND (:sucursalId IS NULL OR so.id = :sucursalId OR sd.id = :sucursalId) " +
           "GROUP BY g.estadoTributario")
    List<Object[]> countByEstadoTributarioForTenantAndSucursal(
            @Param("tenantId") String tenantId,
            @Param("sucursalId") Long sucursalId);
}
