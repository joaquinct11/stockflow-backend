package com.stockflow.repository;

import com.stockflow.entity.ComprobanteSerie;
import jakarta.persistence.LockModeType;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.Optional;

@Repository
public interface ComprobanteSerieRepository extends JpaRepository<ComprobanteSerie, Long> {

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("SELECT cs FROM ComprobanteSerie cs WHERE cs.tenantId = :tenantId AND cs.tipo = :tipo AND cs.serie = :serie")
    Optional<ComprobanteSerie> findByTenantIdAndTipoAndSerieForUpdate(
            @Param("tenantId") String tenantId,
            @Param("tipo") String tipo,
            @Param("serie") String serie
    );

    Optional<ComprobanteSerie> findByTenantIdAndTipoAndSerie(String tenantId, String tipo, String serie);
}
