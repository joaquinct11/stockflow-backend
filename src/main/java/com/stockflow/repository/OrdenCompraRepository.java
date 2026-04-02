package com.stockflow.repository;

import com.stockflow.entity.OrdenCompra;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public interface OrdenCompraRepository extends JpaRepository<OrdenCompra, Long> {

    List<OrdenCompra> findByTenantId(String tenantId);

    List<OrdenCompra> findByTenantIdAndEstado(String tenantId, String estado);

    List<OrdenCompra> findByTenantIdAndProveedorId(String tenantId, Long proveedorId);

    List<OrdenCompra> findByTenantIdAndEstadoAndProveedorId(String tenantId, String estado, Long proveedorId);
}
