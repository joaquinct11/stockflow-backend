package com.stockflow.repository;

import com.stockflow.entity.Suscripcion;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;
import org.springframework.transaction.annotation.Transactional;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

@Repository
public interface SuscripcionRepository extends JpaRepository<Suscripcion, Long> {

    Optional<Suscripcion> findByUsuarioPrincipalId(Long usuarioId);

    /** Suscripción más reciente de un usuario en un tenant específico. */
    Optional<Suscripcion> findFirstByUsuarioPrincipalIdAndTenantIdOrderByIdDesc(Long usuarioId, String tenantId);

    List<Suscripcion> findAllByUsuarioPrincipalId(Long usuarioId);

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

    Optional<Suscripcion> findFirstByCulqiCustomerIdOrderByIdDesc(String culqiCustomerId);

    /** Todas las suscripciones de un mismo cliente Culqi, de más reciente a más antigua. */
    java.util.List<Suscripcion> findAllByCulqiCustomerIdOrderByIdDesc(String culqiCustomerId);

    long countByTenantId(String tenantId);

    /**
     * CAS atómico: actualiza el estado solo si preapprovalId sigue siendo el esperado.
     * Retorna 1 si se actualizó, 0 si preapprovalId ya fue reemplazado (race condition).
     * Protege contra webhooks de suscripciones obsoletas que lleguen tras un upgrade/downgrade.
     */
    @Modifying
    @Transactional
    @Query("UPDATE Suscripcion s SET s.estado = :estado WHERE s.id = :id AND s.preapprovalId = :expectedPreapprovalId")
    int updateEstadoIfPreapprovalIdMatches(
            @Param("id") Long id,
            @Param("estado") String estado,
            @Param("expectedPreapprovalId") String expectedPreapprovalId);

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

    /**
     * Suscripciones ACTIVAS con preapprovalId (Culqi automático) cuyo
     * fechaProximoCobro cae entre dos momentos. Se usa para avisar 1 día antes del cargo.
     */
    @Query("""
            SELECT s FROM Suscripcion s
            WHERE s.estado = 'ACTIVA'
              AND s.preapprovalId IS NOT NULL
              AND s.preapprovalId <> ''
              AND s.fechaProximoCobro IS NOT NULL
              AND s.fechaProximoCobro BETWEEN :desde AND :hasta
            """)
    List<Suscripcion> findActivasCulqiPorCobrarEntre(
            @Param("desde") LocalDateTime desde,
            @Param("hasta") LocalDateTime hasta
    );

}
