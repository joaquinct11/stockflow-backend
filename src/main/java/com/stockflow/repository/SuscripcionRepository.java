package com.stockflow.repository;

import com.stockflow.entity.Suscripcion;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;
import java.util.List;
import java.util.Optional;

@Repository
public interface SuscripcionRepository extends JpaRepository<Suscripcion, Long> {

    Optional<Suscripcion> findByUsuarioPrincipalId(Long usuarioId);

    List<Suscripcion> findByEstado(String estado);

    // ✅ NUEVOS: Filtrar por tenant
    List<Suscripcion> findByTenantId(String tenantId);

    List<Suscripcion> findByEstadoAndTenantId(String estado, String tenantId);

    Optional<Suscripcion> findByTenantIdAndUsuarioPrincipalId(String tenantId, Long usuarioPrincipalId);

    Optional<Suscripcion> findFirstByMpPreferenceIdOrderByIdDesc(String mpPreferenceId);

    long countByTenantId(String tenantId);
}
