package com.stockflow.controller;

import com.stockflow.dto.ComprobanteDTO;
import com.stockflow.dto.ValidarNotaCreditoResponseDTO;
import com.stockflow.dto.VentaDTO;
import com.stockflow.dto.DetalleVentaDTO;
import com.stockflow.entity.*;
import com.stockflow.entity.NotaCredito;
import com.stockflow.mapper.VentaMapper;
import com.stockflow.mapper.DetalleVentaMapper;
import com.stockflow.service.ComprobanteService;
import com.stockflow.service.MovimientoInventarioService;
import com.stockflow.service.NotaCreditoService;
import com.stockflow.service.NotificacionService;
import com.stockflow.service.ProductoVarianteService;
import com.stockflow.service.VentaService;
import com.stockflow.service.ProductoService;
import com.stockflow.service.UsuarioService;
import com.stockflow.entity.ProductoVarianteStockSucursal;
import com.stockflow.repository.ProductoStockSucursalRepository;
import com.stockflow.repository.ProductoVarianteRepository;
import com.stockflow.repository.ProductoVarianteStockSucursalRepository;
import com.stockflow.repository.SucursalRepository;
import com.stockflow.repository.VentaRepository;
import com.stockflow.util.TenantContext;
import com.stockflow.exception.BadRequestException;
import com.stockflow.exception.ResourceNotFoundException;
import jakarta.transaction.Transactional;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.web.bind.annotation.*;
import jakarta.validation.Valid;
import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;
import java.util.stream.Collectors;
import java.math.RoundingMode;

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
    private final MovimientoInventarioService movimientoService;
    private final ComprobanteService comprobanteService;
    private final NotaCreditoService notaCreditoService;
    private final NotificacionService notificacionService;
    private final ProductoVarianteRepository                productoVarianteRepository;
    private final ProductoVarianteStockSucursalRepository   varianteStockSucursalRepository;
    private final ProductoStockSucursalRepository           stockSucursalRepository;
    private final SucursalRepository                        sucursalRepository;
    private final VentaRepository                           ventaRepository;
    private final com.stockflow.service.StockLoteService   stockLoteService;

    /**
     * ✅ ACTUALIZADO: Obtiene ventas del tenant actual.
     * Permitido para ADMIN/GERENTE o usuarios con permiso PERM_VER_VENTAS.
     */
    @GetMapping
    @PreAuthorize("hasAnyRole('ADMIN', 'GERENTE') or hasAuthority('PERM_VER_VENTAS')")
    public ResponseEntity<List<VentaDTO>> obtenerTodas(
            @RequestParam(required = false) Long sucursalId) {
        String tenantId = TenantContext.getCurrentTenant();
        log.info("💰 Obteniendo ventas para tenant: {} sucursalId={}", tenantId, sucursalId);

        List<Venta> ventas = sucursalId != null
                ? ventaService.obtenerVentasPorTenantYSucursal(tenantId, sucursalId)
                : ventaService.obtenerVentasPorTenant(tenantId);

        return ResponseEntity.ok(ventaMapper.toDTOList(ventas));
    }

    @GetMapping("/{id}")
    @PreAuthorize("hasRole('ADMIN') or hasAuthority('PERM_VER_DETALLE_VENTA')")
    public ResponseEntity<VentaDTO> obtenerPorId(@PathVariable Long id) {
        String tenantId = TenantContext.getCurrentTenant();
        return ventaService.obtenerVentaPorId(id)
                .filter(v -> tenantId.equals(v.getTenantId()))
                .map(ventaMapper::toDTO)
                .map(ResponseEntity::ok)
                .orElse(ResponseEntity.notFound().build());
    }

    @GetMapping("/vendedor/{vendedorId}")
    @PreAuthorize("hasAnyRole('ADMIN', 'GERENTE') or hasAuthority('PERM_VER_MIS_VENTAS') or hasAuthority('PERM_VER_VENTAS')")
    public ResponseEntity<List<VentaDTO>> obtenerPorVendedor(
            @PathVariable Long vendedorId,
            Authentication authentication) {

        String tenantId = TenantContext.getCurrentTenant();
        Long currentUserId = TenantContext.getCurrentUserId();

        boolean isAdminOrGerente = authentication.getAuthorities().stream()
                .map(GrantedAuthority::getAuthority)
                .anyMatch(a -> a.equals("ROLE_ADMIN") || a.equals("ROLE_GERENTE"));

        // Si el usuario es VENDEDOR, forzar su propio ID para evitar acceso cruzado
        Long efectiveVendedorId;
        if (isAdminOrGerente) {
            efectiveVendedorId = vendedorId;
        } else {
            efectiveVendedorId = currentUserId;
        }

        log.info("👤 Obteniendo ventas del vendedor: {} (solicitado: {}) para tenant: {}",
                efectiveVendedorId, vendedorId, tenantId);

        return ResponseEntity.ok(
                ventaMapper.toDTOList(ventaService.obtenerVentasPorVendedorYTenant(efectiveVendedorId, tenantId))
        );
    }

    /**
     * ✅ ACTUALIZADO: Usa tenantId automáticamente
     */
    @GetMapping("/periodo")
    @PreAuthorize("hasRole('ADMIN') or hasAuthority('PERM_VER_VENTAS')")
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
    @PreAuthorize("hasRole('ADMIN') or hasAuthority('PERM_VER_DETALLE_VENTA')")
    public ResponseEntity<List<DetalleVentaDTO>> obtenerDetalles(@PathVariable Long ventaId) {
        String tenantId = TenantContext.getCurrentTenant();
        log.info("📋 Obteniendo detalles de venta: {}", ventaId);
        // Verificar que la venta pertenezca al tenant antes de devolver detalles
        boolean pertenece = ventaService.obtenerVentaPorId(ventaId)
                .map(v -> tenantId.equals(v.getTenantId()))
                .orElse(false);
        if (!pertenece) return ResponseEntity.notFound().build();
        return ResponseEntity.ok(
                detalleVentaMapper.toDTOList(ventaService.obtenerDetallesVenta(ventaId))
        );
    }

    /**
     * ✅ ACTUALIZADO: Setea tenantId automáticamente
     */
    @PostMapping
    @PreAuthorize("hasAnyRole('ADMIN', 'VENDEDOR') or hasAuthority('PERM_CREAR_VENTA')")
    @Transactional
    public ResponseEntity<VentaDTO> crear(@Valid @RequestBody VentaDTO ventaDTO) {
        String tenantId = TenantContext.getCurrentTenant();
        log.info("➕ Creando venta para tenant: {}", tenantId);

        Usuario vendedor = usuarioService.obtenerUsuarioPorId(ventaDTO.getVendedorId())
                .orElseThrow(() -> new ResourceNotFoundException("Vendedor no encontrado"));

        if (ventaDTO.getDetalles() == null || ventaDTO.getDetalles().isEmpty()) {
            throw new BadRequestException("La venta debe tener al menos un detalle");
        }

        ventaDTO.setTenantId(tenantId);

        // 1) Validar stock y armar detalles (sin persistir aún)
        List<DetalleVenta> detalles = ventaDTO.getDetalles().stream()
                .map(detalleDTO -> {
                    Producto producto = productoService.obtenerProductoPorId(detalleDTO.getProductoId())
                            .orElseThrow(() -> new ResourceNotFoundException("Producto no encontrado: " + detalleDTO.getProductoId()));

                    if (!tenantId.equals(producto.getTenantId())) {
                        throw new BadRequestException("Producto no encontrado: " + detalleDTO.getProductoId());
                    }

                    // Los servicios (tipo=SERVICIO) no tienen stock — saltar validación
                    boolean esServicio = "SERVICIO".equals(producto.getTipo());
                    if (!esServicio) {
                        if (detalleDTO.getVarianteId() != null) {
                            Long sucId = ventaDTO.getSucursalId();
                            if (sucId != null) {
                                // Plan PRO: validar stock por sucursal
                                ProductoVarianteStockSucursal entry = varianteStockSucursalRepository
                                        .findByVarianteIdAndSucursalId(detalleDTO.getVarianteId(), sucId)
                                        .orElseThrow(() -> new BadRequestException("Stock insuficiente para la variante seleccionada de: " + producto.getNombre()));
                                if (entry.getStockActual() < detalleDTO.getCantidad()) {
                                    throw new BadRequestException("Stock insuficiente para la variante seleccionada de: " + producto.getNombre());
                                }
                            } else {
                                // Plan BÁSICO: validar stock global de la variante
                                productoVarianteRepository.findByIdAndTenantId(detalleDTO.getVarianteId(), tenantId)
                                        .filter(v -> v.getStockActual() >= detalleDTO.getCantidad())
                                        .orElseThrow(() -> new BadRequestException("Stock insuficiente para la variante seleccionada de: " + producto.getNombre()));
                            }
                        } else {
                            // Validar contra stock vigente (no vencido) si el producto tiene lotes
                            Integer stockVigente = stockLoteService.getStockVigente(
                                    tenantId, producto.getId(), ventaDTO.getSucursalId());
                            int stockDisponible = stockVigente != null ? stockVigente : producto.getStockActual();
                            if (stockDisponible < detalleDTO.getCantidad()) {
                                throw new BadRequestException("Stock insuficiente para el producto: " + producto.getNombre());
                            }
                        }
                    }

                    return DetalleVenta.builder()
                            .producto(producto)
                            .cantidad(detalleDTO.getCantidad())
                            .precioUnitario(detalleDTO.getPrecioUnitario())
                            .subtotal(detalleDTO.getPrecioUnitario().multiply(BigDecimal.valueOf(detalleDTO.getCantidad())))
                            .varianteId(detalleDTO.getVarianteId())
                            .varianteDescripcion(detalleDTO.getVarianteDescripcion())
                            .build();
                })
                .collect(Collectors.toList());

        // Los precios ya incluyen IGV. El total es la suma directa de los subtotales.
        // El IGV se extrae (no se suma encima): base = total/1.18, igv = total - base
        final BigDecimal IGV_DIVISOR = new BigDecimal("1.18");

        BigDecimal totalVenta = detalles.stream()
                .map(DetalleVenta::getSubtotal)
                .reduce(BigDecimal.ZERO, BigDecimal::add)
                .setScale(2, RoundingMode.HALF_UP);

        BigDecimal baseImponible = totalVenta.divide(IGV_DIVISOR, 2, RoundingMode.HALF_UP);
        BigDecimal igvVenta = totalVenta.subtract(baseImponible).setScale(2, RoundingMode.HALF_UP);

        log.info("📊 Base imponible: {}, IGV incluido: {}, Total: {}", baseImponible, igvVenta, totalVenta);

        // Aplicar descuento de nota de credito si se proporciona
        BigDecimal descuentoNc = BigDecimal.ZERO;
        Long notaCreditoId = null;
        if (ventaDTO.getNotaCreditoCodigo() != null && !ventaDTO.getNotaCreditoCodigo().isBlank()) {
            ValidarNotaCreditoResponseDTO validacion = notaCreditoService.validar(
                    ventaDTO.getNotaCreditoCodigo(), tenantId);
            if (!validacion.isValida()) {
                throw new BadRequestException("Nota de credito no valida: " + validacion.getMensaje());
            }
            descuentoNc = validacion.getMontoTotal().min(totalVenta);
        }
        BigDecimal totalConDescuento = totalVenta.subtract(descuentoNc).max(BigDecimal.ZERO);

        Venta venta = Venta.builder()
                .vendedor(vendedor)
                .total(totalConDescuento)
                .metodoPago(ventaDTO.getMetodoPago())
                .estado(ventaDTO.getEstado())
                .tenantId(tenantId)
                .detalles(detalles)
                .createdAt(LocalDateTime.now())
                .cajaId(ventaDTO.getCajaId())
                .clienteId(ventaDTO.getClienteId())
                .sucursalId(ventaDTO.getSucursalId())
                .descuentoNotaCredito(descuentoNc.compareTo(BigDecimal.ZERO) > 0 ? descuentoNc : null)
                .build();

        // 3) Persistir venta primero (para tener ID y usarlo como referencia)
        Venta ventaCreada = ventaService.crearVenta(venta);

        // Aplicar nota de credito una vez tenemos el ID de la venta
        if (ventaDTO.getNotaCreditoCodigo() != null && !ventaDTO.getNotaCreditoCodigo().isBlank()
                && descuentoNc.compareTo(BigDecimal.ZERO) > 0) {
            NotaCredito ncAplicada = notaCreditoService.aplicar(
                    ventaDTO.getNotaCreditoCodigo(), ventaCreada.getId(), tenantId);
            ventaCreada.setNotaCreditoId(ncAplicada.getId());
            ventaService.actualizarNotaCredito(ventaCreada.getId(), ncAplicada.getId());
            log.info("Nota de credito {} aplicada a venta {}", ventaDTO.getNotaCreditoCodigo(), ventaCreada.getId());
        }
        log.info("✅ Venta creada exitosamente: ID {}", ventaCreada.getId());

        // 4) Por cada detalle: descontar stock + crear movimiento SALIDA
        //    Los servicios (tipo = SERVICIO) no tocan el inventario.
        for (DetalleVenta detalle : detalles) {
            Producto producto = detalle.getProducto();
            boolean esServicio = "SERVICIO".equals(producto.getTipo());

            if (!esServicio) {
                if (detalle.getVarianteId() != null) {
                    productoVarianteRepository.findById(detalle.getVarianteId()).ifPresent(v -> {
                        Long sucId = ventaCreada.getSucursalId();
                        if (sucId != null) {
                            // Plan PRO: descontar del stock por sucursal
                            ProductoVarianteStockSucursal entry = varianteStockSucursalRepository
                                    .findByVarianteIdAndSucursalId(v.getId(), sucId)
                                    .orElseGet(() -> ProductoVarianteStockSucursal.builder()
                                            .varianteId(v.getId())
                                            .sucursalId(sucId)
                                            .tenantId(tenantId)
                                            .stockActual(0)
                                            .stockMinimo(v.getStockMinimo() != null ? v.getStockMinimo() : 0)
                                            .build());
                            int nuevoSuc = Math.max(0, (entry.getStockActual() != null ? entry.getStockActual() : 0) - detalle.getCantidad());
                            entry.setStockActual(nuevoSuc);
                            varianteStockSucursalRepository.save(entry);
                            // Stock global de la variante = suma de todos los locales
                            int totalVariante = varianteStockSucursalRepository
                                    .sumStockByVarianteIdAndTenantId(v.getId(), tenantId);
                            v.setStockActual(totalVariante);
                        } else {
                            // Plan BÁSICO: descontar del stock global
                            v.setStockActual(v.getStockActual() - detalle.getCantidad());
                        }
                        productoVarianteRepository.save(v);
                        int total = productoVarianteRepository
                                .findByProductoIdAndActivoTrueAndTenantId(producto.getId(), tenantId)
                                .stream().mapToInt(pv -> pv.getStockActual() != null ? pv.getStockActual() : 0).sum();
                        producto.setStockActual(total);
                        productoService.actualizarProducto(producto.getId(), producto);
                    });
                } else {
                    producto.setStockActual(producto.getStockActual() - detalle.getCantidad());
                    productoService.actualizarProducto(producto.getId(), producto);

                    // Descuento FEFO en stock_lotes (si el producto tiene lotes con vencimiento)
                    stockLoteService.descontarFefo(
                            tenantId, producto.getId(), ventaCreada.getSucursalId(), detalle.getCantidad());

                    // Actualizar stock por sucursal si la venta tiene sucursalId
                    if (ventaCreada.getSucursalId() != null) {
                        sucursalRepository.findById(ventaCreada.getSucursalId()).ifPresent(sucursal -> {
                            ProductoStockSucursal entry = stockSucursalRepository
                                    .findByProductoIdAndSucursalId(producto.getId(), sucursal.getId())
                                    .orElseGet(() -> ProductoStockSucursal.builder()
                                            .producto(producto)
                                            .sucursal(sucursal)
                                            .tenantId(tenantId)
                                            .stockActual(0)
                                            .build());
                            int stockSuc = (entry.getStockActual() != null ? entry.getStockActual() : 0) - detalle.getCantidad();
                            entry.setStockActual(Math.max(0, stockSuc));
                            stockSucursalRepository.save(entry);
                        });
                    }
                }

                String varDesc = detalle.getVarianteDescripcion() != null
                        ? " [" + detalle.getVarianteDescripcion() + "]" : "";
                MovimientoInventario movimiento = MovimientoInventario.builder()
                        .producto(producto)
                        .usuario(vendedor)
                        .tipo("SALIDA")
                        .cantidad(detalle.getCantidad())
                        .descripcion("Venta #" + ventaCreada.getId() + varDesc)
                        .referencia("Venta #" + ventaCreada.getId())
                        .tenantId(tenantId)
                        .sucursalId(ventaCreada.getSucursalId())
                        .build();
                movimientoService.crearMovimiento(movimiento);
            }
        }

        return ResponseEntity.status(HttpStatus.CREATED)
                .body(ventaMapper.toDTO(ventaCreada));
    }

    @PatchMapping("/{id}/cliente")
    @PreAuthorize("hasRole('ADMIN') or hasAuthority('PERM_EMITIR_COMPROBANTE') or hasAuthority('CREAR_VENTA')")
    public ResponseEntity<VentaDTO> asignarCliente(
            @PathVariable Long id,
            @RequestBody java.util.Map<String, Long> body) {
        String tenantId = TenantContext.getCurrentTenant();
        Long clienteId = body.get("clienteId");
        return ventaRepository.findById(id)
                .filter(v -> tenantId.equals(v.getTenantId()))
                .map(v -> {
                    v.setClienteId(clienteId);
                    return ResponseEntity.ok(ventaMapper.toDTO(ventaRepository.save(v)));
                })
                .orElse(ResponseEntity.notFound().build());
    }

    @PatchMapping("/{id}/anular")
    @PreAuthorize("hasAnyRole('ADMIN', 'GERENTE') or hasAuthority('PERM_ANULAR_VENTA') or hasAuthority('PERM_ELIMINAR_VENTA')")
    @Transactional
    public ResponseEntity<VentaDTO> anular(@PathVariable Long id) {
        String tenantId = TenantContext.getCurrentTenant();
        Long usuarioId = TenantContext.getCurrentUserId();
        log.info("🚫 Anulando venta ID: {} (tenant={})", id, tenantId);

        Venta venta = ventaService.anularVenta(id, tenantId);

        try {
            notificacionService.notificarRoles(tenantId, List.of("ADMIN", "GERENTE"),
                    "VENTA_ANULADA",
                    "🚫 Venta anulada",
                    String.format("La venta #%d por S/ %.2f fue anulada. Stock repuesto automáticamente.",
                            id, venta.getTotal()),
                    id, "VENTA");
        } catch (Exception e) {
            log.warn("No se pudo crear notificación de venta anulada: {}", e.getMessage());
        }

        log.info("✅ Venta {} anulada — stock repuesto, kardex actualizado", id);
        return ResponseEntity.ok(ventaMapper.toDTO(venta));
    }

    /**
     * Convenience endpoint: get the comprobante associated with a venta.
     */
    @GetMapping("/{ventaId}/comprobante")
    @PreAuthorize("hasRole('ADMIN') or hasAuthority('PERM_VER_COMPROBANTE')")
    public ResponseEntity<ComprobanteDTO> obtenerComprobante(@PathVariable Long ventaId) {
        String tenantId = TenantContext.getCurrentTenant();
        return comprobanteService.obtenerPorVenta(ventaId, tenantId)
                .map(ResponseEntity::ok)
                .orElse(ResponseEntity.notFound().build());
    }
}