package com.stockflow.controller;

import com.stockflow.dto.LoteVencimientoDTO;
import com.stockflow.dto.MovimientoInventarioDTO;
import com.stockflow.entity.MovimientoInventario;
import com.stockflow.entity.Producto;
import com.stockflow.entity.ProductoStockSucursal;
import com.stockflow.entity.ProductoVarianteStockSucursal;
import com.stockflow.entity.Sucursal;
import com.stockflow.entity.Usuario;
import com.stockflow.mapper.MovimientoInventarioMapper;
import com.stockflow.repository.MovimientoInventarioRepository;
import com.stockflow.repository.ProductoStockSucursalRepository;
import com.stockflow.repository.ProductoVarianteRepository;
import com.stockflow.repository.ProductoVarianteStockSucursalRepository;
import com.stockflow.repository.SucursalRepository;
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
    private final ProductoVarianteRepository                 productoVarianteRepository;
    private final ProductoVarianteStockSucursalRepository   varianteStockSucursalRepository;
    private final ProductoStockSucursalRepository           stockSucursalRepository;
    private final SucursalRepository                        sucursalRepository;
    private final com.stockflow.service.StockLoteService   stockLoteService;

    /**
     * ✅ ACTUALIZADO: Obtiene movimientos del tenant actual
     */
    @GetMapping
    @PreAuthorize("hasRole('ADMIN') or hasAuthority('PERM_VER_INVENTARIO')")
    public ResponseEntity<List<MovimientoInventarioDTO>> obtenerTodos(@RequestParam(required = false) Long sucursalId) {
        String tenantId = TenantContext.getCurrentTenant();
        log.info("📦 Obteniendo movimientos de inventario para tenant: {}", tenantId);

        return ResponseEntity.ok(
                movimientoMapper.toDTOList(movimientoService.obtenerMovimientosPorTenant(tenantId, sucursalId))
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
    public ResponseEntity<List<MovimientoInventarioDTO>> obtenerPorProducto(
            @PathVariable Long productoId,
            @RequestParam(required = false) Long sucursalId) {
        String tenantId = TenantContext.getCurrentTenant();
        log.info("📦 Obteniendo movimientos del producto: {} sucursalId={} para tenant: {}", productoId, sucursalId, tenantId);
        productoService.obtenerProductoPorId(productoId)
                .filter(p -> tenantId.equals(p.getTenantId()))
                .orElseThrow(() -> new ResourceNotFoundException("Producto no encontrado"));
        List<MovimientoInventario> movimientos = sucursalId != null
                ? movimientoRepository.findByProductoIdAndSucursalId(productoId, sucursalId)
                : movimientoService.obtenerMovimientosPorProducto(productoId);
        return ResponseEntity.ok(movimientoMapper.toDTOList(movimientos));
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
        if (!movimientoDTO.getTipo().matches("ENTRADA|SALIDA|AJUSTE|AJUSTE_PRECIO|DEVOLUCION")) {
            throw new BadRequestException("Tipo de movimiento inválido.");
        }

        // AJUSTE_PRECIO no requiere cantidad; los demás sí
        if (!"AJUSTE_PRECIO".equals(movimientoDTO.getTipo()) && movimientoDTO.getCantidad() <= 0) {
            throw new BadRequestException("La cantidad debe ser mayor a 0");
        }

        // Validar stock para salidas
        if ("SALIDA".equals(movimientoDTO.getTipo())) {
            if (producto.getStockActual() < movimientoDTO.getCantidad()) {
                throw new BadRequestException("Stock insuficiente. Stock actual: " + producto.getStockActual());
            }
        }

        // Crear movimiento
        boolean esEntrada      = "ENTRADA".equals(movimientoDTO.getTipo());
        boolean esDevolucion   = "DEVOLUCION".equals(movimientoDTO.getTipo());
        boolean esAjuste       = "AJUSTE".equals(movimientoDTO.getTipo());
        boolean esAjustePrecio = "AJUSTE_PRECIO".equals(movimientoDTO.getTipo());

        // Solo ENTRADA y DEVOLUCION llevan lote/proveedor
        Long proveedorId = (esEntrada || esDevolucion) ? movimientoDTO.getProveedorId() : null;
        // ENTRADA, DEVOLUCION, AJUSTE y AJUSTE_PRECIO pueden actualizar precios
        BigDecimal costoUnitario = (esEntrada || esDevolucion || esAjuste || esAjustePrecio) ? movimientoDTO.getCostoUnitario() : null;
        BigDecimal precioVenta   = (esEntrada || esAjuste || esAjustePrecio)                  ? movimientoDTO.getPrecioVenta()   : null;
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
                .cantidad(esAjustePrecio ? 0 : movimientoDTO.getCantidad())
                .descripcion(descripcionFinal)
                .referencia(movimientoDTO.getReferencia())
                .tenantId(tenantId)
                .proveedorId(proveedorId)
                .costoUnitario(costoUnitario)
                .precioVenta(precioVenta)
                .lote(lote)
                .fechaVencimiento(fechaVencimiento)
                .registroSanitario(registroSanitario)
                .sucursalId(movimientoDTO.getSucursalId())
                .build();

        MovimientoInventario movimientoCreado = movimientoService.crearMovimiento(movimiento);

        // Si es ENTRADA o DEVOLUCIÓN con fechaVencimiento, registrar en stock_lotes para control FEFO
        if ((esEntrada || esDevolucion) && fechaVencimiento != null) {
            stockLoteService.registrarLote(
                    tenantId, movimientoCreado.getId(), producto.getId(),
                    movimientoDTO.getSucursalId(), lote,
                    fechaVencimiento, movimientoDTO.getCantidad());
        }

        // Guardar stock previo para calcular delta en ajustes FEFO
        final int stockPrevio = producto.getStockActual();

        // Actualizar stock (AJUSTE_PRECIO no toca el stock)
        if (!esAjustePrecio) {
            if (movimientoDTO.getVarianteId() != null) {
                productoVarianteRepository.findByIdAndTenantId(movimientoDTO.getVarianteId(), tenantId)
                        .ifPresentOrElse(variante -> {
                            Long sucursalId = movimientoDTO.getSucursalId();

                            if (sucursalId != null) {
                                // ── Plan PRO: actualizar stock por sucursal ──────────────────
                                ProductoVarianteStockSucursal entry = varianteStockSucursalRepository
                                        .findByVarianteIdAndSucursalId(variante.getId(), sucursalId)
                                        .orElseGet(() -> ProductoVarianteStockSucursal.builder()
                                                .varianteId(variante.getId())
                                                .sucursalId(sucursalId)
                                                .tenantId(tenantId)
                                                .stockActual(0)
                                                .stockMinimo(variante.getStockMinimo() != null ? variante.getStockMinimo() : 0)
                                                .build());
                                int sv = entry.getStockActual() != null ? entry.getStockActual() : 0;
                                switch (movimientoDTO.getTipo()) {
                                    case "ENTRADA": case "DEVOLUCION": sv += movimientoDTO.getCantidad(); break;
                                    case "SALIDA":  sv -= movimientoDTO.getCantidad(); break;
                                    case "AJUSTE":  sv  = movimientoDTO.getCantidad(); break;
                                }
                                entry.setStockActual(sv);
                                varianteStockSucursalRepository.save(entry);
                                log.info("📍 Stock variante {} sucursal {} actualizado a {}", variante.getId(), sucursalId, sv);

                                // El stock global de la variante = suma de todos los locales
                                int totalVariante = varianteStockSucursalRepository
                                        .sumStockByVarianteIdAndTenantId(variante.getId(), tenantId);
                                variante.setStockActual(totalVariante);
                            } else {
                                // ── Plan BÁSICO: actualizar stock global directamente ────────
                                int sv = variante.getStockActual() != null ? variante.getStockActual() : 0;
                                switch (movimientoDTO.getTipo()) {
                                    case "ENTRADA": case "DEVOLUCION": sv += movimientoDTO.getCantidad(); break;
                                    case "SALIDA":  sv -= movimientoDTO.getCantidad(); break;
                                    case "AJUSTE":  sv  = movimientoDTO.getCantidad(); break;
                                }
                                variante.setStockActual(sv);
                            }

                            productoVarianteRepository.save(variante);

                            // producto.stockActual = suma de todas las variantes activas
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

                // Actualizar también el stock por sucursal si viene informado
                if (movimientoDTO.getSucursalId() != null) {
                    final int stockFinal = nuevoStock;
                    sucursalRepository.findById(movimientoDTO.getSucursalId()).ifPresent(sucursal -> {
                        ProductoStockSucursal entry = stockSucursalRepository
                                .findByProductoIdAndSucursalId(producto.getId(), sucursal.getId())
                                .orElseGet(() -> ProductoStockSucursal.builder()
                                        .producto(producto)
                                        .sucursal(sucursal)
                                        .tenantId(tenantId)
                                        .stockActual(0)
                                        .build());
                        int stockSuc = entry.getStockActual() != null ? entry.getStockActual() : 0;
                        switch (movimientoDTO.getTipo()) {
                            case "ENTRADA": case "DEVOLUCION": stockSuc += movimientoDTO.getCantidad(); break;
                            case "SALIDA":  stockSuc -= movimientoDTO.getCantidad(); break;
                            case "AJUSTE":  stockSuc  = movimientoDTO.getCantidad(); break;
                        }
                        entry.setStockActual(stockSuc);
                        stockSucursalRepository.save(entry);
                        log.info("📍 Stock sucursal {} actualizado a {} para producto {}", sucursal.getId(), stockSuc, producto.getId());
                    });
                }
            }
        }

        // Si es SALIDA manual, descontar de stock_lotes en orden FEFO
        if ("SALIDA".equals(movimientoDTO.getTipo()) && movimientoDTO.getVarianteId() == null) {
            try {
                stockLoteService.descontarFefo(tenantId, producto.getId(),
                        movimientoDTO.getSucursalId(), movimientoDTO.getCantidad());
            } catch (Exception e) {
                log.warn("⚠️ No se pudo descontar FEFO en SALIDA manual: {}", e.getMessage());
            }
        }

        // Si es AJUSTE, sincronizar stock_lotes
        if (esAjuste && movimientoDTO.getVarianteId() == null) {
            try {
                if (movimientoDTO.getAjusteLoteMovimientoId() != null) {
                    // Obtener nombre del lote para mostrarlo en el Kardex
                    String nombreLote = movimientoRepository.findById(movimientoDTO.getAjusteLoteMovimientoId())
                            .map(m -> m.getLote() != null ? m.getLote() : "Lote #" + movimientoDTO.getAjusteLoteMovimientoId())
                            .orElse("Lote #" + movimientoDTO.getAjusteLoteMovimientoId());

                    // Ajustar el lote y calcular el nuevo total del producto
                    int deltaLote = stockLoteService.ajustarLoteEspecifico(
                            movimientoDTO.getAjusteLoteMovimientoId(), movimientoDTO.getCantidad());
                    int nuevoTotalProducto = stockPrevio + deltaLote;
                    producto.setStockActual(nuevoTotalProducto);

                    // Actualizar el movimiento: cantidad = total producto, descripción = lote ajustado
                    movimientoCreado.setCantidad(nuevoTotalProducto);
                    movimientoCreado.setDescripcion("Lote: " + nombreLote
                            + " | " + movimientoDTO.getCantidad() + " uds"
                            + (movimientoDTO.getDescripcion() != null && !movimientoDTO.getDescripcion().isBlank()
                                ? " | " + movimientoDTO.getDescripcion() : ""));
                    movimientoRepository.save(movimientoCreado);

                    // Corregir stock de sucursal (el AJUSTE genérico lo puso en getCantidad(); el correcto es el total del producto)
                    if (movimientoDTO.getSucursalId() != null) {
                        final int totalFinal = nuevoTotalProducto;
                        stockSucursalRepository
                                .findByProductoIdAndSucursalId(producto.getId(), movimientoDTO.getSucursalId())
                                .ifPresent(entry -> {
                                    entry.setStockActual(totalFinal);
                                    stockSucursalRepository.save(entry);
                                });
                    }
                } else {
                    // Sin lote seleccionado: auto-distribuir el delta en orden FEFO
                    int delta = movimientoDTO.getCantidad() - stockPrevio;
                    stockLoteService.ajustarStockLotes(tenantId, producto.getId(),
                            movimientoDTO.getSucursalId(), delta);
                }
            } catch (Exception e) {
                log.warn("⚠️ No se pudo ajustar stock_lotes: {}", e.getMessage());
            }
        }

        // Actualizar precios si vienen informados
        if (costoUnitario != null && costoUnitario.compareTo(BigDecimal.ZERO) > 0) {
            producto.setCostoUnitario(costoUnitario);
        }
        if (precioVenta != null && precioVenta.compareTo(BigDecimal.ZERO) > 0) {
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

        List<MovimientoInventario> movimientos = movimientoRepository
                .findEntradasConVencimientoPorTenant(tenantId);

        // Obtener stock real por lote desde stock_lotes (una sola query batch)
        List<Long> movIds = movimientos.stream().map(MovimientoInventario::getId).collect(Collectors.toList());
        java.util.Map<Long, Integer> stockPorMovimiento = stockLoteService.getStockPorMovimientoIds(movIds);

        List<LoteVencimientoDTO> lotes = movimientos.stream()
                .map(m -> LoteVencimientoDTO.builder()
                        .movimientoId(m.getId())
                        .productoId(m.getProducto().getId())
                        .productoNombre(m.getProducto().getNombre())
                        .codigoBarras(m.getProducto().getCodigoBarras())
                        .lote(m.getLote())
                        .fechaVencimiento(m.getFechaVencimiento())
                        .cantidad(m.getCantidad())
                        // Stock real del lote específico (no el total del producto)
                        .stockActual(stockPorMovimiento.getOrDefault(m.getId(), 0))
                        .diasRestantes(ChronoUnit.DAYS.between(hoy, m.getFechaVencimiento()))
                        .registroSanitario(m.getRegistroSanitario())
                        .build())
                .collect(Collectors.toList());

        return ResponseEntity.ok(lotes);
    }

    /**
     * Devuelve los lotes vigentes de un producto específico — para el selector de lote en ajustes.
     */
    @GetMapping("/lotes/producto/{productoId}")
    @PreAuthorize("hasRole('ADMIN') or hasAuthority('PERM_VER_INVENTARIO')")
    public ResponseEntity<List<LoteVencimientoDTO>> getLotesPorProducto(@PathVariable Long productoId) {
        String tenantId = TenantContext.getCurrentTenant();
        LocalDate hoy = LocalDate.now();

        List<MovimientoInventario> movimientos = movimientoRepository
                .findEntradasConVencimientoPorTenant(tenantId)
                .stream()
                .filter(m -> m.getProducto().getId().equals(productoId))
                .collect(Collectors.toList());

        List<Long> movIds = movimientos.stream().map(MovimientoInventario::getId).collect(Collectors.toList());
        java.util.Map<Long, Integer> stockPorMovimiento = stockLoteService.getStockPorMovimientoIds(movIds);

        List<LoteVencimientoDTO> lotes = movimientos.stream()
                .map(m -> LoteVencimientoDTO.builder()
                        .movimientoId(m.getId())
                        .productoId(m.getProducto().getId())
                        .productoNombre(m.getProducto().getNombre())
                        .lote(m.getLote())
                        .fechaVencimiento(m.getFechaVencimiento())
                        .cantidad(m.getCantidad())
                        .stockActual(stockPorMovimiento.getOrDefault(m.getId(), 0))
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