package com.stockflow.repository;

import com.stockflow.entity.UnidadMedida;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;
import java.util.Optional;

public interface UnidadMedidaRepository extends JpaRepository<UnidadMedida, Long> {

    /**
     * Busca unidad por nombre (case-insensitive) dentro del tenant
     * O en unidades globales (tenantId IS NULL).
     */
    @Query("SELECT u FROM UnidadMedida u WHERE LOWER(u.nombre) = LOWER(:nombre) " +
           "AND (u.tenantId = :tenantId OR u.tenantId IS NULL) " +
           "ORDER BY CASE WHEN u.tenantId = :tenantId THEN 0 ELSE 1 END")
    List<UnidadMedida> findByNombreAndTenantIdOrGlobal(
            @Param("nombre") String nombre,
            @Param("tenantId") String tenantId);

    /** Primera unidad del tenant, o global si no hay específica. */
    @Query("SELECT u FROM UnidadMedida u WHERE u.tenantId = :tenantId OR u.tenantId IS NULL ORDER BY u.id ASC")
    List<UnidadMedida> findAllByTenantIdOrGlobal(@Param("tenantId") String tenantId);

    default Optional<UnidadMedida> findFirstByTenantIdOrGlobal(String tenantId) {
        return findAllByTenantIdOrGlobal(tenantId).stream().findFirst();
    }

    List<UnidadMedida> findByTenantId(String tenantId);
}