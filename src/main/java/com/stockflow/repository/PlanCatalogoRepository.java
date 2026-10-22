package com.stockflow.repository;

import com.stockflow.entity.PlanCatalogo;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public interface PlanCatalogoRepository extends JpaRepository<PlanCatalogo, String> {

    Optional<PlanCatalogo> findByCodigoAndActivoTrue(String codigo);

    List<PlanCatalogo> findBySegmentoAndActivoTrueOrderByNivelAsc(String segmento);

    List<PlanCatalogo> findByRubroOperativoAndActivoTrueOrderByNivelAsc(String rubroOperativo);

    Optional<PlanCatalogo> findByRubroOperativoAndNivelAndActivoTrue(String rubroOperativo, String nivel);

    List<PlanCatalogo> findAllByActivoTrue();

    List<PlanCatalogo> findAllByPrecioMensualAndActivoTrue(java.math.BigDecimal precioMensual);
}
