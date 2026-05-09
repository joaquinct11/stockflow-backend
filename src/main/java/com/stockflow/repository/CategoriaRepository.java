package com.stockflow.repository;

import com.stockflow.entity.Categoria;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public interface CategoriaRepository extends JpaRepository<Categoria, Long> {

    @Query("SELECT c FROM Categoria c WHERE (c.tenantId = :tenantId OR c.tenantId IS NULL) AND c.activo = true ORDER BY c.tenantId DESC NULLS LAST, c.nombre ASC")
    List<Categoria> findByTenantIdOrGlobal(@Param("tenantId") String tenantId);
}
