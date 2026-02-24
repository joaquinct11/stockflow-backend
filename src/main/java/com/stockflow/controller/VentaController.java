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
import com.stockflow.exception.BadRequestException;
import com.stockflow.exception.ResourceNotFoundException;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import jakarta.validation.Valid;
import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;
import java.util.stream.Collectors;

@RestController
@RequestMapping("/ventas")
@RequiredArgsConstructor
public class VentaController {

    private final VentaService ventaService;
    private final ProductoService productoService;
    private final UsuarioService usuarioService;
    private final VentaMapper ventaMapper;
    private final DetalleVentaMapper detalleVentaMapper;

    @GetMapping
    public ResponseEntity<List<VentaDTO>> obtenerTodas() {
        return ResponseEntity.ok(
                ventaMapper.toDTOList(ventaService.obtenerTodasVentas())
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
        return ResponseEntity.ok(
                ventaMapper.toDTOList(ventaService.obtenerVentasPorVendedor(vendedorId))
        );
    }

    @GetMapping("/tenant/{tenantId}")
    public ResponseEntity<List<VentaDTO>> obtenerPorTenant(@PathVariable String tenantId) {
        return ResponseEntity.ok(
                ventaMapper.toDTOList(ventaService.obtenerVentasPorTenant(tenantId))
        );
    }

    @GetMapping("/periodo")
    public ResponseEntity<List<VentaDTO>> obtenerPorPeriodo(
            @RequestParam String tenantId,
            @RequestParam String inicio,
            @RequestParam String fin) {

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
        return ResponseEntity.ok(
                detalleVentaMapper.toDTOList(ventaService.obtenerDetallesVenta(ventaId))
        );
    }

    @PostMapping
    public ResponseEntity<VentaDTO> crear(@Valid @RequestBody VentaDTO ventaDTO) {
        // Validar que el vendedor existe
        Usuario vendedor = usuarioService.obtenerUsuarioPorId(ventaDTO.getVendedorId())
                .orElseThrow(() -> new ResourceNotFoundException("Vendedor no encontrado"));

        // Validar que hay detalles
        if (ventaDTO.getDetalles() == null || ventaDTO.getDetalles().isEmpty()) {
            throw new BadRequestException("La venta debe tener al menos un detalle");
        }

        // Crear detalles y validar stock
        List<DetalleVenta> detalles = ventaDTO.getDetalles().stream()
                .map(detalleDTO -> {
                    Producto producto = productoService.obtenerProductoPorId(detalleDTO.getProductoId())
                            .orElseThrow(() -> new ResourceNotFoundException("Producto no encontrado"));

                    // Validar stock
                    if (producto.getStockActual() < detalleDTO.getCantidad()) {
                        throw new BadRequestException(
                                "Stock insuficiente para " + producto.getNombre() +
                                        ". Disponible: " + producto.getStockActual()
                        );
                    }

                    // Calcular subtotal
                    BigDecimal subtotal = detalleDTO.getPrecioUnitario()
                            .multiply(new BigDecimal(detalleDTO.getCantidad()));

                    return DetalleVenta.builder()
                            .producto(producto)
                            .cantidad(detalleDTO.getCantidad())
                            .precioUnitario(detalleDTO.getPrecioUnitario())
                            .subtotal(subtotal)
                            .build();
                })
                .collect(Collectors.toList());

        // Calcular total
        BigDecimal total = detalles.stream()
                .map(DetalleVenta::getSubtotal)
                .reduce(BigDecimal.ZERO, BigDecimal::add);

        // Crear venta
        Venta venta = Venta.builder()
                .vendedor(vendedor)
                .total(total)
                .metodoPago(ventaDTO.getMetodoPago() != null ? ventaDTO.getMetodoPago() : "EFECTIVO")
                .estado("COMPLETADA")
                .tenantId(ventaDTO.getTenantId())
                .detalles(detalles)
                .createdAt(LocalDateTime.now())
                .build();

        Venta ventaCreada = ventaService.crearVenta(venta);

        // Descontar stock de productos
        detalles.forEach(detalle -> {
            Producto producto = detalle.getProducto();
            producto.setStockActual(producto.getStockActual() - detalle.getCantidad());
            productoService.actualizarProducto(producto.getId(), producto);
        });

        return ResponseEntity.status(HttpStatus.CREATED)
                .body(ventaMapper.toDTO(ventaCreada));
    }

    @DeleteMapping("/{id}")
    public ResponseEntity<Void> eliminar(@PathVariable Long id) {
        ventaService.eliminarVenta(id);
        return ResponseEntity.noContent().build();
    }
}