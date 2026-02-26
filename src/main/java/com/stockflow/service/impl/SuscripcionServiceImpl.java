package com.stockflow.service.impl;

import com.stockflow.entity.Suscripcion;
import com.stockflow.exception.ResourceNotFoundException;
import com.stockflow.repository.SuscripcionRepository;
import com.stockflow.service.SuscripcionService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

@Slf4j
@Service
@RequiredArgsConstructor
public class SuscripcionServiceImpl implements SuscripcionService {

    private final SuscripcionRepository suscripcionRepository;

    @Override
    public Suscripcion crearSuscripcion(Suscripcion suscripcion) {
        log.info("➕ Creando suscripción: Plan {} para usuario {}",
                suscripcion.getPlanId(),
                suscripcion.getUsuarioPrincipal().getId());
        return suscripcionRepository.save(suscripcion);
    }

    @Override
    public Optional<Suscripcion> obtenerSuscripcionPorId(Long id) {
        return suscripcionRepository.findById(id);
    }

    @Override
    public List<Suscripcion> obtenerTodasSuscripciones() {
        return suscripcionRepository.findAll();
    }

    @Override
    public Optional<Suscripcion> obtenerSuscripcionPorUsuario(Long usuarioId) {
        return suscripcionRepository.findByUsuarioPrincipalId(usuarioId);
    }

    @Override
    public List<Suscripcion> obtenerSuscripcionesPorEstado(String estado) {
        return suscripcionRepository.findByEstado(estado);
    }

    @Override
    public Suscripcion actualizarSuscripcion(Long id, Suscripcion suscripcion) {
        log.info("✏️ Actualizando suscripción ID: {}", id);

        return suscripcionRepository.findById(id)
                .map(suscripcionExistente -> {
                    suscripcionExistente.setPlanId(suscripcion.getPlanId());
                    suscripcionExistente.setPrecioMensual(suscripcion.getPrecioMensual());
                    suscripcionExistente.setEstado(suscripcion.getEstado());
                    suscripcionExistente.setMetodoPago(suscripcion.getMetodoPago());
                    suscripcionExistente.setUltimos4Digitos(suscripcion.getUltimos4Digitos());
                    return suscripcionRepository.save(suscripcionExistente);
                })
                .orElseThrow(() -> new ResourceNotFoundException("Suscripción no encontrada"));
    }

    @Override
    public Suscripcion activarSuscripcion(Long id) {
        log.info("✅ Activando suscripción con ID: {}", id);

        Suscripcion suscripcion = suscripcionRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Suscripción no encontrada con ID: " + id));

        // Cambiar el estado a ACTIVA
        suscripcion.setEstado("ACTIVA");
        suscripcion.setDeletedAt(null);
        suscripcion.setFechaCancelacion(null);

        // Guardar en la base de datos
        Suscripcion suscripcionActivada = suscripcionRepository.save(suscripcion);
        log.info("✅ Suscripción activada exitosamente: ID {}", suscripcionActivada.getId());

        return suscripcionActivada;
    }

    @Override
    public Suscripcion cancelarSuscripcion(Long id) {
        log.warn("❌ Cancelando suscripción con ID: {}", id);

        Suscripcion suscripcion = suscripcionRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Suscripción no encontrada con ID: " + id));

        // Cambiar el estado a CANCELADA
        suscripcion.setEstado("CANCELADA");
        suscripcion.setFechaCancelacion(LocalDateTime.now());
        suscripcion.setDeletedAt(LocalDateTime.now());

        // Guardar en la base de datos
        Suscripcion suscripcionCancelada = suscripcionRepository.save(suscripcion);
        log.warn("❌ Suscripción cancelada: ID {}", suscripcionCancelada.getId());

        return suscripcionCancelada;
    }

    @Override
    public void eliminarSuscripcion(Long id) {
        log.warn("🗑️ Eliminando suscripción ID: {}", id);
        suscripcionRepository.deleteById(id);
    }

    @Override
    public List<Suscripcion> obtenerSuscripcionesPorTenant(String tenantId) {
        log.info("🔍 Obteniendo suscripciones para tenant: {}", tenantId);
        return suscripcionRepository.findByTenantId(tenantId);
    }

    @Override
    public List<Suscripcion> obtenerSuscripcionesPorEstadoYTenant(String estado, String tenantId) {
        log.info("🔍 Obteniendo suscripciones con estado: {} para tenant: {}", estado, tenantId);
        return suscripcionRepository.findByEstadoAndTenantId(estado, tenantId);
    }
}