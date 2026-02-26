package com.stockflow.repository;

import com.stockflow.entity.Proveedor;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;
import java.util.List;
import java.util.Optional;

@Repository
public interface ProveedorRepository extends JpaRepository<Proveedor, Long> {

    Optional<Proveedor> findByRuc(String ruc);

    List<Proveedor> findByNombreContainingIgnoreCase(String nombre);

    List<Proveedor> findByActivoTrue();

    // ✅ NUEVOS: Filtrar por tenant
    List<Proveedor> findByTenantId(String tenantId);

    List<Proveedor> findByTenantIdAndActivoTrue(String tenantId);  // ✅ ESTE ES EL QUE FALTA

    long countByTenantId(String tenantId);
}