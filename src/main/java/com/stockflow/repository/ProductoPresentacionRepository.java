package com.stockflow.repository;

import com.stockflow.entity.ProductoPresentacion;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public interface ProductoPresentacionRepository extends JpaRepository<ProductoPresentacion, Long> {

    List<ProductoPresentacion> findByProductoIdAndTenantId(Long productoId, String tenantId);

    List<ProductoPresentacion> findByProductoId(Long productoId);

    Optional<ProductoPresentacion> findByProductoIdAndEsPrincipalTrueAndTenantId(Long productoId, String tenantId);

    boolean existsByProductoIdAndUnidadMedidaId(Long productoId, Long unidadMedidaId);

    void deleteByProductoId(Long productoId);
}
