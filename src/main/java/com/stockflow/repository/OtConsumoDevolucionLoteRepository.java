package com.stockflow.repository;

import com.stockflow.entity.OtConsumoDevolucionLote;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.Collection;
import java.util.List;

@Repository
public interface OtConsumoDevolucionLoteRepository extends JpaRepository<OtConsumoDevolucionLote, Long> {

    List<OtConsumoDevolucionLote> findByDevolucionIdOrderByCreatedAtAsc(Long devolucionId);

    List<OtConsumoDevolucionLote> findByDevolucionIdInOrderByCreatedAtAsc(Collection<Long> devolucionIds);
}
