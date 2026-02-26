package com.stockflow.service.impl;

import com.stockflow.entity.MovimientoInventario;
import com.stockflow.entity.Suscripcion;
import com.stockflow.repository.MovimientoInventarioRepository;
import com.stockflow.service.MovimientoInventarioService;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import java.util.List;
import java.util.Optional;

@Service
@RequiredArgsConstructor
public class MovimientoInventarioServiceImpl implements MovimientoInventarioService {

    private final MovimientoInventarioRepository movimientoRepository;

    @Override
    public MovimientoInventario crearMovimiento(MovimientoInventario movimiento) {
        return movimientoRepository.save(movimiento);
    }

    @Override
    public Optional<MovimientoInventario> obtenerMovimientoPorId(Long id) {
        return movimientoRepository.findById(id);
    }

    @Override
    public List<MovimientoInventario> obtenerTodosMovimientos() {
        return movimientoRepository.findAll();
    }

    @Override
    public List<MovimientoInventario> obtenerMovimientosPorProducto(Long productoId) {
        return movimientoRepository.findByProductoId(productoId);
    }

    @Override
    public List<MovimientoInventario> obtenerMovimientosPorUsuario(Long usuarioId) {
        return movimientoRepository.findByUsuarioId(usuarioId);
    }

    @Override
    public List<MovimientoInventario> obtenerMovimientosPorTipo(String tipo) {
        return movimientoRepository.findByTipo(tipo);
    }

    @Override
    public List<MovimientoInventario> obtenerMovimientosPorTenant(String tenantId) {
        return movimientoRepository.findByTenantId(tenantId);
    }

    @Override
    public void eliminarMovimiento(Long id) {
        movimientoRepository.deleteById(id);
    }

    @Override
    public List<MovimientoInventario> obtenerMovimientosPorTipoYTenant(String tipo, String tenantId) {
        return movimientoRepository.findByTipoAndTenantId(tipo, tenantId);
    }
}