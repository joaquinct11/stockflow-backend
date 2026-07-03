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
}
