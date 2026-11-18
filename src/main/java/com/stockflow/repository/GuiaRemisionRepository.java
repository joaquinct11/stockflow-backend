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

import java.util.Optional;

public interface GuiaRemisionRepository extends JpaRepository<GuiaRemision, Long> {

    Page<GuiaRemision> findByTenantId(String tenantId, Pageable pageable);

    /** Lectura normal para consultas (sin lock). */
    Optional<GuiaRemision> findByIdAndTenantId(Long id, String tenantId);

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
}
