package com.stockflow.service.impl;

import com.stockflow.entity.Suscripcion;
import com.stockflow.exception.ResourceNotFoundException;
import com.stockflow.repository.SuscripcionRepository;
import com.stockflow.service.SuscripcionService;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import java.util.List;
import java.util.Optional;

@Service
@RequiredArgsConstructor
public class SuscripcionServiceImpl implements SuscripcionService {

    private final SuscripcionRepository suscripcionRepository;

    @Override
    public Suscripcion crearSuscripcion(Suscripcion suscripcion) {
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
    public void eliminarSuscripcion(Long id) {
        suscripcionRepository.deleteById(id);
    }
}