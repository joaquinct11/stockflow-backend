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
import java.time.LocalDateTime;

@Repository
public interface UsuarioRepository extends JpaRepository<Usuario, Long> {

    Optional<Usuario> findByEmail(String email);

    List<Usuario> findByTenantId(String tenantId);

    List<Usuario> findByActivoTrue();

    @Query("SELECT u FROM Usuario u WHERE u.deletedAt IS NULL AND u.tenantId = :tenantId AND u.activo = true")
    List<Usuario> findActivosByTenant(@Param("tenantId") String tenantId);

    @Transactional
    @Modifying
    @Query("UPDATE Usuario u SET u.activo = false, u.deletedAt = CURRENT_TIMESTAMP WHERE u.tenantId = :tenantId")
    void desactivarPorTenant(@Param("tenantId") String tenantId);

    @Transactional
    @Modifying
    @Query("UPDATE Usuario u SET u.activo = true, u.deletedAt = NULL WHERE u.tenantId = :tenantId")
    void activarPorTenant(@Param("tenantId") String tenantId);
}