package com.stockflow.controller;

import com.stockflow.dto.VentaDTO;
import com.stockflow.dto.DetalleVentaDTO;
import com.stockflow.entity.Venta;
import com.stockflow.entity.DetalleVenta;
import com.stockflow.entity.Producto;
import com.stockflow.entity.Usuario;
import com.stockflow.mapper.VentaMapper;
import com.stockflow.mapper.DetalleVentaMapper;
import com.stockflow.service.VentaService;
import com.stockflow.service.ProductoService;
import com.stockflow.service.UsuarioService;
import com.stockflow.util.TenantContext;
import com.stockflow.exception.BadRequestException;
import com.stockflow.exception.ResourceNotFoundException;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import jakarta.validation.Valid;
import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;
import java.util.stream.Collectors;

@Slf4j
@RestController
@RequestMapping("/ventas")
@RequiredArgsConstructor
public class VentaController {

    private final VentaService ventaService;
    private final ProductoService productoService;
    private final UsuarioService usuarioService;
    private final VentaMapper ventaMapper;
    private final DetalleVentaMapper detalleVentaMapper;

    /**
     * ✅ ACTUALIZADO: Obtiene ventas del tenant actual
     */
    @GetMapping
    public ResponseEntity<List<VentaDTO>> obtenerTodas() {
        String tenantId = TenantContext.getCurrentTenant();
        log.info("💰 Obteniendo ventas para tenant: {}", tenantId);

        return ResponseEntity.ok(
                ventaMapper.toDTOList(ventaService.obtenerVentasPorTenant(tenantId))
        );
    }

    @GetMapping("/{id}")
    public ResponseEntity<VentaDTO> obtenerPorId(@PathVariable Long id) {
        return ventaService.obtenerVentaPorId(id)
                .map(ventaMapper::toDTO)
                .map(ResponseEntity::ok)
                .orElse(ResponseEntity.notFound().build());
    }

    @GetMapping("/vendedor/{vendedorId}")
    public ResponseEntity<List<VentaDTO>> obtenerPorVendedor(@PathVariable Long vendedorId) {
        log.info("👤 Obteniendo ventas del vendedor: {}", vendedorId);
        return ResponseEntity.ok(
                ventaMapper.toDTOList(ventaService.obtenerVentasPorVendedor(vendedorId))
        );
    }

    /**
     * ✅ ACTUALIZADO: Usa tenantId automáticamente
     */
    @GetMapping("/periodo")
    public ResponseEntity<List<VentaDTO>> obtenerPorPeriodo(
            @RequestParam String inicio,
            @RequestParam String fin) {

        String tenantId = TenantContext.getCurrentTenant();
        log.info("📅 Obteniendo ventas por período para tenant: {} ({}  - {})", tenantId, inicio, fin);

        LocalDateTime inicioDateTime = LocalDateTime.parse(inicio);
        LocalDateTime finDateTime = LocalDateTime.parse(fin);

        return ResponseEntity.ok(
                ventaMapper.toDTOList(
                        ventaService.obtenerVentasPorPeriodo(tenantId, inicioDateTime, finDateTime)
                )
        );
    }

    @GetMapping("/{ventaId}/detalles")
    public ResponseEntity<List<DetalleVentaDTO>> obtenerDetalles(@PathVariable Long ventaId) {
        log.info("📋 Obteniendo detalles de venta: {}", ventaId);
        return ResponseEntity.ok(
                detalleVentaMapper.toDTOList(ventaService.obtenerDetallesVenta(ventaId))
        );
    }

    /**
     * ✅ ACTUALIZADO: Setea tenantId automáticamente
     */
    @PostMapping
    public ResponseEntity<VentaDTO> crear(@Valid @RequestBody VentaDTO ventaDTO) {
        String tenantId = TenantContext.getCurrentTenant();
        log.info("➕ Creando venta para tenant: {}", tenantId);

        // Validar que el vendedor existe
        Usuario vendedor = usuarioService.obtenerUsuarioPorId(ventaDTO.getVendedorId())
                .orElseThrow(() -> new ResourceNotFoundException("Vendedor no encontrado"));

        // Validar que hay detalles
        if (ventaDTO.getDetalles() == null || ventaDTO.getDetalles().isEmpty()) {
            throw new BadRequestException("La venta debe tener al menos un detalle");
        }

        // Setear tenantId
        ventaDTO.setTenantId(tenantId);

        // Crear detalles y validar stock
        List<DetalleVenta> detalles = ventaDTO.getDetalles().stream()
                .map(detalleDTO -> {
                    Producto producto = productoService.obtenerProductoPorId(detalleDTO.getProductoId())
                            .orElseThrow(() -> new ResourceNotFoundException("Producto no encontrado: " + detalleDTO.getProductoId()));

                    // Validar stock
                    if (producto.getStockActual() < detalleDTO.getCantidad()) {
                        throw new BadRequestException("Stock insuficiente para el producto: " + producto.getNombre());
                    }

                    return DetalleVenta.builder()
                            .producto(producto)
                            .cantidad(detalleDTO.getCantidad())
                            .precioUnitario(detalleDTO.getPrecioUnitario())
                            .subtotal(detalleDTO.getPrecioUnitario().multiply(BigDecimal.valueOf(detalleDTO.getCantidad())))
                            .build();
                })
                .collect(Collectors.toList());

        // Crear venta
        Venta venta = Venta.builder()
                .vendedor(vendedor)
                .total(ventaDTO.getTotal())
                .metodoPago(ventaDTO.getMetodoPago())
                .estado(ventaDTO.getEstado())
                .tenantId(tenantId)
                .detalles(detalles)
                .createdAt(LocalDateTime.now())
                .build();

        // Asociar venta a cada detalle
        detalles.forEach(detalle -> {
            Producto producto = detalle.getProducto();
            producto.setStockActual(producto.getStockActual() - detalle.getCantidad());
            productoService.actualizarProducto(producto.getId(), producto);
        });

        Venta ventaCreada = ventaService.crearVenta(venta);
        log.info("✅ Venta creada exitosamente: ID {}", ventaCreada.getId());

        return ResponseEntity.status(HttpStatus.CREATED)
                .body(ventaMapper.toDTO(ventaCreada));
    }

    @DeleteMapping("/{id}")
    public ResponseEntity<Void> eliminar(@PathVariable Long id) {
        log.info("🗑️ Eliminando venta ID: {}", id);
        ventaService.eliminarVenta(id);
        return ResponseEntity.noContent().build();
    }
}