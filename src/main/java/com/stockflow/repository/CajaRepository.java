package com.stockflow.repository;

import com.stockflow.entity.Caja;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;
import java.util.List;
import java.util.Optional;

@Repository
public interface CajaRepository extends JpaRepository<Caja, Long> {
    Optional<Caja> findByIdAndTenantId(Long id, String tenantId);
    List<Caja> findByTenantIdOrderByFechaAperturaDesc(String tenantId);
    Optional<Caja> findByTenantIdAndUsuarioIdAndEstado(String tenantId, Long usuarioId, String estado);
    Optional<Caja> findFirstByTenantIdAndEstadoOrderByFechaAperturaDesc(String tenantId, String estado);
    Optional<Caja> findFirstByTenantIdAndSucursalIdAndEstadoOrderByFechaAperturaDesc(String tenantId, Long sucursalId, String estado);
    List<Caja> findByTenantIdAndSucursalIdOrderByFechaAperturaDesc(String tenantId, Long sucursalId);

    long countByTenantId(String tenantId);

    @Modifying
    @Query(value = "UPDATE cajas SET sucursal_id = :sucursalId WHERE tenant_id = :tenantId AND sucursal_id IS NULL", nativeQuery = true)
    void asignarSucursalDondeEsNulo(@Param("sucursalId") Long sucursalId, @Param("tenantId") String tenantId);
}
