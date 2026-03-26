package com.stockflow.controller;

import com.stockflow.dto.VentaDTO;
import com.stockflow.dto.DetalleVentaDTO;
import com.stockflow.entity.*;
import com.stockflow.mapper.VentaMapper;
import com.stockflow.mapper.DetalleVentaMapper;
import com.stockflow.service.MovimientoInventarioService;
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
        return ventaService.obtenerVentaPorId(id)
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
        log.info("📋 Obteniendo detalles de venta: {}", ventaId);
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

        // 2) Crear venta (entidad)
        Venta venta = Venta.builder()
                .vendedor(vendedor)
                .total(ventaDTO.getTotal())
                .metodoPago(ventaDTO.getMetodoPago())
                .estado(ventaDTO.getEstado())
                .tenantId(tenantId)
                .detalles(detalles)
                .createdAt(LocalDateTime.now())
                .build();

        // 3) Persistir venta primero (para tener ID y usarlo como referencia)
        Venta ventaCreada = ventaService.crearVenta(venta);
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

    @DeleteMapping("/{id}")
    @PreAuthorize("hasRole('ADMIN') or hasAuthority('PERM_ELIMINAR_VENTA')")
    public ResponseEntity<Void> eliminar(@PathVariable Long id) {
        log.info("🗑️ Eliminando venta ID: {}", id);
        ventaService.eliminarVenta(id);
        return ResponseEntity.noContent().build();
    }
}