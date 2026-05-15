package com.stockflow.service;

import com.stockflow.entity.Venta;
import com.stockflow.entity.DetalleVenta;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

public interface VentaService {

    Venta crearVenta(Venta venta);

    Optional<Venta> obtenerVentaPorId(Long id);

    List<Venta> obtenerVentasPorVendedorYTenant(Long vendedorId, String tenantId);

    List<Venta> obtenerVentasPorTenant(String tenantId);

    List<Venta> obtenerVentasPorPeriodo(String tenantId, LocalDateTime inicio, LocalDateTime fin);

    List<DetalleVenta> obtenerDetallesVenta(Long ventaId);

    void actualizarNotaCredito(Long ventaId, Long notaCreditoId);
}
