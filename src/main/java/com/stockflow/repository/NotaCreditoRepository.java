package com.stockflow.repository;

import com.stockflow.entity.NotaCredito;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public interface NotaCreditoRepository extends JpaRepository<NotaCredito, Long> {

    Optional<NotaCredito> findByCodigoAndTenantId(String codigo, String tenantId);

    List<NotaCredito> findByTenantIdOrderByFechaEmisionDesc(String tenantId);

    Optional<NotaCredito> findByIdAndTenantId(Long id, String tenantId);

    @Query("SELECT COALESCE(MAX(CAST(SUBSTRING(n.codigo, 9) AS int)), 0) FROM NotaCredito n " +
           "WHERE n.tenantId = :tenantId AND n.codigo LIKE CONCAT('NC-', :year, '-%')")
    int findMaxSecuenciaAnio(@Param("tenantId") String tenantId, @Param("year") String year);
}
