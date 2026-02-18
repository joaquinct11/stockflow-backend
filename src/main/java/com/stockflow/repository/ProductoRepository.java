package com.stockflow.repository;

import com.stockflow.entity.Producto;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;
import java.util.List;
import java.util.Optional;

@Repository
public interface ProductoRepository extends JpaRepository<Producto, Long> {
    Optional<Producto> findByCodigoBarras(String codigoBarras);
    List<Producto> findByNombreContainingIgnoreCase(String nombre);
    List<Producto> findByTenantId(String tenantId);
    List<Producto> findByActivoTrue();

    @Query("SELECT p FROM Producto p WHERE p.stockActual < p.stockMinimo AND p.tenantId = :tenantId")
    List<Producto> findProductosBajoStock(@Param("tenantId") String tenantId);
}