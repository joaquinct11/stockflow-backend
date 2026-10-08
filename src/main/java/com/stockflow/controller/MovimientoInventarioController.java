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
import com.stockflow.entity.UsuarioTenant;
import com.stockflow.repository.DetalleVentaRepository;
import com.stockflow.repository.MovimientoInventarioRepository;
import com.stockflow.repository.ProductoStockSucursalRepository;
import com.stockflow.repository.ProductoVarianteRepository;
import com.stockflow.repository.ProductoVarianteStockSucursalRepository;
import com.stockflow.repository.ProductoRepository;
import com.stockflow.repository.ProveedorRepository;
import com.stockflow.repository.SucursalRepository;
import com.stockflow.repository.StockLoteRepository;
import com.stockflow.repository.UsuarioTenantRepository;
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
import java.util.Map;
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
    private final DetalleVentaRepository                    detalleVentaRepository;
    private final StockLoteRepository                       stockLoteRepository;
    private final ProveedorRepository                       proveedorRepository;
    private final ProductoRepository                        productoRepository;
    private final UsuarioTenantRepository                    usuarioTenantRepository;

    /**
     * ✅ ACTUALIZADO: Obtiene movimientos del tenant actual
     */
    @GetMapping("/proximos-vencer")
    @PreAuthorize("hasAnyRole('ADMIN', 'VENDEDOR') or hasAuthority('PERM_VER_INVENTARIO')")
    public ResponseEntity<List<MovimientoInventarioDTO>> obtenerProximosAVencer(
            @RequestParam(defaultValue = "90") int ventanaDias) {
        String tenantId = TenantContext.getCurrentTenant();
        java.time.LocalDate hoy   = java.time.LocalDate.now();
        java.time.LocalDate hasta = hoy.plusDays(ventanaDias);
        List<com.stockflow.entity.MovimientoInventario> movimientos =
                movimientoRepository.findEntradasConVencimientoEnRango(tenantId, hoy, hasta);
        return ResponseEntity.ok(movimientoMapper.toDTOList(movimientos));
    }

    @GetMapping
    @PreAuthorize("hasRole('ADMIN') or hasAuthority('PERM_VER_INVENTARIO')")
    public ResponseEntity<List<MovimientoInventarioDTO>> obtenerTodos(
            @RequestParam(required = false) Long sucursalId,
            @RequestParam(required = false) Integer dias) {
        String tenantId = TenantContext.getCurrentTenant();
        log.info("📦 Obteniendo movimientos de inventario para tenant: {} dias={}", tenantId, dias);

        if (dias != null) {
            java.time.LocalDateTime desde = java.time.LocalDateTime.now().minusDays(dias);
            List<com.stockflow.entity.MovimientoInventario> recientes = sucursalId != null
                    ? movimientoRepository.findRecentByTenantIdAndSucursalId(tenantId, sucursalId, desde)
                    : movimientoRepository.findRecentByTenantId(tenantId, desde);
            return ResponseEntity.ok(movimientoMapper.toDTOList(recientes));
        }

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
            @RequestParam(required = false) Long sucursalId,
            @RequestParam(required = false) @org.springframework.format.annotation.DateTimeFormat(iso = org.springframework.format.annotation.DateTimeFormat.ISO.DATE_TIME) java.time.LocalDateTime desde,
            @RequestParam(required = false) @org.springframework.format.annotation.DateTimeFormat(iso = org.springframework.format.annotation.DateTimeFormat.ISO.DATE_TIME) java.time.LocalDateTime hasta) {
        String tenantId = TenantContext.getCurrentTenant();
        log.info("📦 Obteniendo movimientos del producto: {} sucursalId={} desde={} hasta={} tenant={}", productoId, sucursalId, desde, hasta, tenantId);
        productoService.obtenerProductoPorId(productoId)
                .filter(p -> tenantId.equals(p.getTenantId()))
                .orElseThrow(() -> new ResourceNotFoundException("Producto no encontrado"));
        List<MovimientoInventario> movimientos;
        if (desde != null && hasta != null) {
            movimientos = sucursalId != null
                    ? movimientoRepository.findByProductoIdAndSucursalIdAndTenantIdBetween(productoId, sucursalId, tenantId, desde, hasta)
                    : movimientoRepository.findByProductoIdAndTenantIdBetween(productoId, tenantId, desde, hasta);
        } else {
            movimientos = sucursalId != null
                    ? movimientoRepository.findByProductoIdAndSucursalIdAndTenantId(productoId, sucursalId, tenantId)
                    : movimientoRepository.findByProductoIdAndTenantId(productoId, tenantId);
        }
        return ResponseEntity.ok(movimientoMapper.toDTOList(movimientos));
    }

    @GetMapping("/usuario/{usuarioId}")
    @PreAuthorize("hasRole('ADMIN') or hasAuthority('PERM_VER_INVENTARIO')")
    public ResponseEntity<List<MovimientoInventarioDTO>> obtenerPorUsuario(@PathVariable Long usuarioId) {
        String tenantId = TenantContext.getCurrentTenant();
        log.info("👤 Obteniendo movimientos del usuario: {} para tenant: {}", usuarioId, tenantId);
        // Verificar que el usuario pertenece al tenant antes de exponer sus movimientos
        usuarioTenantRepository.findByUsuarioIdAndTenantIdAndActivoTrue(usuarioId, tenantId)
                .orElseThrow(() -> new ResourceNotFoundException("Usuario no encontrado"));
        return ResponseEntity.ok(
                movimientoMapper.toDTOList(movimientoService.obtenerMovimientosPorUsuario(usuarioId, tenantId))
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
    @org.springframework.transaction.annotation.Transactional
    public ResponseEntity<MovimientoInventarioDTO> crear(@Valid @RequestBody MovimientoInventarioDTO movimientoDTO) {
        String tenantId = TenantContext.getCurrentTenant();
        log.info("➕ Creando movimiento de inventario para tenant: {}", tenantId);

        // Validar producto y que pertenece al tenant
        Producto producto = productoService.obtenerProductoPorId(movimientoDTO.getProductoId())
                .filter(p -> tenantId.equals(p.getTenantId()))
                .orElseThrow(() -> new ResourceNotFoundException("Producto no encontrado"));

        // Validar usuario y que pertenece al tenant
        UsuarioTenant usuarioTenant = usuarioTenantRepository
                .findByUsuarioIdAndTenantIdAndActivoTrue(movimientoDTO.getUsuarioId(), tenantId)
                .orElseThrow(() -> new ResourceNotFoundException("Usuario no encontrado"));
        Usuario usuario = usuarioTenant.getUsuario();

        // Validar tipo de movimiento
        if (!movimientoDTO.getTipo().matches("ENTRADA|SALIDA|AJUSTE|AJUSTE_PRECIO|DEVOLUCION|MERMA")) {
            throw new BadRequestException("Tipo de movimiento inválido.");
        }

        // AJUSTE y AJUSTE_PRECIO pueden recibir cantidad 0; los demás requieren > 0
        if (!movimientoDTO.getTipo().matches("AJUSTE|AJUSTE_PRECIO") && movimientoDTO.getCantidad() <= 0) {
            throw new BadRequestException("La cantidad debe ser mayor a 0");
        }

        // Validar stock para salidas y mermas
        if ("SALIDA".equals(movimientoDTO.getTipo()) || "MERMA".equals(movimientoDTO.getTipo())) {
            boolean tieneLotesVal = stockLoteRepository.existsByProductoIdAndTenantId(producto.getId(), tenantId);
            int stockDisponible;
            if (tieneLotesVal) {
                // Para productos con lotes: validar solo contra stockVigente (sin vencidos)
                stockDisponible = movimientoDTO.getSucursalId() != null
                        ? stockLoteRepository.sumStockVigenteConSucursal(
                                producto.getId(), tenantId, movimientoDTO.getSucursalId(), LocalDate.now())
                        : stockLoteRepository.sumStockVigente(
                                producto.getId(), tenantId, LocalDate.now());
            } else {
                stockDisponible = producto.getStockActual();
            }
            if (stockDisponible < movimientoDTO.getCantidad()) {
                throw new BadRequestException("Stock insuficiente. Stock disponible: " + stockDisponible);
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

        // Auto-generar referencia si el usuario no ingresó ninguna
        if (movimientoCreado.getReferencia() == null || movimientoCreado.getReferencia().isBlank()) {
            String prefix = switch (movimientoCreado.getTipo()) {
                case "ENTRADA"       -> "ENT";
                case "DEVOLUCION"    -> "DEV";
                case "AJUSTE"        -> "AJST";
                case "AJUSTE_PRECIO" -> "AJST-P";
                case "SALIDA"        -> "SAL";
                case "MERMA"         -> "MRMA";
                default              -> "MOV";
            };
            movimientoCreado.setReferencia(prefix + "-" + movimientoCreado.getId());
            movimientoRepository.save(movimientoCreado);
        }

        // Registrar en stock_lotes para control FEFO solo cuando viene fecha_vencimiento.
        // Para productos farmacia el frontend obliga la fecha; para otros rubros no se crea lote.
        if ((esEntrada || esDevolucion) && fechaVencimiento != null) {
            stockLoteService.registrarLote(
                    tenantId, movimientoCreado.getId(), producto.getId(),
                    movimientoDTO.getSucursalId(), lote,
                    fechaVencimiento, movimientoDTO.getCantidad(), proveedorId, precioVenta, costoUnitario);
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
                                    case "SALIDA": case "MERMA": sv -= movimientoDTO.getCantidad(); break;
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
                                    case "SALIDA": case "MERMA": sv -= movimientoDTO.getCantidad(); break;
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
                    case "SALIDA": case "MERMA": nuevoStock -= movimientoDTO.getCantidad(); break;
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
                            case "SALIDA": case "MERMA": stockSuc -= movimientoDTO.getCantidad(); break;
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
                log.warn("⚠️ FEFO falló en SALIDA manual: {}", e.getMessage());
                throw new BadRequestException("Stock vigente insuficiente en lotes disponibles: " + e.getMessage());
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

                    // Ajustar el lote y recalcular desde la suma real (evita delta=0 cuando lote ya estaba en el valor objetivo)
                    stockLoteService.ajustarLoteEspecifico(
                            movimientoDTO.getAjusteLoteMovimientoId(), movimientoDTO.getCantidad());
                    int nuevoTotalProducto = stockLoteRepository.sumStockTotalLotes(producto.getId(), tenantId);
                    producto.setStockActual(nuevoTotalProducto);

                    // Actualizar el movimiento: cantidad = total producto, descripción = lote ajustado
                    movimientoCreado.setCantidad(nuevoTotalProducto);
                    movimientoCreado.setDescripcion("Lote: " + nombreLote
                            + " | " + movimientoDTO.getCantidad() + " uds"
                            + (movimientoDTO.getDescripcion() != null && !movimientoDTO.getDescripcion().isBlank()
                                ? " | " + movimientoDTO.getDescripcion() : ""));
                    movimientoRepository.save(movimientoCreado);

                    // Corregir stock de sucursal con suma real de lotes de esa sucursal
                    if (movimientoDTO.getSucursalId() != null) {
                        final int sucTotal = stockLoteRepository.sumStockTotalLotesBySucursal(
                                producto.getId(), tenantId, movimientoDTO.getSucursalId());
                        stockSucursalRepository
                                .findByProductoIdAndSucursalId(producto.getId(), movimientoDTO.getSucursalId())
                                .ifPresent(entry -> {
                                    entry.setStockActual(sucTotal);
                                    stockSucursalRepository.save(entry);
                                });
                    }
                } else {
                    // Sin lote seleccionado: recalcular stockActual como suma real de lotes
                    // para mantener consistencia contable en productos con lotes.
                    boolean tieneLotes = stockLoteRepository.existsByProductoIdAndTenantId(producto.getId(), tenantId);
                    if (tieneLotes) {
                        int sumLotes = stockLoteRepository.sumStockTotalLotes(producto.getId(), tenantId);
                        producto.setStockActual(sumLotes);
                        movimientoCreado.setCantidad(sumLotes);
                        movimientoRepository.save(movimientoCreado);
                        log.info("♻️ AJUSTE sin lote: stockActual resincronizado a {} (suma lotes)", sumLotes);

                        // Resincronizar también ProductoStockSucursal con stock real de lotes en esta sucursal
                        if (movimientoDTO.getSucursalId() != null) {
                            int sumLotesSuc = stockLoteRepository.sumStockTotalLotesBySucursal(
                                    producto.getId(), tenantId, movimientoDTO.getSucursalId());
                            final int stockSucFinal = sumLotesSuc;
                            sucursalRepository.findById(movimientoDTO.getSucursalId()).ifPresent(sucursal -> {
                                ProductoStockSucursal entry = stockSucursalRepository
                                        .findByProductoIdAndSucursalId(producto.getId(), sucursal.getId())
                                        .orElseGet(() -> ProductoStockSucursal.builder()
                                                .producto(producto)
                                                .sucursal(sucursal)
                                                .tenantId(tenantId)
                                                .stockActual(0)
                                                .build());
                                entry.setStockActual(stockSucFinal);
                                stockSucursalRepository.save(entry);
                                log.info("📍 ProductoStockSucursal {} resincronizado a {} (AJUSTE sin lote)",
                                        sucursal.getId(), stockSucFinal);
                            });
                        }
                    }
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
    @PreAuthorize("hasRole('ADMIN') or hasAuthority('PERM_VER_INVENTARIO') or hasRole('VENDEDOR')")
    public ResponseEntity<List<LoteVencimientoDTO>> getLotes() {
        String tenantId = TenantContext.getCurrentTenant();
        LocalDate hoy = LocalDate.now();

        List<MovimientoInventario> movimientos = movimientoRepository
                .findEntradasConVencimientoPorTenant(tenantId);

        // Obtener stock real, proveedor y precio por lote desde stock_lotes (batch)
        List<Long> movIds = movimientos.stream().map(MovimientoInventario::getId).collect(Collectors.toList());
        java.util.Map<Long, Integer> stockPorMovimiento = stockLoteService.getStockPorMovimientoIds(movIds);
        java.util.Map<Long, String> proveedorPorMovimiento = stockLoteService.getProveedorNombrePorMovimientoIds(movIds);
        java.util.Map<Long, java.math.BigDecimal> precioPorMovimiento = stockLoteService.getPrecioVentaPorMovimientoIds(movIds);
        java.util.Map<Long, Long> proveedorIdPorMovimiento = stockLoteService.getProveedorIdPorMovimientoIds(movIds);

        List<LoteVencimientoDTO> lotes = movimientos.stream()
                .map(m -> LoteVencimientoDTO.builder()
                        .movimientoId(m.getId())
                        .productoId(m.getProducto().getId())
                        .productoNombre(m.getProducto().getNombre())
                        .codigoBarras(m.getProducto().getCodigoBarras())
                        .lote(m.getLote())
                        .fechaVencimiento(m.getFechaVencimiento())
                        .cantidad(m.getCantidad())
                        .stockActual(stockPorMovimiento.getOrDefault(m.getId(), 0))
                        .diasRestantes(ChronoUnit.DAYS.between(hoy, m.getFechaVencimiento()))
                        .registroSanitario(m.getRegistroSanitario())
                        .proveedorId(proveedorIdPorMovimiento.get(m.getId()))
                        .proveedorNombre(proveedorPorMovimiento.get(m.getId()))
                        .precioVenta(precioPorMovimiento.get(m.getId()))
                        .build())
                .collect(Collectors.toList());

        return ResponseEntity.ok(lotes);
    }

    /**
     * Devuelve los lotes vigentes de un producto específico — para el selector de lote en ajustes.
     */
    @GetMapping("/lotes/producto/{productoId}")
    @PreAuthorize("hasRole('ADMIN') or hasAuthority('PERM_VER_INVENTARIO') or hasRole('VENDEDOR')")
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
        java.util.Map<Long, String> proveedorPorMovimiento = stockLoteService.getProveedorNombrePorMovimientoIds(movIds);
        java.util.Map<Long, java.math.BigDecimal> precioPorMovimiento = stockLoteService.getPrecioVentaPorMovimientoIds(movIds);

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
                        .proveedorNombre(proveedorPorMovimiento.get(m.getId()))
                        .precioVenta(precioPorMovimiento.get(m.getId()))
                        .build())
                .collect(Collectors.toList());

        return ResponseEntity.ok(lotes);
    }

    /**
     * Lotes disponibles (no vencidos, con stock) para seleccionar en el POS.
     * Permite al vendedor elegir de qué proveedor/lote se descuenta el stock.
     */
    @GetMapping("/lotes/disponibles")
    @PreAuthorize("hasAnyRole('ADMIN', 'VENDEDOR', 'GESTOR_INVENTARIO') or hasAuthority('PERM_VER_INVENTARIO') or hasAuthority('PERM_CREAR_VENTA')")
    public ResponseEntity<List<com.stockflow.dto.StockLoteDisponibleDTO>> getLotesDisponibles(
            @RequestParam Long productoId,
            @RequestParam(required = false) Long sucursalId) {
        String tenantId = TenantContext.getCurrentTenant();
        return ResponseEntity.ok(stockLoteService.getLotesDisponibles(tenantId, productoId, sucursalId));
    }

    @GetMapping("/{movimientoId}/lotes-venta")
    @PreAuthorize("hasRole('ADMIN') or hasAuthority('PERM_VER_INVENTARIO')")
    public ResponseEntity<List<com.stockflow.dto.LoteVentaDetalleDTO>> getLotesVenta(
            @PathVariable Long movimientoId) {
        return movimientoRepository.findById(movimientoId).map(mov -> {
            String ref = mov.getReferencia();
            if (ref == null || !ref.startsWith("Venta #")) {
                return ResponseEntity.ok(List.<com.stockflow.dto.LoteVentaDetalleDTO>of());
            }
            long ventaId;
            try { ventaId = Long.parseLong(ref.replace("Venta #", "").trim()); }
            catch (NumberFormatException e) {
                return ResponseEntity.ok(List.<com.stockflow.dto.LoteVentaDetalleDTO>of());
            }
            Long productoId = mov.getProducto() != null ? mov.getProducto().getId() : null;
            List<com.stockflow.dto.LoteVentaDetalleDTO> result = detalleVentaRepository
                    .findByVentaId(ventaId).stream()
                    .filter(d -> productoId == null || productoId.equals(d.getProducto().getId()))
                    .map(d -> {
                        String[] loteRef = {null};
                        String[] provRef = {null};
                        BigDecimal[] precioRef = {d.getPrecioUnitario()};
                        if (d.getStockLoteId() != null) {
                            stockLoteRepository.findById(d.getStockLoteId()).ifPresent(sl -> {
                                loteRef[0] = sl.getLote();
                                if (sl.getPrecioVenta() != null) precioRef[0] = sl.getPrecioVenta();
                                if (sl.getProveedorId() != null) {
                                    provRef[0] = proveedorRepository.findById(sl.getProveedorId())
                                            .map(com.stockflow.entity.Proveedor::getNombre).orElse(null);
                                }
                            });
                        }
                        return com.stockflow.dto.LoteVentaDetalleDTO.builder()
                                .lote(loteRef[0])
                                .proveedorNombre(provRef[0])
                                .cantidadDescontada(d.getCantidad())
                                .precioVenta(precioRef[0])
                                .build();
                    }).collect(Collectors.toList());
            return ResponseEntity.ok(result);
        }).orElse(ResponseEntity.ok(List.of()));
    }

    @PatchMapping("/lotes/{movimientoId}/proveedor")
    @PreAuthorize("hasRole('ADMIN') or hasAuthority('PERM_EDITAR_INVENTARIO')")
    public ResponseEntity<Void> actualizarProveedorLote(
            @PathVariable Long movimientoId,
            @RequestBody com.stockflow.dto.ActualizarLoteProveedorDTO dto) {
        String tenantId = TenantContext.getCurrentTenant();
        stockLoteService.actualizarProveedorLote(movimientoId, dto.getProveedorId(), dto.getPrecioVenta(),
                dto.getLote(), dto.getFechaVencimiento());
        movimientoRepository.findById(movimientoId).ifPresent(mov -> {
            if (tenantId.equals(mov.getTenantId())) {
                mov.setProveedorId(dto.getProveedorId());
                if (dto.getLote() != null) mov.setLote(dto.getLote());
                if (dto.getFechaVencimiento() != null) mov.setFechaVencimiento(dto.getFechaVencimiento());
                movimientoRepository.save(mov);
                if (dto.getPrecioVenta() != null && mov.getProducto() != null) {
                    Producto prod = mov.getProducto();
                    if (tenantId.equals(prod.getTenantId())) {
                        prod.setPrecioVenta(dto.getPrecioVenta());
                        productoRepository.save(prod);
                        log.info("✅ Precio del producto {} actualizado a {} desde edición de lote",
                                prod.getId(), dto.getPrecioVenta());
                    }
                }
            }
        });
        return ResponseEntity.ok().build();
    }

    /**
     * Da de baja (MERMA) un lote vencido.
     * Reduce stock_lotes y producto.stockActual y registra un movimiento tipo MERMA.
     * Body: { movimientoOrigenId, cantidad, motivo, observaciones }
     */
    @PostMapping("/merma")
    @PreAuthorize("hasRole('ADMIN') or hasAuthority('PERM_CREAR_INVENTARIO')")
    @org.springframework.transaction.annotation.Transactional
    public ResponseEntity<Map<String, Object>> darDeBajaLote(@RequestBody Map<String, Object> body) {
        String tenantId = TenantContext.getCurrentTenant();

        Long movimientoOrigenId = body.get("movimientoOrigenId") instanceof Number n ? n.longValue() : null;
        Integer cantidad = body.get("cantidad") instanceof Number n ? n.intValue() : null;
        String motivo = (String) body.getOrDefault("motivo", "VENCIMIENTO");
        String observaciones = (String) body.getOrDefault("observaciones", "");

        if (movimientoOrigenId == null) throw new BadRequestException("Se requiere movimientoOrigenId.");
        if (cantidad == null || cantidad <= 0) throw new BadRequestException("La cantidad debe ser mayor a 0.");

        // Validar que el lote pertenece al tenant y tiene stock suficiente
        com.stockflow.entity.StockLote lote = stockLoteRepository.findByMovimientoId(movimientoOrigenId)
                .orElseThrow(() -> new ResourceNotFoundException("Lote no encontrado."));
        if (!tenantId.equals(lote.getTenantId()))
            throw new ResourceNotFoundException("Lote no encontrado.");
        if (lote.getStockActual() < cantidad)
            throw new BadRequestException("Cantidad mayor al stock del lote (" + lote.getStockActual() + ").");

        Producto producto = productoService.obtenerProductoPorId(lote.getProductoId())
                .filter(p -> tenantId.equals(p.getTenantId()))
                .orElseThrow(() -> new ResourceNotFoundException("Producto no encontrado."));

        // Obtener info del movimiento origen para lote/fechaVencimiento
        MovimientoInventario origen = movimientoRepository.findById(movimientoOrigenId)
                .orElseThrow(() -> new ResourceNotFoundException("Movimiento origen no encontrado."));

        // Resolver usuario del request actual
        Long usuarioIdMerma = TenantContext.getCurrentUserId();
        Usuario usuarioMerma = usuarioIdMerma != null
                ? usuarioTenantRepository.findByUsuarioIdAndTenantIdAndActivoTrue(usuarioIdMerma, tenantId)
                        .map(UsuarioTenant::getUsuario)
                        .orElse(null)
                : null;

        // Registrar movimiento MERMA
        MovimientoInventario merma = MovimientoInventario.builder()
                .producto(producto)
                .usuario(usuarioMerma)
                .tipo("MERMA")
                .cantidad(cantidad)
                .tenantId(tenantId)
                .lote(origen.getLote())
                .fechaVencimiento(origen.getFechaVencimiento())
                .descripcion("Baja de lote vencido. Motivo: " + motivo
                        + (observaciones != null && !observaciones.isBlank() ? " | " + observaciones : ""))
                .sucursalId(lote.getSucursalId())
                .build();
        MovimientoInventario mermaGuardada = movimientoRepository.save(merma);
        mermaGuardada.setReferencia("MERMA-" + mermaGuardada.getId());
        movimientoRepository.save(mermaGuardada);

        // Descontar del lote
        lote.setStockActual(lote.getStockActual() - cantidad);
        stockLoteRepository.save(lote);

        // Descontar del producto
        int nuevoStock = Math.max(0, producto.getStockActual() - cantidad);
        producto.setStockActual(nuevoStock);
        productoService.actualizarProducto(producto.getId(), producto);

        // Actualizar ProductoStockSucursal si el lote tiene sucursal
        if (lote.getSucursalId() != null) {
            final Long sucursalIdLote = lote.getSucursalId();
            final int cantidadFinal = cantidad;
            sucursalRepository.findById(sucursalIdLote).ifPresent(sucursal -> {
                ProductoStockSucursal entry = stockSucursalRepository
                        .findByProductoIdAndSucursalId(producto.getId(), sucursal.getId())
                        .orElseGet(() -> ProductoStockSucursal.builder()
                                .producto(producto)
                                .sucursal(sucursal)
                                .tenantId(tenantId)
                                .stockActual(0)
                                .build());
                int cur = entry.getStockActual() != null ? entry.getStockActual() : 0;
                entry.setStockActual(Math.max(0, cur - cantidadFinal));
                stockSucursalRepository.save(entry);
                log.info("📍 Stock sucursal {} actualizado por MERMA: -{} para producto {}",
                        sucursal.getId(), cantidadFinal, producto.getId());
            });
        }

        log.info("🗑️ MERMA registrada: producto={} lote={} cantidad={} tenant={}",
                producto.getNombre(), origen.getLote(), cantidad, tenantId);

        return ResponseEntity.ok(Map.of(
                "mensaje", "Baja registrada correctamente.",
                "movimientoId", mermaGuardada.getId(),
                "nuevoStockProducto", nuevoStock,
                "nuevoStockLote", lote.getStockActual()
        ));
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