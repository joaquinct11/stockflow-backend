package com.stockflow.service;

import com.stockflow.dto.ActividadRecienteDTO;
import com.stockflow.entity.*;
import com.stockflow.repository.*;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.util.*;

@Service
@RequiredArgsConstructor
public class DashboardService {

    private final VentaRepository ventaRepository;
    private final ComprobanteRepository comprobanteRepository;
    private final MovimientoInventarioRepository movimientoRepository;
    private final OrdenCompraRepository ordenCompraRepository;
    private final DevolucionRepository devolucionRepository;

    public List<ActividadRecienteDTO> getActividadReciente(String tenantId, Long sucursalId, int limit) {
        int fetch = limit * 2;

        List<ActividadRecienteDTO> items = new ArrayList<>();

        // --- Ventas (activas y anuladas, top N desde DB) ---
        List<Venta> ventas = sucursalId != null
                ? ventaRepository.findTopNRecentesByTenantIdAndSucursalId(tenantId, sucursalId, fetch * 2)
                : ventaRepository.findTopNRecentesByTenantId(tenantId, fetch * 2);

        ventas.stream()
                .filter(v -> v.getCreatedAt() != null && !"ANULADA".equals(v.getEstado()))
                .limit(fetch)
                .forEach(v -> items.add(ActividadRecienteDTO.builder()
                        .tipo("VENTA")
                        .descripcion("Venta por S/ " + formatMonto(v.getTotal()))
                        .detalle(v.getMetodoPago() != null ? v.getMetodoPago().toLowerCase() : null)
                        .usuarioNombre(nombreUsuario(v.getVendedor()))
                        .fechaHora(v.getCreatedAt())
                        .build()));

        ventas.stream()
                .filter(v -> v.getCreatedAt() != null && "ANULADA".equals(v.getEstado()))
                .limit(fetch)
                .forEach(v -> items.add(ActividadRecienteDTO.builder()
                        .tipo("ANULACION")
                        .descripcion("Venta anulada por S/ " + formatMonto(v.getTotal()))
                        .detalle("venta #" + v.getId())
                        .usuarioNombre(nombreUsuario(v.getVendedor()))
                        .fechaHora(v.getCreatedAt())
                        .build()));

        // --- Comprobantes (top N desde DB, filtro de sucursal en query) ---
        comprobanteRepository.findTopNRecentesByTenantId(tenantId, sucursalId, fetch).stream()
                .filter(c -> c.getFechaEmision() != null)
                .forEach(c -> {
                    String tipoLabel = "FACTURA".equalsIgnoreCase(c.getTipo()) ? "Factura" : "Boleta";
                    String sunatDetalle = c.getSunatEstado() != null
                            ? tipoLabel.toLowerCase() + " " + c.getSunatEstado().toLowerCase()
                            : null;
                    items.add(ActividadRecienteDTO.builder()
                            .tipo("COMPROBANTE")
                            .descripcion(tipoLabel + " " + c.getNumero() + " por S/ " + formatMonto(c.getTotal()))
                            .detalle(sunatDetalle)
                            .usuarioNombre(null)
                            .fechaHora(c.getFechaEmision())
                            .build());
                });

        // --- Movimientos ENTRADA/AJUSTE/MERMA (top N desde DB, filtrados por tipo en query) ---
        List<MovimientoInventario> movimientos = sucursalId != null
                ? movimientoRepository.findTopNActividadByTenantIdAndSucursalId(tenantId, sucursalId, fetch)
                : movimientoRepository.findTopNActividadByTenantId(tenantId, fetch);

        movimientos.stream()
                .filter(m -> m.getCreatedAt() != null)
                .forEach(m -> {
                    String desc = switch (m.getTipo()) {
                        case "ENTRADA" -> "Entrada de " + Math.abs(m.getCantidad()) + " unidades"
                                + (m.getReferencia() != null ? " · " + m.getReferencia() : "");
                        case "AJUSTE"  -> "Ajuste de inventario: " + (m.getCantidad() >= 0 ? "+" : "") + m.getCantidad() + " unidades";
                        default        -> "Merma de " + Math.abs(m.getCantidad()) + " unidades";
                    };
                    items.add(ActividadRecienteDTO.builder()
                            .tipo(m.getTipo())
                            .descripcion(desc)
                            .detalle(m.getDescripcion())
                            .usuarioNombre(nombreUsuario(m.getUsuario()))
                            .fechaHora(m.getCreatedAt())
                            .build());
                });

        // --- Órdenes de compra (top N desde DB, filtro de sucursal en query) ---
        ordenCompraRepository.findTopNRecentesByTenantId(tenantId, sucursalId, fetch).stream()
                .filter(oc -> oc.getCreatedAt() != null)
                .forEach(oc -> {
                    String proveedor = oc.getProveedor() != null ? oc.getProveedor().getNombre() : "proveedor";
                    items.add(ActividadRecienteDTO.builder()
                            .tipo("ORDEN_COMPRA")
                            .descripcion("Orden de compra a " + proveedor)
                            .detalle("estado: " + oc.getEstado().toLowerCase())
                            .usuarioNombre(nombreUsuario(oc.getUsuarioCreador()))
                            .fechaHora(oc.getCreatedAt())
                            .build());
                });

        // --- Devoluciones (top N desde DB, filtro de sucursal en query) ---
        devolucionRepository.findTopNRecentesByTenantId(tenantId, sucursalId, fetch).stream()
                .filter(d -> d.getFechaDevolucion() != null)
                .forEach(d -> items.add(ActividadRecienteDTO.builder()
                        .tipo("DEVOLUCION")
                        .descripcion("Devolución por S/ " + formatMonto(d.getTotalDevuelto()))
                        .detalle(d.getMotivo())
                        .usuarioNombre(nombreUsuario(d.getUsuario()))
                        .fechaHora(d.getFechaDevolucion())
                        .build()));

        items.sort(Comparator.comparing(ActividadRecienteDTO::getFechaHora).reversed());
        return items.stream().limit(limit).toList();
    }

    private String nombreUsuario(Usuario u) {
        if (u == null) return null;
        String n = u.getNombre() != null ? u.getNombre() : "";
        String a = u.getApellido() != null ? u.getApellido() : "";
        String completo = (n + " " + a).trim();
        if (completo.isBlank()) return u.getEmail();
        // Abreviar: "María Quispe" → "María Q."
        String[] partes = completo.split("\\s+");
        if (partes.length >= 2) {
            return partes[0] + " " + partes[1].charAt(0) + ".";
        }
        return completo;
    }

    private String formatMonto(BigDecimal monto) {
        if (monto == null) return "0,00";
        return String.format("%,.2f", monto).replace('.', ',');
    }

}
