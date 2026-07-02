package com.stockflow.repository;

import com.stockflow.entity.Gasto;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;

@Repository
public interface GastoRepository extends JpaRepository<Gasto, Long> {

    List<Gasto> findByTenantIdAndDeletedAtIsNullOrderByFechaGastoDesc(String tenantId);

    @Query(value = "SELECT * FROM gastos WHERE tenant_id = :tenantId AND sucursal_id = :sucursalId AND deleted_at IS NULL ORDER BY fecha_gasto DESC", nativeQuery = true)
    List<Gasto> findByTenantIdAndSucursalIdAndDeletedAtIsNull(@Param("tenantId") String tenantId, @Param("sucursalId") Long sucursalId);

    List<Gasto> findByTenantIdAndActivoTrueAndDeletedAtIsNullOrderByFechaGastoDesc(String tenantId);

    @Query(value = "SELECT * FROM gastos WHERE tenant_id = :tenantId AND sucursal_id = :sucursalId AND activo = true AND deleted_at IS NULL ORDER BY fecha_gasto DESC", nativeQuery = true)
    List<Gasto> findByTenantIdAndSucursalIdAndActivoTrueAndDeletedAtIsNull(@Param("tenantId") String tenantId, @Param("sucursalId") Long sucursalId);

    List<Gasto> findByTenantIdAndCategoriaAndDeletedAtIsNullOrderByFechaGastoDesc(String tenantId, String categoria);

    @Query("SELECT g FROM Gasto g WHERE g.tenantId = :tenantId AND g.deletedAt IS NULL " +
           "AND g.fechaGasto BETWEEN :inicio AND :fin ORDER BY g.fechaGasto DESC")
    List<Gasto> findByTenantIdAndFechaGastoBetween(
            @Param("tenantId") String tenantId,
            @Param("inicio") LocalDate inicio,
            @Param("fin") LocalDate fin);

    @Query("SELECT g FROM Gasto g WHERE g.tenantId = :tenantId AND g.sucursalId = :sucursalId AND g.deletedAt IS NULL " +
           "AND g.fechaGasto BETWEEN :inicio AND :fin ORDER BY g.fechaGasto DESC")
    List<Gasto> findByTenantIdAndSucursalIdAndFechaGastoBetween(
            @Param("tenantId") String tenantId,
            @Param("sucursalId") Long sucursalId,
            @Param("inicio") LocalDate inicio,
            @Param("fin") LocalDate fin);

    @Query("SELECT COALESCE(SUM(g.monto), 0) FROM Gasto g WHERE g.tenantId = :tenantId " +
           "AND g.deletedAt IS NULL AND g.fechaGasto BETWEEN :inicio AND :fin")
    BigDecimal sumMontoByTenantIdAndFechaBetween(
            @Param("tenantId") String tenantId,
            @Param("inicio") LocalDate inicio,
            @Param("fin") LocalDate fin);

    @Query("SELECT COALESCE(SUM(g.monto), 0) FROM Gasto g WHERE g.tenantId = :tenantId " +
           "AND g.sucursalId = :sucursalId AND g.deletedAt IS NULL AND g.fechaGasto BETWEEN :inicio AND :fin")
    BigDecimal sumMontoByTenantIdAndSucursalIdAndFechaBetween(
            @Param("tenantId") String tenantId,
            @Param("sucursalId") Long sucursalId,
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

    @Modifying
    @Query(value = "UPDATE gastos SET sucursal_id = :sucursalId WHERE tenant_id = :tenantId AND sucursal_id IS NULL", nativeQuery = true)
    void asignarSucursalDondeEsNulo(@Param("sucursalId") Long sucursalId, @Param("tenantId") String tenantId);
}
