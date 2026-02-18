package com.stockflow.repository;

import com.stockflow.entity.Suscripcion;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

@Repository
public interface SuscripcionRepository extends JpaRepository<Suscripcion, Long> {
    Optional<Suscripcion> findByUsuarioPrincipalId(Long usuarioPrincipalId);
    List<Suscripcion> findByEstado(String estado);
    List<Suscripcion> findByEstadoAndFechaProximoCobro(String estado, LocalDateTime fecha);

    @Query("SELECT s FROM Suscripcion s WHERE s.estado = 'ACTIVA' AND s.fechaProximoCobro <= :ahora")
    List<Suscripcion> findSuscripcionesParaCobrar(@Param("ahora") LocalDateTime ahora);
}