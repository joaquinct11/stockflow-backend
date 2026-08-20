package com.stockflow.service;

import com.stockflow.entity.MovimientoInventario;
import java.util.List;
import java.util.Optional;

public interface MovimientoInventarioService {

    MovimientoInventario crearMovimiento(MovimientoInventario movimiento);

    Optional<MovimientoInventario> obtenerMovimientoPorId(Long id);

    List<MovimientoInventario> obtenerMovimientosPorProducto(Long productoId, String tenantId);

    List<MovimientoInventario> obtenerMovimientosPorUsuario(Long usuarioId, String tenantId);

    List<MovimientoInventario> obtenerMovimientosPorTenant(String tenantId, Long sucursalId);

    void eliminarMovimiento(Long id);

    List<MovimientoInventario> obtenerMovimientosPorTipoYTenant(String tipo, String tenantId);
}
