package com.stockflow.repository;

import com.stockflow.entity.OtConsumoDevolucion;
import jakarta.persistence.LockModeType;
import jakarta.persistence.QueryHint;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.jpa.repository.QueryHints;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.Collection;
import java.util.List;
import java.util.Optional;

@Repository
public interface OtConsumoDevolucionRepository extends JpaRepository<OtConsumoDevolucion, Long> {

    List<OtConsumoDevolucion> findByConsumoIdOrderByCreatedAtAsc(Long consumoId);

    List<OtConsumoDevolucion> findByConsumoIdInOrderByCreatedAtAsc(Collection<Long> consumoIds);

    @Query("SELECT COALESCE(SUM(d.cantidadBase), 0) FROM OtConsumoDevolucion d WHERE d.consumoId = :consumoId AND d.tenantId = :tenantId")
    Integer sumCantidadDevueltaByConsumoId(@Param("consumoId") Long consumoId, @Param("tenantId") String tenantId);

    @Query("SELECT d.consumoId, COALESCE(SUM(d.cantidadBase), 0) FROM OtConsumoDevolucion d WHERE d.consumoId IN :consumoIds GROUP BY d.consumoId")
    List<Object[]> sumCantidadDevueltaGroupByConsumoId(@Param("consumoIds") Collection<Long> consumoIds);
}
