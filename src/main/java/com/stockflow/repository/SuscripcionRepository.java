package com.stockflow.repository;

import com.stockflow.entity.Suscripcion;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

@Repository
public interface SuscripcionRepository extends JpaRepository<Suscripcion, Long> {

    Optional<Suscripcion> findByUsuarioPrincipalId(Long usuarioId);

    List<Suscripcion> findByEstado(String estado);

    // ✅ NUEVOS: Filtrar por tenant
    List<Suscripcion> findByTenantId(String tenantId);

    List<Suscripcion> findByEstadoAndTenantId(String estado, String tenantId);

    Optional<Suscripcion> findByTenantIdAndUsuarioPrincipalId(String tenantId, Long usuarioPrincipalId);

    /** Suscripción más antigua (para búsquedas históricas). */
    Optional<Suscripcion> findFirstByTenantIdOrderByIdAsc(String tenantId);

    /** Suscripción más reciente — usar para validar plan actual del tenant. */
    Optional<Suscripcion> findFirstByTenantIdOrderByIdDesc(String tenantId);

    Optional<Suscripcion> findByPreapprovalId(String preapprovalId);

    long countByTenantId(String tenantId);

    /**
     * Suscripciones ACTIVAS sin preapprovalId (cobro manual) cuya fechaProximoCobro
     * cae entre dos momentos. Las que tienen preapprovalId (Culqi) se renuevan recurrentemente.
     */
    @Query("""
            SELECT s FROM Suscripcion s
            WHERE s.estado = 'ACTIVA'
              AND (s.preapprovalId IS NULL OR s.preapprovalId = '')
              AND s.fechaProximoCobro IS NOT NULL
              AND s.fechaProximoCobro BETWEEN :desde AND :hasta
            """)
    List<Suscripcion> findActivasManualesPorVencerEntre(
            @Param("desde") LocalDateTime desde,
            @Param("hasta") LocalDateTime hasta
    );

    /**
     * Trials por vencer — no se renuevan automáticamente, el usuario debe contratar.
     */
    @Query("""
            SELECT s FROM Suscripcion s
            WHERE s.estado = 'TRIAL'
              AND s.trialEndDate IS NOT NULL
              AND s.trialEndDate BETWEEN :desde AND :hasta
            """)
    List<Suscripcion> findTrialsPorVencerEntre(
            @Param("desde") LocalDateTime desde,
            @Param("hasta") LocalDateTime hasta
    );

}
