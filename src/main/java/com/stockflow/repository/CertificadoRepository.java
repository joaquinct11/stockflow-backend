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

    List<CertificadoEstablecimiento> findByTenantIdAndSucursalIdAndActivoTrueOrderByFechaVencimientoAsc(String tenantId, Long sucursalId);

    Optional<CertificadoEstablecimiento> findByIdAndTenantId(Long id, String tenantId);

    @org.springframework.data.jpa.repository.Modifying
    @org.springframework.data.jpa.repository.Query(value = "UPDATE certificados_establecimiento SET sucursal_id = :sucursalId WHERE tenant_id = :tenantId AND sucursal_id IS NULL", nativeQuery = true)
    void asignarSucursalDondeEsNulo(@org.springframework.data.repository.query.Param("sucursalId") Long sucursalId, @org.springframework.data.repository.query.Param("tenantId") String tenantId);

    /**
     * Todos los certificados activos cuya fecha de vencimiento <= hoy + diasAlerta
     * (respeta el valor individual de diasAlerta de cada certificado).
     */
    @Query(value = """
        SELECT * FROM certificados_establecimiento
        WHERE activo = true
          AND fecha_vencimiento <= CURRENT_DATE + (dias_alerta * INTERVAL '1 day')
    """, nativeQuery = true)
    List<CertificadoEstablecimiento> findCertificadosEnAlertas();

    /** Cuenta los certificados activos vencidos o por vencer (para el badge del sidebar) */
    @Query("""
        SELECT COUNT(c) FROM CertificadoEstablecimiento c
        WHERE c.tenantId = :tenantId
          AND c.activo = true
          AND c.fechaVencimiento <= :fechaAlerta
    """)
    long countAlertasActivas(@Param("tenantId") String tenantId, @Param("fechaAlerta") LocalDate fechaAlerta);
}
