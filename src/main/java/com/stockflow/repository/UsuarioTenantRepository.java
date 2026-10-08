package com.stockflow.repository;

import com.stockflow.entity.UsuarioTenant;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.Optional;

@Repository
public interface UsuarioTenantRepository extends JpaRepository<UsuarioTenant, Long> {

    List<UsuarioTenant> findByUsuarioIdAndActivoTrue(Long usuarioId);

    List<UsuarioTenant> findByTenantIdAndActivoTrue(String tenantId);

    Optional<UsuarioTenant> findByUsuarioIdAndTenantId(Long usuarioId, String tenantId);

    Optional<UsuarioTenant> findByUsuarioIdAndTenantIdAndActivoTrue(Long usuarioId, String tenantId);

    boolean existsByUsuarioIdAndTenantIdAndActivoTrue(Long usuarioId, String tenantId);

    long countByUsuarioIdAndActivoTrue(Long usuarioId);

    long countByTenantIdAndActivoTrue(String tenantId);

    @Query("SELECT ut FROM UsuarioTenant ut JOIN FETCH ut.rol WHERE ut.usuario.id = :usuarioId AND ut.activo = true")
    List<UsuarioTenant> findActivosConRolByUsuarioId(@Param("usuarioId") Long usuarioId);

    /**
     * Al crear la sucursal principal de un tenant (upgrade PRO), asigna esa sucursal
     * a todos los usuario_tenant del tenant que aún no tienen sucursal, excepto los ADMIN.
     * Filtra por tenant_id → no afecta a otros tenants.
     */
    @Transactional
    @Modifying
    @Query("UPDATE UsuarioTenant ut SET ut.sucursalId = :sucursalId " +
           "WHERE ut.tenantId = :tenantId AND ut.sucursalId IS NULL AND ut.rol.nombre <> 'ADMIN'")
    void asignarSucursalDondeEsNuloByTenant(@Param("sucursalId") Long sucursalId,
                                            @Param("tenantId") String tenantId);
}
