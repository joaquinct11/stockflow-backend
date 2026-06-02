package com.stockflow.repository;

import com.stockflow.entity.DevolucionDetalle;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public interface DevolucionDetalleRepository extends JpaRepository<DevolucionDetalle, Long> {

    List<DevolucionDetalle> findByDevolucionId(Long devolucionId);
}
