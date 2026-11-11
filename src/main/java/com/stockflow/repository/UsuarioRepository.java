package com.stockflow.repository;

import com.stockflow.entity.Usuario;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;
import java.util.Optional;
import java.util.List;

@Repository
public interface UsuarioRepository extends JpaRepository<Usuario, Long> {

    Optional<Usuario> findByEmail(String email);

    List<Usuario> findByActivoTrue();

    Optional<Usuario> findByTokenRecuperacion(String tokenRecuperacion);

    Optional<Usuario> findByTokenActivacion(String tokenActivacion);

    /** Devuelve los usuarios ADMIN y GERENTE activos de un tenant via usuario_tenant (para notificaciones). */
    @Query("SELECT u FROM Usuario u WHERE u.activo = true AND u.id IN " +
           "(SELECT ut.usuario.id FROM UsuarioTenant ut WHERE ut.tenantId = :tenantId " +
           "AND ut.activo = true AND ut.rol.nombre IN ('ADMIN', 'GERENTE'))")
    List<Usuario> findAdminYGerenteByTenant(@Param("tenantId") String tenantId);
}
