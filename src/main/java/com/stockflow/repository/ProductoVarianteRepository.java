package com.stockflow.repository;

import com.stockflow.entity.ProductoVariante;
import org.springframework.data.jpa.repository.JpaRepository;
import java.util.List;
import java.util.Optional;

public interface ProductoVarianteRepository extends JpaRepository<ProductoVariante, Long> {

    List<ProductoVariante> findByProductoIdAndTenantId(Long productoId, String tenantId);

    List<ProductoVariante> findByProductoIdAndActivoTrueAndTenantId(Long productoId, String tenantId);

    Optional<ProductoVariante> findByIdAndTenantId(Long id, String tenantId);

    boolean existsByProductoIdAndTenantId(Long productoId, String tenantId);
}
