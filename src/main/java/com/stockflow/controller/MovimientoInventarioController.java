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
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;
import jakarta.validation.Valid;
import java.math.BigDecimal;
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
    @PreAuthorize("hasAnyRole('ADMIN', 'GERENTE', 'GESTOR_INVENTARIO')")
    public ResponseEntity<List<MovimientoInventarioDTO>> obtenerTodos() {
        String tenantId = TenantContext.getCurrentTenant();
        log.info("📦 Obteniendo movimientos de inventario para tenant: {}", tenantId);

        return ResponseEntity.ok(
                movimientoMapper.toDTOList(movimientoService.obtenerMovimientosPorTenant(tenantId))
        );
    }

    @GetMapping("/{id}")
    @PreAuthorize("hasAnyRole('ADMIN', 'GERENTE', 'GESTOR_INVENTARIO')")
    public ResponseEntity<MovimientoInventarioDTO> obtenerPorId(@PathVariable Long id) {
        return movimientoService.obtenerMovimientoPorId(id)
                .map(movimientoMapper::toDTO)
                .map(ResponseEntity::ok)
                .orElse(ResponseEntity.notFound().build());
    }

    @GetMapping("/producto/{productoId}")
    @PreAuthorize("hasAnyRole('ADMIN', 'GERENTE', 'GESTOR_INVENTARIO')")
    public ResponseEntity<List<MovimientoInventarioDTO>> obtenerPorProducto(@PathVariable Long productoId) {
        log.info("📦 Obteniendo movimientos del producto: {}", productoId);
        return ResponseEntity.ok(
                movimientoMapper.toDTOList(movimientoService.obtenerMovimientosPorProducto(productoId))
        );
    }

    @GetMapping("/usuario/{usuarioId}")
    @PreAuthorize("hasAnyRole('ADMIN', 'GERENTE', 'GESTOR_INVENTARIO')")
    public ResponseEntity<List<MovimientoInventarioDTO>> obtenerPorUsuario(@PathVariable Long usuarioId) {
        log.info("👤 Obteniendo movimientos del usuario: {}", usuarioId);
        return ResponseEntity.ok(
                movimientoMapper.toDTOList(movimientoService.obtenerMovimientosPorUsuario(usuarioId))
        );
    }

    @GetMapping("/tipo/{tipo}")
    @PreAuthorize("hasAnyRole('ADMIN', 'GERENTE', 'GESTOR_INVENTARIO')")
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
    @PreAuthorize("hasAnyRole('ADMIN', 'GERENTE', 'GESTOR_INVENTARIO') or hasAuthority('PERM_CREAR_INVENTARIO')")
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
        boolean esEntrada = "ENTRADA".equals(movimientoDTO.getTipo());
        boolean esDevolucion = "DEVOLUCION".equals(movimientoDTO.getTipo());

        // Solo ENTRADA y DEVOLUCION pueden llevar datos de lote/proveedor/costo
        Long proveedorId = (esEntrada || esDevolucion) ? movimientoDTO.getProveedorId() : null;
        BigDecimal costoUnitario = (esEntrada || esDevolucion) ? movimientoDTO.getCostoUnitario() : null;
        String lote = (esEntrada || esDevolucion) ? movimientoDTO.getLote() : null;
        java.time.LocalDate fechaVencimiento = (esEntrada || esDevolucion) ? movimientoDTO.getFechaVencimiento() : null;

        MovimientoInventario movimiento = MovimientoInventario.builder()
                .producto(producto)
                .usuario(usuario)
                .tipo(movimientoDTO.getTipo())
                .cantidad(movimientoDTO.getCantidad())
                .descripcion(movimientoDTO.getDescripcion())
                .referencia(movimientoDTO.getReferencia())
                .tenantId(tenantId)
                .proveedorId(proveedorId)
                .costoUnitario(costoUnitario)
                .lote(lote)
                .fechaVencimiento(fechaVencimiento)
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

        // Si es ENTRADA y costoUnitario fue provisto y > 0, actualizar el último costo del producto
        if (esEntrada && costoUnitario != null && costoUnitario.compareTo(BigDecimal.ZERO) > 0) {
            producto.setCostoUnitario(costoUnitario);
        }

        productoService.actualizarProducto(producto.getId(), producto);

        log.info("✅ Movimiento creado: {} - Nuevo stock: {}", movimientoDTO.getTipo(), nuevoStock);

        return ResponseEntity.status(HttpStatus.CREATED)
                .body(movimientoMapper.toDTO(movimientoCreado));
    }

    @DeleteMapping("/{id}")
    @PreAuthorize("hasAnyRole('ADMIN', 'GERENTE') or hasAuthority('PERM_ELIMINAR_INVENTARIO')")
    public ResponseEntity<Void> eliminar(@PathVariable Long id) {
        log.info("🗑️ Eliminando movimiento ID: {}", id);
        movimientoService.eliminarMovimiento(id);
        return ResponseEntity.noContent().build();
    }
}