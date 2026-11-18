package com.stockflow.repository;

import com.stockflow.entity.gre.GuiaRemisionEvento;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface GuiaRemisionEventoRepository extends JpaRepository<GuiaRemisionEvento, Long> {
    List<GuiaRemisionEvento> findByGuiaRemisionIdOrderByCreatedAtAsc(Long guiaRemisionId);
}
