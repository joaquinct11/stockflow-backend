package com.stockflow.repository;

import com.stockflow.entity.OrdenCompra;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.time.LocalDateTime;
import java.util.List;

@Repository
public interface OrdenCompraRepository extends JpaRepository<OrdenCompra, Long> {

    List<OrdenCompra> findByTenantId(String tenantId);

    @Query(value = "SELECT * FROM orden_compra WHERE tenant_id = :tenantId AND sucursal_id = :sucursalId ORDER BY created_at DESC", nativeQuery = true)
    List<OrdenCompra> findByTenantIdAndSucursalId(@Param("tenantId") String tenantId, @Param("sucursalId") Long sucursalId);

    List<OrdenCompra> findByTenantIdAndEstado(String tenantId, String estado);

    List<OrdenCompra> findByTenantIdAndProveedorId(String tenantId, Long proveedorId);

    List<OrdenCompra> findByTenantIdAndEstadoAndProveedorId(String tenantId, String estado, Long proveedorId);

    /** OCs en estado ENVIADA cuya fecha de creación es anterior a :limite — sin recepcionar. */
    @Query("""
            SELECT oc FROM OrdenCompra oc
            WHERE oc.tenantId = :tenantId
              AND oc.estado = 'ENVIADA'
              AND oc.createdAt < :limite
            """)
    List<OrdenCompra> findEnviadasSinRecepcionar(
            @Param("tenantId") String tenantId,
            @Param("limite")   LocalDateTime limite
    );

    @Modifying
    @Query(value = "UPDATE orden_compra SET sucursal_id = :sucursalId WHERE tenant_id = :tenantId AND sucursal_id IS NULL", nativeQuery = true)
    void asignarSucursalDondeEsNulo(@Param("sucursalId") Long sucursalId, @Param("tenantId") String tenantId);
}
