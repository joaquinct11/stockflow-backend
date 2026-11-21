package com.stockflow.repository;

import com.stockflow.entity.Tenant;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.stereotype.Repository;
import java.util.List;
import java.util.Optional;

@Repository
public interface TenantRepository extends JpaRepository<Tenant, Long> {
    Optional<Tenant> findByTenantId(String tenantId);
    boolean existsByTenantId(String tenantId);

    /** Solo los tenantId de tenants activos — para schedulers que no necesitan la entidad completa. */
    @Query("SELECT t.tenantId FROM Tenant t WHERE t.activo = true")
    List<String> findAllActiveTenantIds();
}