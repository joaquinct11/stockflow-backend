package com.stockflow.repository;

import com.stockflow.entity.Notificacion;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;
import java.time.LocalDateTime;
import java.util.List;

@Repository
public interface NotificacionRepository extends JpaRepository<Notificacion, Long> {

    /** Todas las notificaciones no leídas del usuario: las de su rol + las dirigidas a él. */
    @Query("""
            SELECT n FROM Notificacion n
            WHERE n.leida = false
              AND n.tenantId = :tenantId
              AND (n.rolDestino = :rolDestino OR n.usuarioId = :usuarioId)
            ORDER BY n.createdAt DESC
            """)
    List<Notificacion> findNoLeidasByUsuario(
            @Param("tenantId")   String tenantId,
            @Param("rolDestino") String rolDestino,
            @Param("usuarioId")  Long   usuarioId
    );

    /** Todas (leídas y no leídas) — no leídas primero, luego leídas, ambas por fecha desc. */
    @Query("""
            SELECT n FROM Notificacion n
            WHERE n.tenantId = :tenantId
              AND (n.rolDestino = :rolDestino OR n.usuarioId = :usuarioId)
            ORDER BY n.leida ASC, n.createdAt DESC
            """)
    List<Notificacion> findAllByUsuario(
            @Param("tenantId")   String tenantId,
            @Param("rolDestino") String rolDestino,
            @Param("usuarioId")  Long   usuarioId
    );

    /** Contador de no leídas — para el badge. */
    @Query("""
            SELECT COUNT(n) FROM Notificacion n
            WHERE n.leida = false
              AND n.tenantId = :tenantId
              AND (n.rolDestino = :rolDestino OR n.usuarioId = :usuarioId)
            """)
    long countNoLeidas(
            @Param("tenantId")   String tenantId,
            @Param("rolDestino") String rolDestino,
            @Param("usuarioId")  Long   usuarioId
    );

    /** Marca como leídas todas las del usuario. */
    @Modifying
    @Query("""
            UPDATE Notificacion n SET n.leida = true
            WHERE n.leida = false
              AND n.tenantId = :tenantId
              AND (n.rolDestino = :rolDestino OR n.usuarioId = :usuarioId)
            """)
    void marcarTodasLeidas(
            @Param("tenantId")   String tenantId,
            @Param("rolDestino") String rolDestino,
            @Param("usuarioId")  Long   usuarioId
    );

    /** Elimina todas las notificaciones leídas del usuario (limpieza del panel). */
    @Modifying
    @Query("""
            DELETE FROM Notificacion n
            WHERE n.leida = true
              AND n.tenantId = :tenantId
              AND (n.rolDestino = :rolDestino OR n.usuarioId = :usuarioId)
            """)
    void eliminarTodasLeidas(
            @Param("tenantId")   String tenantId,
            @Param("rolDestino") String rolDestino,
            @Param("usuarioId")  Long   usuarioId
    );

    /** Evita duplicar notificaciones del scheduler para el mismo recurso. */
    boolean existsByTenantIdAndRolDestinoAndTipoAndReferenciaIdAndLeidaFalse(
            String tenantId, String rolDestino, String tipo, Long referenciaId
    );

    /** Limpieza global: elimina notificaciones leídas anteriores a una fecha. */
    @Modifying
    @Query("DELETE FROM Notificacion n WHERE n.leida = true AND n.createdAt < :antes")
    int eliminarLeidasAntiguasGlobal(@Param("antes") LocalDateTime antes);

    /** Limpieza global: elimina notificaciones NO leídas anteriores a una fecha (demasiado antiguas para ser útiles). */
    @Modifying
    @Query("DELETE FROM Notificacion n WHERE n.leida = false AND n.createdAt < :antes")
    int eliminarNoLeidasAntiguasGlobal(@Param("antes") LocalDateTime antes);
}
