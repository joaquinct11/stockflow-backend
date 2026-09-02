package com.stockflow.repository;

import com.stockflow.entity.CatalogoDigemid;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;
import java.util.Optional;

public interface CatalogoDigemidRepository extends JpaRepository<CatalogoDigemid, Long> {

    Optional<CatalogoDigemid> findByCodProd(String codProd);

    @Query(value = """
        SELECT * FROM catalogo_digemid
        WHERE nom_prod    ILIKE '%' || :q || '%'
           OR num_reg_san ILIKE '%' || :q || '%'
           OR nom_ifa     ILIKE '%' || :q || '%'
        ORDER BY
            CASE WHEN nom_prod ILIKE '%' || :q || '%' THEN 0 ELSE 1 END,
            nom_prod
        LIMIT 30
        """, nativeQuery = true)
    List<CatalogoDigemid> buscar(@Param("q") String q);

    long count();
}
