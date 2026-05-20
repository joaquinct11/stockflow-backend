package com.stockflow.service.impl;

import com.stockflow.dto.NotificacionDTO;
import com.stockflow.entity.Notificacion;
import com.stockflow.repository.NotificacionRepository;
import com.stockflow.service.NotificacionService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

@Slf4j
@Service
@RequiredArgsConstructor
public class NotificacionServiceImpl implements NotificacionService {

    private final NotificacionRepository notificacionRepository;

    // ── Creación ────────────────────────────────────────────────────────────

    @Override
    @Transactional
    public void notificarRoles(String tenantId, List<String> roles,
                                String tipo, String titulo, String mensaje,
                                Long referenciaId, String referenciaTipo) {
        for (String rol : roles) {
            Notificacion n = Notificacion.builder()
                    .tenantId(tenantId)
                    .rolDestino(rol)
                    .tipo(tipo)
                    .titulo(titulo)
                    .mensaje(mensaje)
                    .referenciaId(referenciaId)
                    .referenciaTipo(referenciaTipo)
                    .build();
            notificacionRepository.save(n);
        }
        log.debug("🔔 Notificación '{}' creada para roles {} en tenant={}", tipo, roles, tenantId);
    }

    @Override
    @Transactional
    public void notificarUsuario(Long usuarioId, String tenantId,
                                  String tipo, String titulo, String mensaje,
                                  Long referenciaId, String referenciaTipo) {
        Notificacion n = Notificacion.builder()
                .tenantId(tenantId)
                .usuarioId(usuarioId)
                .tipo(tipo)
                .titulo(titulo)
                .mensaje(mensaje)
                .referenciaId(referenciaId)
                .referenciaTipo(referenciaTipo)
                .build();
        notificacionRepository.save(n);
        log.debug("🔔 Notificación '{}' creada para usuarioId={}", tipo, usuarioId);
    }

    // ── Consultas ───────────────────────────────────────────────────────────

    @Override
    @Transactional(readOnly = true)
    public List<NotificacionDTO> getMisNotificaciones(Long usuarioId, String rolNombre, String tenantId) {
        return notificacionRepository
                .findAllByUsuario(tenantId, rolNombre, usuarioId)
                .stream()
                .limit(50)
                .map(this::toDTO)
                .toList();
    }

    @Override
    @Transactional(readOnly = true)
    public long contarNoLeidas(Long usuarioId, String rolNombre, String tenantId) {
        return notificacionRepository.countNoLeidas(tenantId, rolNombre, usuarioId);
    }

    // ── Acciones ────────────────────────────────────────────────────────────

    @Override
    @Transactional
    public void marcarLeida(Long notificacionId, Long usuarioId, String rolNombre, String tenantId) {
        notificacionRepository.findById(notificacionId).ifPresent(n -> {
            // Verificar que pertenece al usuario o a su rol
            boolean esDelUsuario = usuarioId.equals(n.getUsuarioId());
            boolean esDeRol = rolNombre.equals(n.getRolDestino()) && tenantId.equals(n.getTenantId());
            if (esDelUsuario || esDeRol) {
                n.setLeida(true);
                notificacionRepository.save(n);
            }
        });
    }

    @Override
    @Transactional
    public void marcarTodasLeidas(Long usuarioId, String rolNombre, String tenantId) {
        notificacionRepository.marcarTodasLeidas(tenantId, rolNombre, usuarioId);
        log.debug("🔔 Todas las notificaciones marcadas como leídas para usuarioId={} rol={}", usuarioId, rolNombre);
    }

    // ── Eliminación ─────────────────────────────────────────────────────────

    @Override
    @Transactional
    public void eliminar(Long notificacionId, Long usuarioId, String rolNombre, String tenantId) {
        notificacionRepository.findById(notificacionId).ifPresent(n -> {
            boolean esDelUsuario = usuarioId.equals(n.getUsuarioId());
            boolean esDeRol = rolNombre.equals(n.getRolDestino()) && tenantId.equals(n.getTenantId());
            if (esDelUsuario || esDeRol) {
                notificacionRepository.delete(n);
            }
        });
    }

    @Override
    @Transactional
    public void eliminarTodasLeidas(Long usuarioId, String rolNombre, String tenantId) {
        notificacionRepository.eliminarTodasLeidas(tenantId, rolNombre, usuarioId);
        log.debug("🗑️ Notificaciones leídas eliminadas para usuarioId={} rol={}", usuarioId, rolNombre);
    }

    // ── Scheduler helpers ───────────────────────────────────────────────────

    @Override
    public boolean yaExisteNoLeida(String tenantId, String rolDestino, String tipo, Long referenciaId) {
        return notificacionRepository
                .existsByTenantIdAndRolDestinoAndTipoAndReferenciaIdAndLeidaFalse(
                        tenantId, rolDestino, tipo, referenciaId);
    }

    // ── Mapper ──────────────────────────────────────────────────────────────

    private NotificacionDTO toDTO(Notificacion n) {
        return NotificacionDTO.builder()
                .id(n.getId())
                .tipo(n.getTipo())
                .titulo(n.getTitulo())
                .mensaje(n.getMensaje())
                .leida(n.isLeida())
                .referenciaId(n.getReferenciaId())
                .referenciaTipo(n.getReferenciaTipo())
                .createdAt(n.getCreatedAt())
                .build();
    }
}
