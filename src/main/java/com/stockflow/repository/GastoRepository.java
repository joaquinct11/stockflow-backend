package com.stockflow.repository;

import com.stockflow.entity.Gasto;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;

@Repository
public interface GastoRepository extends JpaRepository<Gasto, Long> {

    List<Gasto> findByTenantIdAndDeletedAtIsNullOrderByFechaGastoDesc(String tenantId);

    List<Gasto> findByTenantIdAndActivoTrueAndDeletedAtIsNullOrderByFechaGastoDesc(String tenantId);

    List<Gasto> findByTenantIdAndCategoriaAndDeletedAtIsNullOrderByFechaGastoDesc(String tenantId, String categoria);

    @Query("SELECT g FROM Gasto g WHERE g.tenantId = :tenantId AND g.deletedAt IS NULL " +
           "AND g.fechaGasto BETWEEN :inicio AND :fin ORDER BY g.fechaGasto DESC")
    List<Gasto> findByTenantIdAndFechaGastoBetween(
            @Param("tenantId") String tenantId,
            @Param("inicio") LocalDate inicio,
            @Param("fin") LocalDate fin);

    @Query("SELECT COALESCE(SUM(g.monto), 0) FROM Gasto g WHERE g.tenantId = :tenantId " +
           "AND g.deletedAt IS NULL AND g.fechaGasto BETWEEN :inicio AND :fin")
    BigDecimal sumMontoByTenantIdAndFechaBetween(
            @Param("tenantId") String tenantId,
            @Param("inicio") LocalDate inicio,
            @Param("fin") LocalDate fin);

    @Query("SELECT COALESCE(SUM(g.monto), 0) FROM Gasto g WHERE g.tenantId = :tenantId " +
           "AND g.deletedAt IS NULL AND g.categoria = :categoria " +
           "AND g.fechaGasto BETWEEN :inicio AND :fin")
    BigDecimal sumMontoByCategoria(
            @Param("tenantId") String tenantId,
            @Param("categoria") String categoria,
            @Param("inicio") LocalDate inicio,
            @Param("fin") LocalDate fin);

    long countByTenantIdAndDeletedAtIsNull(String tenantId);

    @Query("SELECT g FROM Gasto g WHERE g.tenantId = :tenantId " +
           "AND g.deletedAt IS NULL AND LOWER(g.concepto) LIKE LOWER(CONCAT('%', :q, '%')) " +
           "ORDER BY g.fechaGasto DESC")
    List<Gasto> searchByConcepto(@Param("tenantId") String tenantId, @Param("q") String q);
}
