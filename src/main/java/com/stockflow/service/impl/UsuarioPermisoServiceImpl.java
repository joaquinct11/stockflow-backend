package com.stockflow.service.impl;

import com.stockflow.entity.Permiso;
import com.stockflow.entity.Usuario;
import com.stockflow.entity.UsuarioPermiso;
import com.stockflow.exception.ResourceNotFoundException;
import com.stockflow.repository.PermisoRepository;
import com.stockflow.repository.UsuarioPermisoRepository;
import com.stockflow.repository.UsuarioRepository;
import com.stockflow.service.UsuarioPermisoService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.List;
import java.util.stream.Collectors;

@Slf4j
@Service
@RequiredArgsConstructor
public class UsuarioPermisoServiceImpl implements UsuarioPermisoService {

    private final UsuarioPermisoRepository usuarioPermisoRepository;
    private final UsuarioRepository usuarioRepository;
    private final PermisoRepository permisoRepository;

    @Override
    @Transactional(readOnly = true)
    public List<String> obtenerPermisosCodigos(Long usuarioId, String tenantId) {
        return usuarioPermisoRepository.findPermisoCodigos(usuarioId, tenantId);
    }

    @Override
    @Transactional
    public void asignarPermisos(Long usuarioId, List<String> permisoCodigos, String tenantId) {
        log.info("🔑 Asignando {} permisos al usuario {} en tenant {}", permisoCodigos.size(), usuarioId, tenantId);

        Usuario usuario = usuarioRepository.findById(usuarioId)
                .orElseThrow(() -> new ResourceNotFoundException("Usuario no encontrado: " + usuarioId));

        // Delete existing assignments for this user+tenant
        usuarioPermisoRepository.deleteByUsuarioIdAndTenantId(usuarioId, tenantId);

        if (permisoCodigos.isEmpty()) {
            log.info("✅ Todos los permisos eliminados para el usuario {}", usuarioId);
            return;
        }

        // Fetch all matching permissions in a single query
        List<Permiso> permisos = permisoRepository.findByNombreIn(permisoCodigos);

        List<UsuarioPermiso> nuevasAsignaciones = permisos.stream()
                .map(permiso -> UsuarioPermiso.builder()
                        .usuario(usuario)
                        .permiso(permiso)
                        .tenantId(tenantId)
                        .createdAt(LocalDateTime.now())
                        .build())
                .collect(Collectors.toList());

        usuarioPermisoRepository.saveAll(nuevasAsignaciones);

        log.info("✅ {} permisos asignados al usuario {}", nuevasAsignaciones.size(), usuarioId);
    }
}
