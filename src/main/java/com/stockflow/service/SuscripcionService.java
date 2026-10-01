package com.stockflow.service;

import com.stockflow.entity.Suscripcion;
import java.util.List;
import java.util.Optional;

public interface SuscripcionService {

    Suscripcion crearSuscripcion(Suscripcion suscripcion);

    Optional<Suscripcion> obtenerSuscripcionPorId(Long id);

    Optional<Suscripcion> obtenerSuscripcionPorUsuario(Long usuarioId);

    /** Suscripción vigente del tenant (la más reciente). Usar en flujos auth. */
    Optional<Suscripcion> obtenerSuscripcionPorTenant(String tenantId);

    Suscripcion activarSuscripcion(Long id);

    void eliminarSuscripcion(Long id);

    List<Suscripcion> obtenerSuscripcionesPorTenant(String tenantId);

    List<Suscripcion> obtenerSuscripcionesPorEstadoYTenant(String estado, String tenantId);

    Suscripcion expirarTrial(Long suscripcionId);
}
