package com.stockflow.repository;

import com.stockflow.entity.Cliente;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public interface ClienteRepository extends JpaRepository<Cliente, Long> {

    List<Cliente> findByTenantId(String tenantId);

    List<Cliente> findByTenantId(String tenantId, Pageable pageable);

    List<Cliente> findByTenantIdAndActivoTrue(String tenantId);

    List<Cliente> findByTenantIdAndActivoTrue(String tenantId, Pageable pageable);

    List<Cliente> findByNombreContainingIgnoreCaseAndTenantId(String nombre, String tenantId);

    List<Cliente> findByNombreContainingIgnoreCaseAndTenantId(String nombre, String tenantId, Pageable pageable);

    List<Cliente> findByNumeroDocumentoAndTenantId(String numeroDocumento, String tenantId);

    List<Cliente> findByNumeroDocumentoAndTenantId(String numeroDocumento, String tenantId, Pageable pageable);

    long countByTenantId(String tenantId);

    long countByTenantIdAndActivoTrue(String tenantId);

    java.util.Optional<Cliente> findByIdAndTenantId(Long id, String tenantId);
}
