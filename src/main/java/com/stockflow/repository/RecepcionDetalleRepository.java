package com.stockflow.repository;

import com.stockflow.entity.RecepcionDetalle;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public interface RecepcionDetalleRepository extends JpaRepository<RecepcionDetalle, Long> {

    List<RecepcionDetalle> findByRecepcionId(Long recepcionId);

    Optional<RecepcionDetalle> findByRecepcionIdAndProductoId(Long recepcionId, Long productoId);
}
