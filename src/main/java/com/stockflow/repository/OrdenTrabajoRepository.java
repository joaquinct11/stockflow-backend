package com.stockflow.repository;

import com.stockflow.dto.OtResumenDTO;
import com.stockflow.entity.OrdenTrabajo;
import jakarta.persistence.LockModeType;
import jakarta.persistence.QueryHint;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.jpa.repository.QueryHints;
import org.springframework.data.repository.query.Param;

import java.util.Optional;

public interface OrdenTrabajoRepository extends JpaRepository<OrdenTrabajo, Long> {

    Optional<OrdenTrabajo> findByIdAndTenantId(Long id, String tenantId);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @QueryHints(@QueryHint(name = "jakarta.persistence.lock.timeout", value = "5000"))
    @Query("SELECT ot FROM OrdenTrabajo ot WHERE ot.id = :id AND ot.tenantId = :tenantId")
    Optional<OrdenTrabajo> findByIdAndTenantIdForUpdate(@Param("id") Long id, @Param("tenantId") String tenantId);

    // ── Paginated listing ─────────────────────────────────────────────────────

    @Query("""
            SELECT ot.id            AS id,
                   ot.numeroOt      AS numeroOt,
                   ot.estado        AS estado,
                   ot.prioridad     AS prioridad,
                   ot.equipo.nombre AS equipoNombre,
                   ot.cliente.nombre AS clienteNombre,
                   ot.sucursal.nombre AS sucursalNombre,
                   ot.tecnico.nombre AS tecnicoNombre,
                   ot.createdAt     AS createdAt
            FROM OrdenTrabajo ot
            WHERE ot.tenantId = :tenantId
              AND (:estado IS NULL OR ot.estado = :estado)
              AND (:equipoClienteId IS NULL OR ot.equipo.id = :equipoClienteId)
              AND (:clienteId IS NULL OR ot.cliente.id = :clienteId)
              AND (:s = '' OR LOWER(ot.numeroOt) LIKE LOWER(CONCAT('%', :s, '%'))
                           OR LOWER(ot.cliente.nombre) LIKE LOWER(CONCAT('%', :s, '%'))
                           OR LOWER(ot.equipo.nombre) LIKE LOWER(CONCAT('%', :s, '%')))
            ORDER BY ot.createdAt DESC
            """)
    Page<OtResumenDTO> findPaginado(
            @Param("tenantId") String tenantId,
            @Param("estado") String estado,
            @Param("equipoClienteId") Long equipoClienteId,
            @Param("clienteId") Long clienteId,
            @Param("s") String search,
            Pageable pageable);

    // ── Paginated listing filtered by assigned técnico ────────────────────────

    @Query("""
            SELECT ot.id            AS id,
                   ot.numeroOt      AS numeroOt,
                   ot.estado        AS estado,
                   ot.prioridad     AS prioridad,
                   ot.equipo.nombre AS equipoNombre,
                   ot.cliente.nombre AS clienteNombre,
                   ot.sucursal.nombre AS sucursalNombre,
                   ot.tecnico.nombre AS tecnicoNombre,
                   ot.createdAt     AS createdAt
            FROM OrdenTrabajo ot
            WHERE ot.tenantId = :tenantId
              AND ot.tecnico.id = :tecnicoId
              AND (:estado IS NULL OR ot.estado = :estado)
              AND (:equipoClienteId IS NULL OR ot.equipo.id = :equipoClienteId)
              AND (:clienteId IS NULL OR ot.cliente.id = :clienteId)
              AND (:s = '' OR LOWER(ot.numeroOt) LIKE LOWER(CONCAT('%', :s, '%'))
                           OR LOWER(ot.cliente.nombre) LIKE LOWER(CONCAT('%', :s, '%'))
                           OR LOWER(ot.equipo.nombre) LIKE LOWER(CONCAT('%', :s, '%')))
            ORDER BY ot.createdAt DESC
            """)
    Page<OtResumenDTO> findPaginadoByTecnico(
            @Param("tenantId") String tenantId,
            @Param("tecnicoId") Long tecnicoId,
            @Param("estado") String estado,
            @Param("equipoClienteId") Long equipoClienteId,
            @Param("clienteId") Long clienteId,
            @Param("s") String search,
            Pageable pageable);

    // ── Count queries ─────────────────────────────────────────────────────────

    @Query("SELECT COUNT(ot) FROM OrdenTrabajo ot WHERE ot.tenantId = :tenantId")
    long countTotal(@Param("tenantId") String tenantId);

    @Query("SELECT COUNT(ot) FROM OrdenTrabajo ot WHERE ot.tenantId = :tenantId AND ot.estado = :estado")
    long countByEstado(@Param("tenantId") String tenantId, @Param("estado") String estado);

    @Query("SELECT COUNT(ot) FROM OrdenTrabajo ot WHERE ot.tenantId = :tenantId AND ot.tecnico.id = :tecnicoId")
    long countTotalByTecnico(@Param("tenantId") String tenantId, @Param("tecnicoId") Long tecnicoId);

    @Query("SELECT COUNT(ot) FROM OrdenTrabajo ot WHERE ot.tenantId = :tenantId AND ot.tecnico.id = :tecnicoId AND ot.estado = :estado")
    long countByEstadoAndTecnico(@Param("tenantId") String tenantId, @Param("tecnicoId") Long tecnicoId, @Param("estado") String estado);
}
