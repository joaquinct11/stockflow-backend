package com.stockflow.repository;

import com.stockflow.entity.NotaCredito;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public interface NotaCreditoRepository extends JpaRepository<NotaCredito, Long> {

    Optional<NotaCredito> findByCodigoAndTenantId(String codigo, String tenantId);

    List<NotaCredito> findByTenantIdOrderByFechaEmisionDesc(String tenantId);

    @Query(value = "SELECT * FROM notas_credito WHERE tenant_id = :tenantId AND sucursal_id = :sucursalId ORDER BY fecha_emision DESC", nativeQuery = true)
    List<NotaCredito> findByTenantIdAndSucursalId(@Param("tenantId") String tenantId, @Param("sucursalId") Long sucursalId);

    Optional<NotaCredito> findByIdAndTenantId(Long id, String tenantId);

    @Query("SELECT COALESCE(MAX(CAST(SUBSTRING(n.codigo, 9) AS int)), 0) FROM NotaCredito n " +
           "WHERE n.tenantId = :tenantId AND n.codigo LIKE CONCAT('NC-', :year, '-%')")
    int findMaxSecuenciaAnio(@Param("tenantId") String tenantId, @Param("year") String year);

    @Modifying
    @Query(value = "UPDATE notas_credito SET sucursal_id = :sucursalId WHERE tenant_id = :tenantId AND sucursal_id IS NULL", nativeQuery = true)
    void asignarSucursalDondeEsNulo(@Param("sucursalId") Long sucursalId, @Param("tenantId") String tenantId);
}
