package com.stockflow.repository;

import com.stockflow.entity.DetalleVenta;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;
import java.util.List;
import java.util.Optional;

@Repository
public interface DetalleVentaRepository extends JpaRepository<DetalleVenta, Long> {
    List<DetalleVenta> findByVentaId(Long ventaId);
    List<DetalleVenta> findByProductoId(Long productoId);

    @Query("SELECT d FROM DetalleVenta d JOIN d.venta v WHERE v.id = :ventaId AND d.producto.id = :productoId AND v.tenantId = :tenantId")
    Optional<DetalleVenta> findByVentaIdAndProductoIdAndTenantId(
            @Param("ventaId") Long ventaId,
            @Param("productoId") Long productoId,
            @Param("tenantId") String tenantId);
}