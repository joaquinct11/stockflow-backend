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
        int fetch = limit * 2; // pedir más para que al mezclar sobre todo haya suficientes

        List<ActividadRecienteDTO> items = new ArrayList<>();

        // --- Ventas ---
        List<Venta> ventas = sucursalId != null
                ? ventaRepository.findByTenantIdAndSucursalId(tenantId, sucursalId)
                : ventaRepository.findByTenantId(tenantId);

        ventas.stream()
                .filter(v -> v.getCreatedAt() != null && !"ANULADA".equals(v.getEstado()))
                .sorted(Comparator.comparing(Venta::getCreatedAt).reversed())
                .limit(fetch)
                .forEach(v -> {
                    String usuario = nombreUsuario(v.getVendedor());
                    items.add(ActividadRecienteDTO.builder()
                            .tipo("VENTA")
                            .descripcion("Venta por S/ " + formatMonto(v.getTotal()))
                            .detalle(v.getMetodoPago() != null ? v.getMetodoPago().toLowerCase() : null)
                            .usuarioNombre(usuario)
                            .fechaHora(v.getCreatedAt())
                            .build());
                });

        // --- Comprobantes (boletas/facturas con número) ---
        comprobanteRepository.findByTenantId(tenantId).stream()
                .filter(c -> c.getFechaEmision() != null
                        && (sucursalId == null || sucursalIdDeVenta(c.getVenta()) == null
                            || sucursalIdDeVenta(c.getVenta()).equals(sucursalId)))
                .sorted(Comparator.comparing(Comprobante::getFechaEmision).reversed())
                .limit(fetch)
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

        // --- Movimientos ENTRADA y AJUSTE ---
        List<String> tiposFiltro = List.of("ENTRADA", "AJUSTE", "MERMA");
        List<MovimientoInventario> movimientos = sucursalId != null
                ? movimientoRepository.findByTenantIdAndSucursalId(tenantId, sucursalId)
                : movimientoRepository.findByTenantId(tenantId);
        movimientos.stream()
                .filter(m -> m.getCreatedAt() != null && tiposFiltro.contains(m.getTipo()))
                .sorted(Comparator.comparing(MovimientoInventario::getCreatedAt).reversed())
                .limit(fetch)
                .forEach(m -> {
                    String tipoActividad = switch (m.getTipo()) {
                        case "ENTRADA" -> "ENTRADA";
                        case "AJUSTE" -> "AJUSTE";
                        default -> "MERMA";
                    };
                    String desc = switch (m.getTipo()) {
                        case "ENTRADA" -> "Entrada de " + Math.abs(m.getCantidad()) + " unidades"
                                + (m.getReferencia() != null ? " · " + m.getReferencia() : "");
                        case "AJUSTE" -> "Ajuste de inventario: " + (m.getCantidad() >= 0 ? "+" : "") + m.getCantidad() + " unidades";
                        default -> "Merma de " + Math.abs(m.getCantidad()) + " unidades";
                    };
                    items.add(ActividadRecienteDTO.builder()
                            .tipo(tipoActividad)
                            .descripcion(desc)
                            .detalle(m.getDescripcion())
                            .usuarioNombre(nombreUsuario(m.getUsuario()))
                            .fechaHora(m.getCreatedAt())
                            .build());
                });

        // --- Órdenes de compra ---
        ordenCompraRepository.findByTenantId(tenantId).stream()
                .filter(oc -> oc.getCreatedAt() != null
                        && (sucursalId == null || sucursalId.equals(oc.getSucursalId())))
                .sorted(Comparator.comparing(OrdenCompra::getCreatedAt).reversed())
                .limit(fetch)
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

        // --- Anulaciones (ventas con estado ANULADA) ---
        ventas.stream()
                .filter(v -> v.getCreatedAt() != null && "ANULADA".equals(v.getEstado()))
                .sorted(Comparator.comparing(Venta::getCreatedAt).reversed())
                .limit(fetch)
                .forEach(v -> {
                    String usuario = nombreUsuario(v.getVendedor());
                    items.add(ActividadRecienteDTO.builder()
                            .tipo("ANULACION")
                            .descripcion("Venta anulada por S/ " + formatMonto(v.getTotal()))
                            .detalle("venta #" + v.getId())
                            .usuarioNombre(usuario)
                            .fechaHora(v.getCreatedAt())
                            .build());
                });

        // --- Devoluciones ---
        devolucionRepository.findByTenantIdOrderByFechaDevolucionDesc(tenantId).stream()
                .filter(d -> d.getFechaDevolucion() != null
                        && (sucursalId == null || sucursalId.equals(d.getSucursalId())))
                .limit(fetch)
                .forEach(d -> {
                    String usuario = nombreUsuario(d.getUsuario());
                    items.add(ActividadRecienteDTO.builder()
                            .tipo("DEVOLUCION")
                            .descripcion("Devolución por S/ " + formatMonto(d.getTotalDevuelto()))
                            .detalle(d.getMotivo() != null ? d.getMotivo() : null)
                            .usuarioNombre(usuario)
                            .fechaHora(d.getFechaDevolucion())
                            .build());
                });

        // Ordenar por fechaHora desc y limitar
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

    private Long sucursalIdDeVenta(Venta v) {
        return v != null ? v.getSucursalId() : null;
    }
}
