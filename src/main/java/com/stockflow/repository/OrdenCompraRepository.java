package com.stockflow.repository;

import com.stockflow.entity.OrdenCompra;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.time.LocalDateTime;
import java.util.List;

@Repository
public interface OrdenCompraRepository extends JpaRepository<OrdenCompra, Long> {

    List<OrdenCompra> findByTenantId(String tenantId);

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
}
