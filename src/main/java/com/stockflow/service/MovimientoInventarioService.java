package com.stockflow.service;

import com.stockflow.entity.MovimientoInventario;
import java.util.List;
import java.util.Optional;

public interface MovimientoInventarioService {

    MovimientoInventario crearMovimiento(MovimientoInventario movimiento);

    Optional<MovimientoInventario> obtenerMovimientoPorId(Long id);

    List<MovimientoInventario> obtenerTodosMovimientos();

    List<MovimientoInventario> obtenerMovimientosPorProducto(Long productoId);

    List<MovimientoInventario> obtenerMovimientosPorUsuario(Long usuarioId);

    List<MovimientoInventario> obtenerMovimientosPorTipo(String tipo);

    List<MovimientoInventario> obtenerMovimientosPorTenant(String tenantId);

    void eliminarMovimiento(Long id);

    List<MovimientoInventario> obtenerMovimientosPorTipoYTenant(String tipo, String tenantId);
}