package com.stockflow.repository;

import com.stockflow.entity.OtConsumoClasificacion;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.Collection;
import java.util.List;

@Repository
public interface OtConsumoClasificacionRepository extends JpaRepository<OtConsumoClasificacion, Long> {

    List<OtConsumoClasificacion> findByConsumoIdOrderByCreatedAtAsc(Long consumoId);

    List<OtConsumoClasificacion> findByConsumoIdInOrderByCreatedAtAsc(Collection<Long> consumoIds);

    @Query("SELECT COALESCE(SUM(cl.cantidad), 0) FROM OtConsumoClasificacion cl WHERE cl.consumoId = :consumoId AND cl.tenantId = :tenantId")
    Integer sumCantidadClasificadaByConsumoId(@Param("consumoId") Long consumoId, @Param("tenantId") String tenantId);

    @Query("SELECT cl.consumoId, COALESCE(SUM(cl.cantidad), 0) FROM OtConsumoClasificacion cl WHERE cl.consumoId IN :consumoIds GROUP BY cl.consumoId")
    List<Object[]> sumCantidadClasificadaGroupByConsumoId(@Param("consumoIds") Collection<Long> consumoIds);
}
