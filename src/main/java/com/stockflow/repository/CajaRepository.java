package com.stockflow.repository;

import com.stockflow.entity.Caja;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;
import java.util.List;
import java.util.Optional;

@Repository
public interface CajaRepository extends JpaRepository<Caja, Long> {
    Optional<Caja> findByIdAndTenantId(Long id, String tenantId);
    List<Caja> findByTenantIdOrderByFechaAperturaDesc(String tenantId);
    Optional<Caja> findByTenantIdAndUsuarioIdAndEstado(String tenantId, Long usuarioId, String estado);
    Optional<Caja> findFirstByTenantIdAndEstadoOrderByFechaAperturaDesc(String tenantId, String estado);

    long countByTenantId(String tenantId);
}
