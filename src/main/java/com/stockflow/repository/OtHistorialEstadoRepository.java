package com.stockflow.repository;

import com.stockflow.entity.OtHistorialEstado;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface OtHistorialEstadoRepository extends JpaRepository<OtHistorialEstado, Long> {

    List<OtHistorialEstado> findByOrdenTrabajo_IdAndTenantIdOrderByCreatedAtAsc(
            Long ordenTrabajoId, String tenantId);
}
