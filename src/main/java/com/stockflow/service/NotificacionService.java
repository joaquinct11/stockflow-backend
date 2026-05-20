package com.stockflow.service;

import com.stockflow.dto.NotificacionDTO;
import java.util.List;

public interface NotificacionService {

    // ── Creación ────────────────────────────────────────────────────────────

    /** Crea una notificación para cada rol de la lista. */
    void notificarRoles(String tenantId, List<String> roles,
                        String tipo, String titulo, String mensaje,
                        Long referenciaId, String referenciaTipo);

    /** Crea una notificación para un usuario específico. */
    void notificarUsuario(Long usuarioId, String tenantId,
                          String tipo, String titulo, String mensaje,
                          Long referenciaId, String referenciaTipo);

    // ── Consultas ───────────────────────────────────────────────────────────

    List<NotificacionDTO> getMisNotificaciones(Long usuarioId, String rolNombre, String tenantId);

    long contarNoLeidas(Long usuarioId, String rolNombre, String tenantId);

    // ── Acciones ────────────────────────────────────────────────────────────

    void marcarLeida(Long notificacionId, Long usuarioId, String rolNombre, String tenantId);

    void marcarTodasLeidas(Long usuarioId, String rolNombre, String tenantId);

    void eliminar(Long notificacionId, Long usuarioId, String rolNombre, String tenantId);

    void eliminarTodasLeidas(Long usuarioId, String rolNombre, String tenantId);

    // ── Scheduler helpers ───────────────────────────────────────────────────

    /** Evita duplicar notificaciones del scheduler para el mismo recurso. */
    boolean yaExisteNoLeida(String tenantId, String rolDestino, String tipo, Long referenciaId);
}
