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

    @Query("SELECT p FROM ProductoPresentacion p LEFT JOIN FETCH p.unidadMedida WHERE p.producto.id = :productoId AND p.tenantId = :tenantId")
    List<ProductoPresentacion> findByProductoIdAndTenantId(@Param("productoId") Long productoId, @Param("tenantId") String tenantId);

    List<ProductoPresentacion> findByProductoId(Long productoId);

    @Query("SELECT p FROM ProductoPresentacion p LEFT JOIN FETCH p.unidadMedida WHERE p.producto.id = :productoId AND p.esPrincipal = true AND p.tenantId = :tenantId")
    Optional<ProductoPresentacion> findByProductoIdAndEsPrincipalTrueAndTenantId(@Param("productoId") Long productoId, @Param("tenantId") String tenantId);

    boolean existsByProductoIdAndUnidadMedidaId(Long productoId, Long unidadMedidaId);

    void deleteByProductoId(Long productoId);

    @Query("SELECT p FROM ProductoPresentacion p LEFT JOIN FETCH p.unidadMedida WHERE p.id IN :ids")
    List<ProductoPresentacion> findByIdInWithUnidadMedida(@Param("ids") Collection<Long> ids);
}
