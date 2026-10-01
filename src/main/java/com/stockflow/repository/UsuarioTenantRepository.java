package com.stockflow.repository;

import com.stockflow.entity.UsuarioTenant;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public interface UsuarioTenantRepository extends JpaRepository<UsuarioTenant, Long> {

    List<UsuarioTenant> findByUsuarioIdAndActivoTrue(Long usuarioId);

    Optional<UsuarioTenant> findByUsuarioIdAndTenantIdAndActivoTrue(Long usuarioId, String tenantId);

    boolean existsByUsuarioIdAndTenantIdAndActivoTrue(Long usuarioId, String tenantId);

    long countByUsuarioIdAndActivoTrue(Long usuarioId);

    @Query("SELECT ut FROM UsuarioTenant ut JOIN FETCH ut.rol WHERE ut.usuario.id = :usuarioId AND ut.activo = true")
    List<UsuarioTenant> findActivosConRolByUsuarioId(@Param("usuarioId") Long usuarioId);
}
