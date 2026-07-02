package com.stockflow.repository;

import com.stockflow.entity.Usuario;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;
import org.springframework.transaction.annotation.Transactional;
import java.util.Optional;
import java.util.List;

@Repository
public interface UsuarioRepository extends JpaRepository<Usuario, Long> {

    Optional<Usuario> findByEmail(String email);

    List<Usuario> findByTenantId(String tenantId);

    List<Usuario> findByActivoTrue();

    long countByTenantId(String tenantId);

    Optional<Usuario> findByTokenRecuperacion(String tokenRecuperacion);

    Optional<Usuario> findByTokenActivacion(String tokenActivacion);

    /** Devuelve los usuarios ADMIN y GERENTE activos de un tenant (para notificaciones). */
    @Query("SELECT u FROM Usuario u WHERE u.tenantId = :tenantId AND u.rol.nombre IN ('ADMIN', 'GERENTE') AND u.activo = true")
    List<Usuario> findAdminYGerenteByTenant(@Param("tenantId") String tenantId);

    @Query("SELECT u FROM Usuario u WHERE u.activo = true AND u.tenantId = :tenantId")
    List<Usuario> findActivosByTenant(@Param("tenantId") String tenantId);

    @Transactional
    @Modifying
    @Query("UPDATE Usuario u SET u.activo = false WHERE u.tenantId = :tenantId")
    void desactivarPorTenant(@Param("tenantId") String tenantId);

    @Transactional
    @Modifying
    @Query("UPDATE Usuario u SET u.activo = true WHERE u.tenantId = :tenantId")
    void activarPorTenant(@Param("tenantId") String tenantId);

    /**
     * Al inicializar la sucursal principal (upgrade PRO), asigna la sucursal
     * solo a usuarios no-ADMIN que aún no tienen sucursal asignada.
     * El ADMIN mantiene sucursal_id = NULL para poder ver todos los locales.
     */
    @Modifying
    @Query(value = "UPDATE usuarios SET sucursal_id = :sucursalId WHERE tenant_id = :tenantId AND sucursal_id IS NULL AND rol_id != (SELECT id FROM roles WHERE nombre = 'ADMIN' LIMIT 1)", nativeQuery = true)
    void asignarSucursalAdminNulo(@Param("sucursalId") Long sucursalId, @Param("tenantId") String tenantId);
}
