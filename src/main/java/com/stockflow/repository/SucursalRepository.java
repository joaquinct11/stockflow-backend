package com.stockflow.repository;

import com.stockflow.entity.Sucursal;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

public interface SucursalRepository extends JpaRepository<Sucursal, Long> {

    List<Sucursal> findByTenantIdAndActivoTrueOrderByEsPrincipalDescNombreAsc(String tenantId);

    Optional<Sucursal> findByIdAndTenantId(Long id, String tenantId);

    Optional<Sucursal> findByTenantIdAndEsPrincipalTrue(String tenantId);

    long countByTenantIdAndActivoTrue(String tenantId);

    /** Devuelve sucursales activas + bloqueadas por plan (para mostrar en UI con estado). */
    List<Sucursal> findByTenantIdAndActivoTrueOrTenantIdAndBloqueadaPorPlanTrueOrderByEsPrincipalDescNombreAsc(
            String tenantId1, String tenantId2);

    /** Sucursales bloqueadas por downgrade — se reactivan al volver a PRO. */
    List<Sucursal> findByTenantIdAndBloqueadaPorPlanTrue(String tenantId);

    /** Sucursales adicionales activas (no principal) — para bloquear en downgrade. */
    List<Sucursal> findByTenantIdAndEsPrincipalFalseAndActivoTrue(String tenantId);
}
