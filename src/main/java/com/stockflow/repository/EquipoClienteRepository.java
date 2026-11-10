package com.stockflow.repository;

import com.stockflow.dto.EquipoResumenDTO;
import com.stockflow.entity.EquipoCliente;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.Optional;

public interface EquipoClienteRepository extends JpaRepository<EquipoCliente, Long> {

    Optional<EquipoCliente> findByIdAndTenantId(Long id, String tenantId);

    long countByCliente_IdAndTenantId(Long clienteId, String tenantId);

    boolean existsByNumeroSerieAndTenantId(String numeroSerie, String tenantId);

    boolean existsByNumeroSerieAndTenantIdAndIdNot(String numeroSerie, String tenantId, Long id);

    // ── Paginated list queries ────────────────────────────────────────────────

    @Query("""
            SELECT e.id AS id, e.cliente.id AS clienteId, e.cliente.nombre AS clienteNombre,
                   e.nombre AS nombre, e.tipo AS tipo, e.marca AS marca, e.modelo AS modelo,
                   e.numeroSerie AS numeroSerie, e.ubicacion AS ubicacion, e.activo AS activo
            FROM EquipoCliente e
            WHERE e.tenantId = :tenantId
              AND (:clienteId IS NULL OR e.cliente.id = :clienteId)
              AND (:s = '' OR LOWER(e.nombre) LIKE LOWER(CONCAT('%', :s, '%'))
                           OR LOWER(e.marca)  LIKE LOWER(CONCAT('%', :s, '%'))
                           OR LOWER(e.modelo) LIKE LOWER(CONCAT('%', :s, '%'))
                           OR LOWER(COALESCE(e.numeroSerie, '')) LIKE LOWER(CONCAT('%', :s, '%')))
            ORDER BY e.nombre ASC
            """)
    Page<EquipoResumenDTO> findPaginadoTodos(
            @Param("tenantId") String tenantId,
            @Param("clienteId") Long clienteId,
            @Param("s") String search,
            Pageable pageable);

    @Query("""
            SELECT e.id AS id, e.cliente.id AS clienteId, e.cliente.nombre AS clienteNombre,
                   e.nombre AS nombre, e.tipo AS tipo, e.marca AS marca, e.modelo AS modelo,
                   e.numeroSerie AS numeroSerie, e.ubicacion AS ubicacion, e.activo AS activo
            FROM EquipoCliente e
            WHERE e.tenantId = :tenantId
              AND e.activo = true
              AND (:clienteId IS NULL OR e.cliente.id = :clienteId)
              AND (:s = '' OR LOWER(e.nombre) LIKE LOWER(CONCAT('%', :s, '%'))
                           OR LOWER(e.marca)  LIKE LOWER(CONCAT('%', :s, '%'))
                           OR LOWER(e.modelo) LIKE LOWER(CONCAT('%', :s, '%'))
                           OR LOWER(COALESCE(e.numeroSerie, '')) LIKE LOWER(CONCAT('%', :s, '%')))
            ORDER BY e.nombre ASC
            """)
    Page<EquipoResumenDTO> findPaginadoActivos(
            @Param("tenantId") String tenantId,
            @Param("clienteId") Long clienteId,
            @Param("s") String search,
            Pageable pageable);

    @Query("""
            SELECT e.id AS id, e.cliente.id AS clienteId, e.cliente.nombre AS clienteNombre,
                   e.nombre AS nombre, e.tipo AS tipo, e.marca AS marca, e.modelo AS modelo,
                   e.numeroSerie AS numeroSerie, e.ubicacion AS ubicacion, e.activo AS activo
            FROM EquipoCliente e
            WHERE e.tenantId = :tenantId
              AND e.activo = false
              AND (:clienteId IS NULL OR e.cliente.id = :clienteId)
              AND (:s = '' OR LOWER(e.nombre) LIKE LOWER(CONCAT('%', :s, '%'))
                           OR LOWER(e.marca)  LIKE LOWER(CONCAT('%', :s, '%'))
                           OR LOWER(e.modelo) LIKE LOWER(CONCAT('%', :s, '%'))
                           OR LOWER(COALESCE(e.numeroSerie, '')) LIKE LOWER(CONCAT('%', :s, '%')))
            ORDER BY e.nombre ASC
            """)
    Page<EquipoResumenDTO> findPaginadoInactivos(
            @Param("tenantId") String tenantId,
            @Param("clienteId") Long clienteId,
            @Param("s") String search,
            Pageable pageable);

    // ── Count queries for stats ───────────────────────────────────────────────

    @Query("SELECT COUNT(e) FROM EquipoCliente e WHERE e.tenantId = :tenantId")
    long countTotal(@Param("tenantId") String tenantId);

    @Query("SELECT COUNT(e) FROM EquipoCliente e WHERE e.tenantId = :tenantId AND e.activo = true")
    long countActivos(@Param("tenantId") String tenantId);

    @Query("SELECT COUNT(e) FROM EquipoCliente e WHERE e.tenantId = :tenantId AND e.activo = false")
    long countInactivos(@Param("tenantId") String tenantId);

    @Query("SELECT COUNT(DISTINCT e.cliente.id) FROM EquipoCliente e WHERE e.tenantId = :tenantId")
    long countClientesConEquipos(@Param("tenantId") String tenantId);
}
