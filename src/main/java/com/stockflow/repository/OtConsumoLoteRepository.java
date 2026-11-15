package com.stockflow.repository;

import com.stockflow.entity.OtConsumoLote;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.Collection;
import java.util.List;

@Repository
public interface OtConsumoLoteRepository extends JpaRepository<OtConsumoLote, Long> {

    List<OtConsumoLote> findByConsumoIdOrderByCreatedAtAsc(Long consumoId);

    List<OtConsumoLote> findByConsumoIdInOrderByCreatedAtAsc(Collection<Long> consumoIds);
}
