package com.stockflow.repository;

import com.stockflow.entity.CatalogoDigemid;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;
import java.util.Optional;

public interface CatalogoDigemidRepository extends JpaRepository<CatalogoDigemid, Long> {

    Optional<CatalogoDigemid> findByNumRegSan(String numRegSan);

    Optional<CatalogoDigemid> findByCodProd(String codProd);

    @Query("""
        SELECT c FROM CatalogoDigemid c
        WHERE LOWER(c.nomProd) LIKE LOWER(CONCAT('%', :q, '%'))
           OR LOWER(c.numRegSan) LIKE LOWER(CONCAT('%', :q, '%'))
           OR LOWER(c.nomIfa) LIKE LOWER(CONCAT('%', :q, '%'))
        ORDER BY c.nomProd
        """)
    List<CatalogoDigemid> buscar(@Param("q") String q, Pageable pageable);

    long count();
}
