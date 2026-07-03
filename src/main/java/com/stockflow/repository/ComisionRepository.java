package com.stockflow.repository;

import com.stockflow.entity.Comision;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import java.util.Optional;

@Repository
public interface ComisionRepository extends JpaRepository<Comision, Long> {

    List<Comision> findByTenantIdAndDeletedAtIsNullOrderByFechaDesc(String tenantId);

    @Query(value = "SELECT * FROM comisiones WHERE tenant_id = :tenantId AND sucursal_id = :sucursalId AND deleted_at IS NULL ORDER BY fecha DESC", nativeQuery = true)
    List<Comision> findByTenantIdAndSucursalIdAndDeletedAtIsNull(@Param("tenantId") String tenantId, @Param("sucursalId") Long sucursalId);

    Optional<Comision> findByIdAndTenantId(Long id, String tenantId);

    @Query("SELECT c FROM Comision c WHERE c.tenantId = :tenantId AND c.deletedAt IS NULL " +
           "AND c.fecha BETWEEN :inicio AND :fin ORDER BY c.fecha DESC")
    List<Comision> findByTenantIdAndFechaBetween(
            @Param("tenantId") String tenantId,
            @Param("inicio") LocalDate inicio,
            @Param("fin") LocalDate fin);

    @Query("SELECT COALESCE(SUM(c.monto), 0) FROM Comision c WHERE c.tenantId = :tenantId " +
           "AND c.deletedAt IS NULL AND c.fecha BETWEEN :inicio AND :fin")
    BigDecimal sumMontoByTenantIdAndFechaBetween(
            @Param("tenantId") String tenantId,
            @Param("inicio") LocalDate inicio,
            @Param("fin") LocalDate fin);
}
