package com.stockflow.controller;

import com.stockflow.dto.LoteVencimientoDTO;
import com.stockflow.dto.MovimientoInventarioDTO;
import com.stockflow.entity.MovimientoInventario;
import com.stockflow.entity.Producto;
import com.stockflow.entity.Usuario;
import com.stockflow.mapper.MovimientoInventarioMapper;
import com.stockflow.repository.MovimientoInventarioRepository;
import com.stockflow.repository.ProductoVarianteRepository;
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
import java.time.LocalDate;
import java.time.temporal.ChronoUnit;
import java.util.List;
import java.util.stream.Collectors;

@Slf4j
@RestController
@RequestMapping("/movimientos-inventario")
@RequiredArgsConstructor
public class MovimientoInventarioController {

    private final MovimientoInventarioService        movimientoService;
    private final MovimientoInventarioRepository     movimientoRepository;
    private final ProductoService                    productoService;
    private final UsuarioService                     usuarioService;
    private final MovimientoInventarioMapper         movimientoMapper;
    private final ProductoVarianteRepository         productoVarianteRepository;

    /**
     * ✅ ACTUALIZADO: Obtiene movimientos del tenant actual
     */
    @GetMapping
    @PreAuthorize("hasRole('ADMIN') or hasAuthority('PERM_VER_INVENTARIO')")
    public ResponseEntity<List<MovimientoInventarioDTO>> obtenerTodos() {
        String tenantId = TenantContext.getCurrentTenant();
        log.info("📦 Obteniendo movimientos de inventario para tenant: {}", tenantId);

        return ResponseEntity.ok(
                movimientoMapper.toDTOList(movimientoService.obtenerMovimientosPorTenant(tenantId))
        );
    }

    @GetMapping("/{id}")
    @PreAuthorize("hasRole('ADMIN') or hasAuthority('PERM_VER_DETALLE_INVENTARIO')")
    public ResponseEntity<MovimientoInventarioDTO> obtenerPorId(@PathVariable Long id) {
        String tenantId = TenantContext.getCurrentTenant();
        return movimientoService.obtenerMovimientoPorId(id)
                .filter(m -> tenantId.equals(m.getTenantId()))
                .map(movimientoMapper::toDTO)
                .map(ResponseEntity::ok)
                .orElse(ResponseEntity.notFound().build());
    }

    @GetMapping("/producto/{productoId}")
    @PreAuthorize("hasRole('ADMIN') or hasAuthority('PERM_VER_INVENTARIO')")
    public ResponseEntity<List<MovimientoInventarioDTO>> obtenerPorProducto(@PathVariable Long productoId) {
        String tenantId = TenantContext.getCurrentTenant();
        log.info("📦 Obteniendo movimientos del producto: {} para tenant: {}", productoId, tenantId);
        // Verificar que el producto pertenece al tenant antes de exponer sus movimientos
        productoService.obtenerProductoPorId(productoId)
                .filter(p -> tenantId.equals(p.getTenantId()))
                .orElseThrow(() -> new ResourceNotFoundException("Producto no encontrado"));
        return ResponseEntity.ok(
                movimientoMapper.toDTOList(movimientoService.obtenerMovimientosPorProducto(productoId))
        );
    }

    @GetMapping("/usuario/{usuarioId}")
    @PreAuthorize("hasRole('ADMIN') or hasAuthority('PERM_VER_INVENTARIO')")
    public ResponseEntity<List<MovimientoInventarioDTO>> obtenerPorUsuario(@PathVariable Long usuarioId) {
        String tenantId = TenantContext.getCurrentTenant();
        log.info("👤 Obteniendo movimientos del usuario: {} para tenant: {}", usuarioId, tenantId);
        // Verificar que el usuario pertenece al tenant antes de exponer sus movimientos
        usuarioService.obtenerUsuarioPorId(usuarioId)
                .filter(u -> tenantId.equals(u.getTenantId()))
                .orElseThrow(() -> new ResourceNotFoundException("Usuario no encontrado"));
        return ResponseEntity.ok(
                movimientoMapper.toDTOList(movimientoService.obtenerMovimientosPorUsuario(usuarioId))
        );
    }

    @GetMapping("/tipo/{tipo}")
    @PreAuthorize("hasRole('ADMIN') or hasAuthority('PERM_VER_INVENTARIO')")
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
    @PreAuthorize("hasAnyRole('ADMIN', 'GESTOR_INVENTARIO') or hasAuthority('PERM_CREAR_INVENTARIO')")
    public ResponseEntity<MovimientoInventarioDTO> crear(@Valid @RequestBody MovimientoInventarioDTO movimientoDTO) {
        String tenantId = TenantContext.getCurrentTenant();
        log.info("➕ Creando movimiento de inventario para tenant: {}", tenantId);

        // Validar producto y que pertenece al tenant
        Producto producto = productoService.obtenerProductoPorId(movimientoDTO.getProductoId())
                .filter(p -> tenantId.equals(p.getTenantId()))
                .orElseThrow(() -> new ResourceNotFoundException("Producto no encontrado"));

        // Validar usuario y que pertenece al tenant
        Usuario usuario = usuarioService.obtenerUsuarioPorId(movimientoDTO.getUsuarioId())
                .filter(u -> tenantId.equals(u.getTenantId()))
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
        BigDecimal precioVenta = esEntrada ? movimientoDTO.getPrecioVenta() : null;
        String lote = (esEntrada || esDevolucion) ? movimientoDTO.getLote() : null;
        java.time.LocalDate fechaVencimiento = (esEntrada || esDevolucion) ? movimientoDTO.getFechaVencimiento() : null;
        String registroSanitario = esEntrada ? movimientoDTO.getRegistroSanitario() : null;

        // Si viene varianteId, incluir talla/color en la descripción para que sea visible en el Kardex
        String descripcionFinal = movimientoDTO.getDescripcion() != null ? movimientoDTO.getDescripcion() : "";
        if (movimientoDTO.getVarianteId() != null) {
            String varDesc = productoVarianteRepository
                    .findByIdAndTenantId(movimientoDTO.getVarianteId(), tenantId)
                    .map(v -> {
                        java.util.List<String> parts = new java.util.ArrayList<>();
                        if (v.getTalla() != null && !v.getTalla().isBlank()) parts.add(v.getTalla());
                        if (v.getColor() != null && !v.getColor().isBlank()) parts.add(v.getColor());
                        return parts.isEmpty() ? "" : " [" + String.join(" / ", parts) + "]";
                    }).orElse("");
            descripcionFinal = descripcionFinal + varDesc;
        }

        MovimientoInventario movimiento = MovimientoInventario.builder()
                .producto(producto)
                .usuario(usuario)
                .tipo(movimientoDTO.getTipo())
                .cantidad(movimientoDTO.getCantidad())
                .descripcion(descripcionFinal)
                .referencia(movimientoDTO.getReferencia())
                .tenantId(tenantId)
                .proveedorId(proveedorId)
                .costoUnitario(costoUnitario)
                .precioVenta(precioVenta)
                .lote(lote)
                .fechaVencimiento(fechaVencimiento)
                .registroSanitario(registroSanitario)
                .build();

        MovimientoInventario movimientoCreado = movimientoService.crearMovimiento(movimiento);

        // Actualizar stock: si hay varianteId → afectar variante y re-sincronizar producto padre
        if (movimientoDTO.getVarianteId() != null) {
            productoVarianteRepository.findByIdAndTenantId(movimientoDTO.getVarianteId(), tenantId)
                    .ifPresentOrElse(variante -> {
                        int sv = variante.getStockActual() != null ? variante.getStockActual() : 0;
                        switch (movimientoDTO.getTipo()) {
                            case "ENTRADA": case "DEVOLUCION": sv += movimientoDTO.getCantidad(); break;
                            case "SALIDA":  sv -= movimientoDTO.getCantidad(); break;
                            case "AJUSTE":  sv  = movimientoDTO.getCantidad(); break;
                        }
                        variante.setStockActual(sv);
                        productoVarianteRepository.save(variante);
                        int totalPadre = productoVarianteRepository
                                .findByProductoIdAndActivoTrueAndTenantId(producto.getId(), tenantId)
                                .stream().mapToInt(v -> v.getStockActual() != null ? v.getStockActual() : 0).sum();
                        producto.setStockActual(totalPadre);
                    }, () -> { throw new ResourceNotFoundException("Variante no encontrada"); });
        } else {
            int nuevoStock = producto.getStockActual();
            switch (movimientoDTO.getTipo()) {
                case "ENTRADA": case "DEVOLUCION": nuevoStock += movimientoDTO.getCantidad(); break;
                case "SALIDA":  nuevoStock -= movimientoDTO.getCantidad(); break;
                case "AJUSTE":  nuevoStock  = movimientoDTO.getCantidad(); break;
            }
            producto.setStockActual(nuevoStock);
        }

        if (esEntrada && costoUnitario != null && costoUnitario.compareTo(BigDecimal.ZERO) > 0) {
            producto.setCostoUnitario(costoUnitario);
        }
        if (esEntrada && precioVenta != null && precioVenta.compareTo(BigDecimal.ZERO) > 0) {
            producto.setPrecioVenta(precioVenta);
        }

        productoService.actualizarProducto(producto.getId(), producto);

        log.info("✅ Movimiento creado: {} - Nuevo stock: {}", movimientoDTO.getTipo(), producto.getStockActual());

        return ResponseEntity.status(HttpStatus.CREATED)
                .body(movimientoMapper.toDTO(movimientoCreado));
    }

    /**
     * Devuelve todos los lotes con fecha de vencimiento del tenant,
     * ordenados de más próximo a vencer al más lejano.
     * diasRestantes es negativo cuando el lote ya está vencido.
     */
    @GetMapping("/lotes")
    @PreAuthorize("hasRole('ADMIN') or hasAuthority('PERM_VER_INVENTARIO')")
    public ResponseEntity<List<LoteVencimientoDTO>> getLotes() {
        String tenantId = TenantContext.getCurrentTenant();
        LocalDate hoy = LocalDate.now();

        List<LoteVencimientoDTO> lotes = movimientoRepository
                .findEntradasConVencimientoPorTenant(tenantId)
                .stream()
                .map(m -> LoteVencimientoDTO.builder()
                        .movimientoId(m.getId())
                        .productoId(m.getProducto().getId())
                        .productoNombre(m.getProducto().getNombre())
                        .codigoBarras(m.getProducto().getCodigoBarras())
                        .lote(m.getLote())
                        .fechaVencimiento(m.getFechaVencimiento())
                        .cantidad(m.getCantidad())
                        .diasRestantes(ChronoUnit.DAYS.between(hoy, m.getFechaVencimiento()))
                        .registroSanitario(m.getRegistroSanitario())
                        .build())
                .collect(Collectors.toList());

        return ResponseEntity.ok(lotes);
    }

    @DeleteMapping("/{id}")
    @PreAuthorize("hasRole('ADMIN') or hasAuthority('PERM_ELIMINAR_INVENTARIO')")
    public ResponseEntity<Void> eliminar(@PathVariable Long id) {
        String tenantId = TenantContext.getCurrentTenant();
        log.info("🗑️ Eliminando movimiento ID: {}", id);
        movimientoService.obtenerMovimientoPorId(id)
                .filter(m -> tenantId.equals(m.getTenantId()))
                .ifPresent(m -> movimientoService.eliminarMovimiento(id));
        return ResponseEntity.noContent().build();
    }
}