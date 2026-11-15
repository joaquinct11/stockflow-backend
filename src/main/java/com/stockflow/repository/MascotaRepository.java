package com.stockflow.repository;

import com.stockflow.dto.MascotaResumenDTO;
import com.stockflow.entity.Mascota;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public interface MascotaRepository extends JpaRepository<Mascota, Long> {

    Optional<Mascota> findByIdAndTenantId(Long id, String tenantId);

    List<Mascota> findByTenantIdAndActivoTrue(String tenantId);

    List<Mascota> findByCliente_IdAndTenantIdAndActivoTrue(Long clienteId, String tenantId);

    List<Mascota> findByNombreContainingIgnoreCaseAndTenantIdAndActivoTrue(String nombre, String tenantId);

    boolean existsByMicrochipAndTenantId(String microchip, String tenantId);

    long countByCliente_IdAndTenantId(Long clienteId, String tenantId);

    /** Solo mascotas activas del tenant — sin N+1. */
    @Query("""
           SELECT m.id             AS id,
                  m.cliente.id     AS clienteId,
                  m.cliente.nombre AS clienteNombre,
                  m.nombre         AS nombre,
                  m.especie        AS especie,
                  m.raza           AS raza,
                  m.sexo           AS sexo,
                  m.pesoKg         AS pesoKg,
                  m.activo         AS activo
           FROM Mascota m
           WHERE m.tenantId = :tenantId
             AND m.activo = true
           ORDER BY m.nombre
           """)
    List<MascotaResumenDTO> findResumenByTenantId(@Param("tenantId") String tenantId);

    /** Todas las mascotas del tenant (activas e inactivas). */
    @Query("""
           SELECT m.id             AS id,
                  m.cliente.id     AS clienteId,
                  m.cliente.nombre AS clienteNombre,
                  m.nombre         AS nombre,
                  m.especie        AS especie,
                  m.raza           AS raza,
                  m.sexo           AS sexo,
                  m.pesoKg         AS pesoKg,
                  m.activo         AS activo
           FROM Mascota m
           WHERE m.tenantId = :tenantId
           ORDER BY m.nombre
           """)
    List<MascotaResumenDTO> findResumenAllByTenantId(@Param("tenantId") String tenantId);

    /** Solo mascotas inactivas del tenant. */
    @Query("""
           SELECT m.id             AS id,
                  m.cliente.id     AS clienteId,
                  m.cliente.nombre AS clienteNombre,
                  m.nombre         AS nombre,
                  m.especie        AS especie,
                  m.raza           AS raza,
                  m.sexo           AS sexo,
                  m.pesoKg         AS pesoKg,
                  m.activo         AS activo
           FROM Mascota m
           WHERE m.tenantId = :tenantId
             AND m.activo = false
           ORDER BY m.nombre
           """)
    List<MascotaResumenDTO> findResumenInactivasByTenantId(@Param("tenantId") String tenantId);

    /** Resumen por cliente — incluye activas e inactivas (para detalle de cliente). */
    @Query("""
           SELECT m.id             AS id,
                  m.cliente.id     AS clienteId,
                  m.cliente.nombre AS clienteNombre,
                  m.nombre         AS nombre,
                  m.especie        AS especie,
                  m.raza           AS raza,
                  m.sexo           AS sexo,
                  m.pesoKg         AS pesoKg,
                  m.activo         AS activo
           FROM Mascota m
           WHERE m.tenantId = :tenantId
             AND m.cliente.id = :clienteId
           ORDER BY m.nombre
           """)
    List<MascotaResumenDTO> findResumenByClienteIdAndTenantId(
            @Param("clienteId") Long clienteId,
            @Param("tenantId")  String tenantId);

    // ── Paginadas con búsqueda ────────────────────────────────────────────────

    @Query(value = """
           SELECT m.id             AS id,
                  m.cliente.id     AS clienteId,
                  m.cliente.nombre AS clienteNombre,
                  m.nombre         AS nombre,
                  m.especie        AS especie,
                  m.raza           AS raza,
                  m.sexo           AS sexo,
                  m.pesoKg         AS pesoKg,
                  m.activo         AS activo
           FROM Mascota m
           WHERE m.tenantId = :tenantId
             AND m.activo = true
             AND (:s = '' OR LOWER(m.nombre)                   LIKE LOWER(CONCAT('%', :s, '%'))
                          OR LOWER(m.especie)                  LIKE LOWER(CONCAT('%', :s, '%'))
                          OR LOWER(COALESCE(m.raza, ''))       LIKE LOWER(CONCAT('%', :s, '%'))
                          OR LOWER(m.cliente.nombre)            LIKE LOWER(CONCAT('%', :s, '%')))
           """,
           countQuery = """
           SELECT COUNT(m) FROM Mascota m
           WHERE m.tenantId = :tenantId
             AND m.activo = true
             AND (:s = '' OR LOWER(m.nombre)                   LIKE LOWER(CONCAT('%', :s, '%'))
                          OR LOWER(m.especie)                  LIKE LOWER(CONCAT('%', :s, '%'))
                          OR LOWER(COALESCE(m.raza, ''))       LIKE LOWER(CONCAT('%', :s, '%'))
                          OR LOWER(m.cliente.nombre)            LIKE LOWER(CONCAT('%', :s, '%')))
           """)
    Page<MascotaResumenDTO> findPaginadoActivas(
            @Param("tenantId") String tenantId,
            @Param("s") String search,
            Pageable pageable);

    @Query(value = """
           SELECT m.id             AS id,
                  m.cliente.id     AS clienteId,
                  m.cliente.nombre AS clienteNombre,
                  m.nombre         AS nombre,
                  m.especie        AS especie,
                  m.raza           AS raza,
                  m.sexo           AS sexo,
                  m.pesoKg         AS pesoKg,
                  m.activo         AS activo
           FROM Mascota m
           WHERE m.tenantId = :tenantId
             AND m.activo = false
             AND (:s = '' OR LOWER(m.nombre)                   LIKE LOWER(CONCAT('%', :s, '%'))
                          OR LOWER(m.especie)                  LIKE LOWER(CONCAT('%', :s, '%'))
                          OR LOWER(COALESCE(m.raza, ''))       LIKE LOWER(CONCAT('%', :s, '%'))
                          OR LOWER(m.cliente.nombre)            LIKE LOWER(CONCAT('%', :s, '%')))
           """,
           countQuery = """
           SELECT COUNT(m) FROM Mascota m
           WHERE m.tenantId = :tenantId
             AND m.activo = false
             AND (:s = '' OR LOWER(m.nombre)                   LIKE LOWER(CONCAT('%', :s, '%'))
                          OR LOWER(m.especie)                  LIKE LOWER(CONCAT('%', :s, '%'))
                          OR LOWER(COALESCE(m.raza, ''))       LIKE LOWER(CONCAT('%', :s, '%'))
                          OR LOWER(m.cliente.nombre)            LIKE LOWER(CONCAT('%', :s, '%')))
           """)
    Page<MascotaResumenDTO> findPaginadoInactivas(
            @Param("tenantId") String tenantId,
            @Param("s") String search,
            Pageable pageable);

    @Query(value = """
           SELECT m.id             AS id,
                  m.cliente.id     AS clienteId,
                  m.cliente.nombre AS clienteNombre,
                  m.nombre         AS nombre,
                  m.especie        AS especie,
                  m.raza           AS raza,
                  m.sexo           AS sexo,
                  m.pesoKg         AS pesoKg,
                  m.activo         AS activo
           FROM Mascota m
           WHERE m.tenantId = :tenantId
             AND (:s = '' OR LOWER(m.nombre)                   LIKE LOWER(CONCAT('%', :s, '%'))
                          OR LOWER(m.especie)                  LIKE LOWER(CONCAT('%', :s, '%'))
                          OR LOWER(COALESCE(m.raza, ''))       LIKE LOWER(CONCAT('%', :s, '%'))
                          OR LOWER(m.cliente.nombre)            LIKE LOWER(CONCAT('%', :s, '%')))
           """,
           countQuery = """
           SELECT COUNT(m) FROM Mascota m
           WHERE m.tenantId = :tenantId
             AND (:s = '' OR LOWER(m.nombre)                   LIKE LOWER(CONCAT('%', :s, '%'))
                          OR LOWER(m.especie)                  LIKE LOWER(CONCAT('%', :s, '%'))
                          OR LOWER(COALESCE(m.raza, ''))       LIKE LOWER(CONCAT('%', :s, '%'))
                          OR LOWER(m.cliente.nombre)            LIKE LOWER(CONCAT('%', :s, '%')))
           """)
    Page<MascotaResumenDTO> findPaginadoTodos(
            @Param("tenantId") String tenantId,
            @Param("s") String search,
            Pageable pageable);

    // ── Conteos para resumen (independientes del estado filtrado) ────────────

    @Query("""
           SELECT COUNT(m) FROM Mascota m
           WHERE m.tenantId = :tenantId
             AND (:s = '' OR LOWER(m.nombre)                   LIKE LOWER(CONCAT('%', :s, '%'))
                          OR LOWER(m.especie)                  LIKE LOWER(CONCAT('%', :s, '%'))
                          OR LOWER(COALESCE(m.raza, ''))       LIKE LOWER(CONCAT('%', :s, '%'))
                          OR LOWER(m.cliente.nombre)            LIKE LOWER(CONCAT('%', :s, '%')))
           """)
    long countResumenTotal(@Param("tenantId") String tenantId, @Param("s") String search);

    @Query("""
           SELECT COUNT(m) FROM Mascota m
           WHERE m.tenantId = :tenantId
             AND m.activo = true
             AND (:s = '' OR LOWER(m.nombre)                   LIKE LOWER(CONCAT('%', :s, '%'))
                          OR LOWER(m.especie)                  LIKE LOWER(CONCAT('%', :s, '%'))
                          OR LOWER(COALESCE(m.raza, ''))       LIKE LOWER(CONCAT('%', :s, '%'))
                          OR LOWER(m.cliente.nombre)            LIKE LOWER(CONCAT('%', :s, '%')))
           """)
    long countResumenActivas(@Param("tenantId") String tenantId, @Param("s") String search);
}
