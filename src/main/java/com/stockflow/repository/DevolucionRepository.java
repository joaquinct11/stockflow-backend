package com.stockflow.repository;

import com.stockflow.entity.Devolucion;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public interface DevolucionRepository extends JpaRepository<Devolucion, Long> {

    List<Devolucion> findByTenantIdOrderByFechaDevolucionDesc(String tenantId);

    List<Devolucion> findByVentaIdAndTenantId(Long ventaId, String tenantId);

    Optional<Devolucion> findByIdAndTenantId(Long id, String tenantId);
}
