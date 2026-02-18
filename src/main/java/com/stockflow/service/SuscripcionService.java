package com.stockflow.service;

import com.stockflow.entity.Suscripcion;
import java.util.List;
import java.util.Optional;

public interface SuscripcionService {

    Suscripcion crearSuscripcion(Suscripcion suscripcion);

    Optional<Suscripcion> obtenerSuscripcionPorId(Long id);

    List<Suscripcion> obtenerTodasSuscripciones();

    Optional<Suscripcion> obtenerSuscripcionPorUsuario(Long usuarioId);

    List<Suscripcion> obtenerSuscripcionesPorEstado(String estado);

    Suscripcion actualizarSuscripcion(Long id, Suscripcion suscripcion);

    Suscripcion activarSuscripcion(Long id);

    void eliminarSuscripcion(Long id);
}