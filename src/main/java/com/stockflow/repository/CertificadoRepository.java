package com.stockflow.repository;

import com.stockflow.entity.CertificadoEstablecimiento;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.time.LocalDate;
import java.util.List;
import java.util.Optional;

@Repository
public interface CertificadoRepository extends JpaRepository<CertificadoEstablecimiento, Long> {

    List<CertificadoEstablecimiento> findByTenantIdAndActivoTrueOrderByFechaVencimientoAsc(String tenantId);

    Optional<CertificadoEstablecimiento> findByIdAndTenantId(Long id, String tenantId);

    /** Cuenta los certificados activos vencidos o por vencer (para el badge del sidebar) */
    @Query("""
        SELECT COUNT(c) FROM CertificadoEstablecimiento c
        WHERE c.tenantId = :tenantId
          AND c.activo = true
          AND c.fechaVencimiento <= :fechaAlerta
    """)
    long countAlertasActivas(@Param("tenantId") String tenantId, @Param("fechaAlerta") LocalDate fechaAlerta);
}
