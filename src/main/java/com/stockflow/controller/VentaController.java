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
import com.stockflow.service.VentaService;
import com.stockflow.service.ProductoService;
import com.stockflow.service.UsuarioService;
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

    /**
     * ✅ ACTUALIZADO: Obtiene ventas del tenant actual.
     * Permitido para ADMIN/GERENTE o usuarios con permiso PERM_VER_VENTAS.
     */
    @GetMapping
    @PreAuthorize("hasAnyRole('ADMIN', 'GERENTE') or hasAuthority('PERM_VER_VENTAS')")
    public ResponseEntity<List<VentaDTO>> obtenerTodas() {
        String tenantId = TenantContext.getCurrentTenant();
        log.info("💰 Obteniendo ventas para tenant: {}", tenantId);

        return ResponseEntity.ok(
                ventaMapper.toDTOList(ventaService.obtenerVentasPorTenant(tenantId))
        );
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

                    // Validar que el producto pertenezca al tenant actual (aislamiento multi-tenant)
                    if (!tenantId.equals(producto.getTenantId())) {
                        throw new BadRequestException("Producto no encontrado: " + detalleDTO.getProductoId());
                    }
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
        for (DetalleVenta detalle : detalles) {
            Producto producto = detalle.getProducto();

            // Descontar stock
            producto.setStockActual(producto.getStockActual() - detalle.getCantidad());
            productoService.actualizarProducto(producto.getId(), producto);

            // Crear movimiento por producto
            MovimientoInventario movimiento = MovimientoInventario.builder()
                    .producto(producto)
                    .usuario(vendedor)
                    .tipo("SALIDA")
                    .cantidad(detalle.getCantidad())
                    .descripcion("Venta #" + ventaCreada.getId())
                    .referencia("Venta #" + ventaCreada.getId())
                    .tenantId(tenantId)
                    .build();

            movimientoService.crearMovimiento(movimiento);
        }

        return ResponseEntity.status(HttpStatus.CREATED)
                .body(ventaMapper.toDTO(ventaCreada));
    }

    @PatchMapping("/{id}/anular")
    @PreAuthorize("hasAnyRole('ADMIN', 'GERENTE') or hasAuthority('PERM_ANULAR_VENTA') or hasAuthority('PERM_ELIMINAR_VENTA')")
    @Transactional
    public ResponseEntity<VentaDTO> anular(@PathVariable Long id) {
        String tenantId = TenantContext.getCurrentTenant();
        Long usuarioId = TenantContext.getCurrentUserId();
        log.info("🚫 Anulando venta ID: {} (tenant={})", id, tenantId);

        // 1. Obtener venta y validar
        Venta venta = ventaService.obtenerVentaPorId(id)
                .filter(v -> tenantId.equals(v.getTenantId()))
                .orElseThrow(() -> new ResourceNotFoundException("Venta no encontrada"));

        if ("ANULADA".equals(venta.getEstado())) {
            throw new BadRequestException("La venta ya está anulada");
        }
        if ("DEVUELTA".equals(venta.getEstado()) || "DEVUELTA_PARCIAL".equals(venta.getEstado())) {
            throw new BadRequestException("No se puede anular una venta que ya tiene devoluciones registradas");
        }

        // 2. Obtener usuario que anula
        Usuario usuario = usuarioService.obtenerUsuarioPorId(usuarioId)
                .orElseThrow(() -> new ResourceNotFoundException("Usuario no encontrado"));

        // 3. Reponer stock + crear movimiento ANULACION por cada producto
        for (DetalleVenta detalle : venta.getDetalles()) {
            Producto producto = detalle.getProducto();
            producto.setStockActual(producto.getStockActual() + detalle.getCantidad());
            productoService.actualizarProducto(producto.getId(), producto);

            MovimientoInventario mov = MovimientoInventario.builder()
                    .producto(producto)
                    .usuario(usuario)
                    .tipo("AJUSTE")
                    .cantidad(detalle.getCantidad())
                    .descripcion("Anulación venta #" + venta.getId())
                    .referencia("ANUL-" + venta.getId())
                    .tenantId(tenantId)
                    .build();
            movimientoService.crearMovimiento(mov);

            log.info("📦 Stock repuesto: +{} de {} por anulación venta {}",
                    detalle.getCantidad(), producto.getNombre(), venta.getId());
        }

        // 4. Si se usó una NC en esta venta, restaurarla a PENDIENTE
        if (venta.getNotaCreditoId() != null) {
            notaCreditoService.restaurarPorVentaAnulada(venta.getNotaCreditoId(), tenantId);
            venta.setNotaCreditoId(null);
            venta.setDescuentoNotaCredito(null);
        }

        // 5. Marcar venta como ANULADA
        venta.setEstado("ANULADA");
        ventaService.crearVenta(venta);

        // Notificar anulación a ADMIN y GERENTE
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