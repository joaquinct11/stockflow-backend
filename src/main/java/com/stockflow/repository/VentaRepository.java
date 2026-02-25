package com.stockflow.repository;

import com.stockflow.entity.Venta;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;
import java.time.LocalDateTime;
import java.util.List;

@Repository
public interface VentaRepository extends JpaRepository<Venta, Long> {
    List<Venta> findByVendedorId(Long vendedorId);
    List<Venta> findByTenantId(String tenantId);
    List<Venta> findByCreatedAtBetween(LocalDateTime fechaInicio, LocalDateTime fechaFin);
    long countByTenantId(String tenantId);
    @Query("SELECT v FROM Venta v WHERE v.tenantId = :tenantId AND v.createdAt BETWEEN :inicio AND :fin")
    List<Venta> findVentasPorPeriodo(
            @Param("tenantId") String tenantId,
            @Param("inicio") LocalDateTime inicio,
            @Param("fin") LocalDateTime fin
    );
}