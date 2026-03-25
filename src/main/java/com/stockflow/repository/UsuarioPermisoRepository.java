package com.stockflow.repository;

import com.stockflow.entity.UsuarioPermiso;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public interface UsuarioPermisoRepository extends JpaRepository<UsuarioPermiso, Long> {

    @Query("SELECT up FROM UsuarioPermiso up JOIN FETCH up.permiso WHERE up.usuario.id = :usuarioId AND up.tenantId = :tenantId")
    List<UsuarioPermiso> findByUsuarioIdAndTenantId(@Param("usuarioId") Long usuarioId,
                                                    @Param("tenantId") String tenantId);

    @Query("SELECT up.permiso.nombre FROM UsuarioPermiso up WHERE up.usuario.id = :usuarioId AND up.tenantId = :tenantId")
    List<String> findPermisoCodigos(@Param("usuarioId") Long usuarioId,
                                    @Param("tenantId") String tenantId);

    @Modifying
    @Query("DELETE FROM UsuarioPermiso up WHERE up.usuario.id = :usuarioId AND up.tenantId = :tenantId")
    void deleteByUsuarioIdAndTenantId(@Param("usuarioId") Long usuarioId,
                                      @Param("tenantId") String tenantId);
}
