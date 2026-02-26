package com.stockflow.controller;

import com.stockflow.dto.MovimientoInventarioDTO;
import com.stockflow.entity.MovimientoInventario;
import com.stockflow.entity.Producto;
import com.stockflow.entity.Usuario;
import com.stockflow.mapper.MovimientoInventarioMapper;
import com.stockflow.service.MovimientoInventarioService;
import com.stockflow.service.ProductoService;
import com.stockflow.service.UsuarioService;
import com.stockflow.util.TenantContext;
import com.stockflow.exception.ResourceNotFoundException;
import com.stockflow.exception.BadRequestException;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import jakarta.validation.Valid;
import java.time.LocalDateTime;
import java.util.List;

@Slf4j
@RestController
@RequestMapping("/movimientos-inventario")
@RequiredArgsConstructor
public class MovimientoInventarioController {

    private final MovimientoInventarioService movimientoService;
    private final ProductoService productoService;
    private final UsuarioService usuarioService;
    private final MovimientoInventarioMapper movimientoMapper;

    /**
     * ✅ ACTUALIZADO: Obtiene movimientos del tenant actual
     */
    @GetMapping
    public ResponseEntity<List<MovimientoInventarioDTO>> obtenerTodos() {
        String tenantId = TenantContext.getCurrentTenant();
        log.info("📦 Obteniendo movimientos de inventario para tenant: {}", tenantId);

        return ResponseEntity.ok(
                movimientoMapper.toDTOList(movimientoService.obtenerMovimientosPorTenant(tenantId))
        );
    }

    @GetMapping("/{id}")
    public ResponseEntity<MovimientoInventarioDTO> obtenerPorId(@PathVariable Long id) {
        return movimientoService.obtenerMovimientoPorId(id)
                .map(movimientoMapper::toDTO)
                .map(ResponseEntity::ok)
                .orElse(ResponseEntity.notFound().build());
    }

    @GetMapping("/producto/{productoId}")
    public ResponseEntity<List<MovimientoInventarioDTO>> obtenerPorProducto(@PathVariable Long productoId) {
        log.info("📦 Obteniendo movimientos del producto: {}", productoId);
        return ResponseEntity.ok(
                movimientoMapper.toDTOList(movimientoService.obtenerMovimientosPorProducto(productoId))
        );
    }

    @GetMapping("/usuario/{usuarioId}")
    public ResponseEntity<List<MovimientoInventarioDTO>> obtenerPorUsuario(@PathVariable Long usuarioId) {
        log.info("👤 Obteniendo movimientos del usuario: {}", usuarioId);
        return ResponseEntity.ok(
                movimientoMapper.toDTOList(movimientoService.obtenerMovimientosPorUsuario(usuarioId))
        );
    }

    @GetMapping("/tipo/{tipo}")
    public ResponseEntity<List<MovimientoInventarioDTO>> obtenerPorTipo(@PathVariable String tipo) {
        String tenantId = TenantContext.getCurrentTenant();
        log.info("🔍 Obteniendo movimientos tipo: {} para tenant: {}", tipo, tenantId);

        return ResponseEntity.ok(
                movimientoMapper.toDTOList(movimientoService.obtenerMovimientosPorTipoYTenant(tipo, tenantId))
        );
    }

    /**
     * ✅ ACTUALIZADO: Setea tenantId automáticamente
     */
    @PostMapping
    public ResponseEntity<MovimientoInventarioDTO> crear(@Valid @RequestBody MovimientoInventarioDTO movimientoDTO) {
        String tenantId = TenantContext.getCurrentTenant();
        log.info("➕ Creando movimiento de inventario para tenant: {}", tenantId);

        // Validar producto
        Producto producto = productoService.obtenerProductoPorId(movimientoDTO.getProductoId())
                .orElseThrow(() -> new ResourceNotFoundException("Producto no encontrado"));

        // Validar usuario
        Usuario usuario = usuarioService.obtenerUsuarioPorId(movimientoDTO.getUsuarioId())
                .orElseThrow(() -> new ResourceNotFoundException("Usuario no encontrado"));

        // Validar tipo de movimiento
        if (!movimientoDTO.getTipo().matches("ENTRADA|SALIDA|AJUSTE|DEVOLUCION")) {
            throw new BadRequestException("Tipo de movimiento inválido. Use: ENTRADA, SALIDA, AJUSTE, DEVOLUCION");
        }

        // Validar cantidad
        if (movimientoDTO.getCantidad() <= 0) {
            throw new BadRequestException("La cantidad debe ser mayor a 0");
        }

        // Validar stock para salidas
        if ("SALIDA".equals(movimientoDTO.getTipo())) {
            if (producto.getStockActual() < movimientoDTO.getCantidad()) {
                throw new BadRequestException("Stock insuficiente. Stock actual: " + producto.getStockActual());
            }
        }

        // Crear movimiento
        MovimientoInventario movimiento = MovimientoInventario.builder()
                .producto(producto)
                .usuario(usuario)
                .tipo(movimientoDTO.getTipo())
                .cantidad(movimientoDTO.getCantidad())
                .descripcion(movimientoDTO.getDescripcion())
                .tenantId(tenantId)
                .build();

        MovimientoInventario movimientoCreado = movimientoService.crearMovimiento(movimiento);

        // Actualizar stock del producto según tipo de movimiento
        int nuevoStock = producto.getStockActual();
        switch (movimientoDTO.getTipo()) {
            case "ENTRADA":
            case "DEVOLUCION":
                nuevoStock += movimientoDTO.getCantidad();
                break;
            case "SALIDA":
                nuevoStock -= movimientoDTO.getCantidad();
                break;
            case "AJUSTE":
                nuevoStock = movimientoDTO.getCantidad();
                break;
        }

        producto.setStockActual(nuevoStock);
        productoService.actualizarProducto(producto.getId(), producto);

        log.info("✅ Movimiento creado: {} - Nuevo stock: {}", movimientoDTO.getTipo(), nuevoStock);

        return ResponseEntity.status(HttpStatus.CREATED)
                .body(movimientoMapper.toDTO(movimientoCreado));
    }

    @DeleteMapping("/{id}")
    public ResponseEntity<Void> eliminar(@PathVariable Long id) {
        log.info("🗑️ Eliminando movimiento ID: {}", id);
        movimientoService.eliminarMovimiento(id);
        return ResponseEntity.noContent().build();
    }
}