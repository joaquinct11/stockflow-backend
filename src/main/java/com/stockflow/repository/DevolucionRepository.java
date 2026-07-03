package com.stockflow.repository;

import com.stockflow.entity.Devolucion;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public interface DevolucionRepository extends JpaRepository<Devolucion, Long> {

    List<Devolucion> findByTenantIdOrderByFechaDevolucionDesc(String tenantId);

    List<Devolucion> findByVentaIdAndTenantId(Long ventaId, String tenantId);

    Optional<Devolucion> findByIdAndTenantId(Long id, String tenantId);

    @Modifying
    @Query(value = "UPDATE devoluciones SET sucursal_id = :sucursalId WHERE tenant_id = :tenantId AND sucursal_id IS NULL", nativeQuery = true)
    void asignarSucursalDondeEsNulo(@Param("sucursalId") Long sucursalId, @Param("tenantId") String tenantId);
}
