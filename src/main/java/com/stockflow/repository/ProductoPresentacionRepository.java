package com.stockflow.repository;

import com.stockflow.entity.ProductoPresentacion;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.Collection;
import java.util.List;
import java.util.Optional;

@Repository
public interface ProductoPresentacionRepository extends JpaRepository<ProductoPresentacion, Long> {

    List<ProductoPresentacion> findByProductoIdAndTenantId(Long productoId, String tenantId);

    List<ProductoPresentacion> findByProductoId(Long productoId);

    Optional<ProductoPresentacion> findByProductoIdAndEsPrincipalTrueAndTenantId(Long productoId, String tenantId);

    boolean existsByProductoIdAndUnidadMedidaId(Long productoId, Long unidadMedidaId);

    void deleteByProductoId(Long productoId);

    @Query("SELECT p FROM ProductoPresentacion p LEFT JOIN FETCH p.unidadMedida WHERE p.id IN :ids")
    List<ProductoPresentacion> findByIdInWithUnidadMedida(@Param("ids") Collection<Long> ids);
}
