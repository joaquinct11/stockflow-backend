package com.stockflow.service.impl;

import com.stockflow.dto.CrearDevolucionDTO;
import com.stockflow.dto.DevolucionDTO;
import com.stockflow.dto.DevolucionDetalleItemDTO;
import com.stockflow.dto.DevolucionDetalleRespDTO;
import com.stockflow.entity.*;
import com.stockflow.exception.BadRequestException;
import com.stockflow.exception.ResourceNotFoundException;
import com.stockflow.repository.*;
import com.stockflow.service.DevolucionService;
import com.stockflow.service.NotaCreditoService;
import com.stockflow.util.TenantContext;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

@Service
@RequiredArgsConstructor
@Slf4j
@Transactional
public class DevolucionServiceImpl implements DevolucionService {

    private final DevolucionRepository devolucionRepository;
    private final VentaRepository ventaRepository;
    private final ProductoRepository productoRepository;
    private final UsuarioRepository usuarioRepository;
    private final MovimientoInventarioRepository movimientoRepository;
    private final NotaCreditoService notaCreditoService;
    private final ProductoStockSucursalRepository stockSucursalRepository;
    private final SucursalRepository sucursalRepository;
    private final ProductoVarianteRepository productoVarianteRepository;
    private final ProductoVarianteStockSucursalRepository varianteStockSucursalRepository;

    @Override
    public DevolucionDTO crear(CrearDevolucionDTO dto, Long usuarioId, String tenantId) {
        // 1. Validate venta exists and belongs to tenant
        Venta venta = ventaRepository.findById(dto.getVentaId())
                .filter(v -> tenantId.equals(v.getTenantId()))
                .orElseThrow(() -> new ResourceNotFoundException("Venta no encontrada"));

        // 2. Validate venta is returnable
        if ("DEVUELTA".equals(venta.getEstado())) {
            throw new BadRequestException("Esta venta ya fue devuelta completamente");
        }

        // 3. Get user
        Usuario usuario = usuarioRepository.findById(usuarioId)
                .orElseThrow(() -> new ResourceNotFoundException("Usuario no encontrado"));

        // 4. Calculate already returned quantities per product
        List<Devolucion> devolucionesExistentes = devolucionRepository.findByVentaIdAndTenantId(dto.getVentaId(), tenantId);

        Map<Long, Integer> yaDevuelto = new HashMap<>();
        for (Devolucion dev : devolucionesExistentes) {
            for (DevolucionDetalle det : dev.getDetalles()) {
                yaDevuelto.merge(det.getProducto().getId(), det.getCantidadDevuelta(), Integer::sum);
            }
        }

        // 5. Validate each item against original sale
        Map<Long, DetalleVenta> detallesPorProducto = new HashMap<>();
        for (DetalleVenta dv : venta.getDetalles()) {
            detallesPorProducto.put(dv.getProducto().getId(), dv);
        }

        BigDecimal totalDevuelto = BigDecimal.ZERO;
        List<DevolucionDetalle> detalles = new ArrayList<>();

        for (DevolucionDetalleItemDTO item : dto.getDetalles()) {
            if (item.getCantidadDevuelta() == null || item.getCantidadDevuelta() <= 0) continue;

            DetalleVenta original = detallesPorProducto.get(item.getProductoId());
            if (original == null) {
                throw new BadRequestException("Producto no pertenece a esta venta");
            }

            int yaDevueltoCount = yaDevuelto.getOrDefault(item.getProductoId(), 0);
            int disponible = original.getCantidad() - yaDevueltoCount;

            if (item.getCantidadDevuelta() > disponible) {
                throw new BadRequestException(
                        "No se puede devolver más de " + disponible +
                        " unidades de " + original.getProducto().getNombre()
                );
            }

            BigDecimal subtotal = item.getPrecioUnitario().multiply(BigDecimal.valueOf(item.getCantidadDevuelta()));
            totalDevuelto = totalDevuelto.add(subtotal);

            Producto producto = productoRepository.findById(item.getProductoId())
                    .orElseThrow(() -> new ResourceNotFoundException("Producto no encontrado"));

            detalles.add(DevolucionDetalle.builder()
                    .producto(producto)
                    .cantidadDevuelta(item.getCantidadDevuelta())
                    .precioUnitario(item.getPrecioUnitario())
                    .subtotal(subtotal)
                    .build());
        }

        if (detalles.isEmpty()) {
            throw new BadRequestException("Debe devolver al menos un producto");
        }

        // 6. Create devolucion
        Devolucion devolucion = Devolucion.builder()
                .tenantId(tenantId)
                .venta(venta)
                .usuario(usuario)
                .motivo(dto.getMotivo())
                .observaciones(dto.getObservaciones())
                .totalDevuelto(totalDevuelto)
                .reponerStock(dto.isReponerStock())
                .estado("PROCESADA")
                .sucursalId(venta.getSucursalId())
                .build();

        for (DevolucionDetalle det : detalles) {
            det.setDevolucion(devolucion);
        }
        devolucion.setDetalles(detalles);
        devolucionRepository.save(devolucion);

        // 6b. Generate Nota de Credito automatically
        NotaCredito nc = notaCreditoService.generarParaDevolucion(devolucion, tenantId);
        log.info("Nota de credito {} generada para devolucion {}", nc.getCodigo(), devolucion.getId());

        // 7. Reponer stock if requested
        if (dto.isReponerStock()) {
            Long sucursalId = venta.getSucursalId();
            for (DevolucionDetalle det : detalles) {
                Producto producto = det.getProducto();
                producto.setStockActual(producto.getStockActual() + det.getCantidadDevuelta());
                productoRepository.save(producto);

                // Reponer también en producto_stock_sucursal
                if (sucursalId != null) {
                    final Producto prod = producto;
                    final int cant = det.getCantidadDevuelta();
                    sucursalRepository.findById(sucursalId).ifPresent(sucursal -> {
                        ProductoStockSucursal entry = stockSucursalRepository
                                .findByProductoIdAndSucursalId(prod.getId(), sucursal.getId())
                                .orElseGet(() -> ProductoStockSucursal.builder()
                                        .producto(prod)
                                        .sucursal(sucursal)
                                        .tenantId(tenantId)
                                        .stockActual(0)
                                        .build());
                        entry.setStockActual((entry.getStockActual() != null ? entry.getStockActual() : 0) + cant);
                        stockSucursalRepository.save(entry);
                    });
                }

                DetalleVenta dvOrigen = detallesPorProducto.get(det.getProducto().getId());
                String varDesc = (dvOrigen != null && dvOrigen.getVarianteDescripcion() != null)
                        ? " [" + dvOrigen.getVarianteDescripcion() + "]" : "";

                // Reponer stock de variante si aplica
                if (dvOrigen != null && dvOrigen.getVarianteId() != null) {
                    final Long varianteId = dvOrigen.getVarianteId();
                    final int cantDev = det.getCantidadDevuelta();
                    productoVarianteRepository.findById(varianteId).ifPresent(pv -> {
                        pv.setStockActual((pv.getStockActual() != null ? pv.getStockActual() : 0) + cantDev);
                        productoVarianteRepository.save(pv);
                    });
                    if (sucursalId != null) {
                        sucursalRepository.findById(sucursalId).ifPresent(sucursal -> {
                            ProductoVarianteStockSucursal pvss = varianteStockSucursalRepository
                                    .findByVarianteIdAndSucursalId(varianteId, sucursal.getId())
                                    .orElseGet(() -> ProductoVarianteStockSucursal.builder()
                                            .varianteId(varianteId)
                                            .sucursalId(sucursal.getId())
                                            .tenantId(tenantId)
                                            .stockActual(0)
                                            .build());
                            pvss.setStockActual((pvss.getStockActual() != null ? pvss.getStockActual() : 0) + cantDev);
                            varianteStockSucursalRepository.save(pvss);
                        });
                    }
                }
                MovimientoInventario mov = MovimientoInventario.builder()
                        .producto(producto)
                        .cantidad(det.getCantidadDevuelta())
                        .tipo("DEVOLUCION")
                        .usuario(usuario)
                        .tenantId(tenantId)
                        .sucursalId(sucursalId)
                        .descripcion("Devolución venta #" + venta.getId() + varDesc + " - " + dto.getMotivo())
                        .referencia("DEV-" + devolucion.getId())
                        .build();
                movimientoRepository.save(mov);
            }
        }

        // 8. Update venta estado
        Map<Long, Integer> nuevoYaDevuelto = new HashMap<>(yaDevuelto);
        for (DevolucionDetalle det : detalles) {
            nuevoYaDevuelto.merge(det.getProducto().getId(), det.getCantidadDevuelta(), Integer::sum);
        }

        boolean todoDevuelto = venta.getDetalles().stream()
                .allMatch(dv -> nuevoYaDevuelto.getOrDefault(dv.getProducto().getId(), 0) >= dv.getCantidad());

        venta.setEstado(todoDevuelto ? "DEVUELTA" : "DEVUELTA_PARCIAL");
        ventaRepository.save(venta);

        log.info("Devolucion {} creada para venta {} (tenant={})", devolucion.getId(), venta.getId(), tenantId);
        DevolucionDTO dto2 = toDTO(devolucion);
        dto2.setNotaCreditoCodigo(nc.getCodigo());
        dto2.setMontoNotaCredito(nc.getMontoTotal());
        dto2.setFechaVencimientoNc(nc.getFechaVencimiento());
        return dto2;
    }

    @Override
    @Transactional(readOnly = true)
    public List<DevolucionDTO> getByVenta(Long ventaId, String tenantId) {
        return devolucionRepository.findByVentaIdAndTenantId(ventaId, tenantId)
                .stream().map(this::toDTO).toList();
    }

    @Override
    @Transactional(readOnly = true)
    public List<DevolucionDTO> getAll(String tenantId) {
        return devolucionRepository.findByTenantIdOrderByFechaDevolucionDesc(tenantId)
                .stream().map(this::toDTO).toList();
    }

    private DevolucionDTO toDTO(Devolucion d) {
        return DevolucionDTO.builder()
                .id(d.getId())
                .ventaId(d.getVenta().getId())
                .tenantId(d.getTenantId())
                .usuarioNombre(d.getUsuario().getNombre())
                .motivo(d.getMotivo())
                .observaciones(d.getObservaciones())
                .totalDevuelto(d.getTotalDevuelto())
                .reponerStock(d.isReponerStock())
                .estado(d.getEstado())
                .fechaDevolucion(d.getFechaDevolucion())
                .detalles(d.getDetalles().stream().map(det -> DevolucionDetalleRespDTO.builder()
                        .productoId(det.getProducto().getId())
                        .productoNombre(det.getProducto().getNombre())
                        .cantidadDevuelta(det.getCantidadDevuelta())
                        .precioUnitario(det.getPrecioUnitario())
                        .subtotal(det.getSubtotal())
                        .build()).toList())
                .build();
    }
}
