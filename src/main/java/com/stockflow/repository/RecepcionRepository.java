package com.stockflow.repository;

import com.stockflow.entity.Recepcion;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public interface RecepcionRepository extends JpaRepository<Recepcion, Long> {

    List<Recepcion> findByTenantId(String tenantId);

    List<Recepcion> findByTenantIdAndEstado(String tenantId, String estado);

    List<Recepcion> findByOrdenCompraId(Long ocId);
}
