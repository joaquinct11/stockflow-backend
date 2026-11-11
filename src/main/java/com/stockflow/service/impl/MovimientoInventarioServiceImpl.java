package com.stockflow.service.impl;

import com.stockflow.entity.MovimientoInventario;
import com.stockflow.exception.ResourceNotFoundException;
import com.stockflow.repository.MovimientoInventarioRepository;
import com.stockflow.service.MovimientoInventarioService;
import com.stockflow.util.TenantContext;
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
        String tenantId = TenantContext.getCurrentTenant();
        return movimientoRepository.findByIdAndTenantId(id, tenantId);
    }

    @Override
    public List<MovimientoInventario> obtenerMovimientosPorProducto(Long productoId, String tenantId) {
        return movimientoRepository.findByProductoIdAndTenantId(productoId, tenantId);
    }

    @Override
    public List<MovimientoInventario> obtenerMovimientosPorUsuario(Long usuarioId, String tenantId) {
        return movimientoRepository.findByUsuarioIdAndTenantId(usuarioId, tenantId);
    }

    @Override
    public List<MovimientoInventario> obtenerMovimientosPorTenant(String tenantId, Long sucursalId) {
        if (sucursalId != null) return movimientoRepository.findByTenantIdAndSucursalId(tenantId, sucursalId);
        return movimientoRepository.findByTenantId(tenantId);
    }

    @Override
    public void eliminarMovimiento(Long id) {
        String tenantId = TenantContext.getCurrentTenant();
        MovimientoInventario mov = movimientoRepository.findByIdAndTenantId(id, tenantId)
                .orElseThrow(() -> new ResourceNotFoundException("Movimiento no encontrado"));
        movimientoRepository.delete(mov);
    }

    @Override
    public List<MovimientoInventario> obtenerMovimientosPorTipoYTenant(String tipo, String tenantId) {
        return movimientoRepository.findByTipoAndTenantId(tipo, tenantId);
    }
}