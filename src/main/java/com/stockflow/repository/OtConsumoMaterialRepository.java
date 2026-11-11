package com.stockflow.repository;

import com.stockflow.entity.OtConsumoMaterial;
import jakarta.persistence.LockModeType;
import jakarta.persistence.QueryHint;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.jpa.repository.QueryHints;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public interface OtConsumoMaterialRepository extends JpaRepository<OtConsumoMaterial, Long> {

    List<OtConsumoMaterial> findByOrdenTrabajo_IdAndTenantIdOrderByCreatedAtAsc(Long otId, String tenantId);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @QueryHints(@QueryHint(name = "jakarta.persistence.lock.timeout", value = "3000"))
    @Query("SELECT c FROM OtConsumoMaterial c WHERE c.id = :id AND c.tenantId = :tenantId")
    Optional<OtConsumoMaterial> findByIdAndTenantIdForUpdate(@Param("id") Long id, @Param("tenantId") String tenantId);
}
