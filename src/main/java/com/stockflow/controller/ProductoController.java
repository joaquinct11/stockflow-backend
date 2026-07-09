package com.stockflow.controller;

import com.stockflow.dto.ProductoDTO;
import com.stockflow.dto.ProductoImportResultDTO;
import com.stockflow.dto.ProductoImportRowDTO;
import com.stockflow.entity.Producto;
import com.stockflow.mapper.ProductoMapper;
import com.stockflow.repository.MovimientoInventarioRepository;
import com.stockflow.repository.ProductoStockSucursalRepository;
import com.stockflow.repository.ProductoVarianteStockSucursalRepository;
import com.stockflow.service.ProductoService;
import com.stockflow.util.TenantContext;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;
import jakarta.validation.Valid;
import java.time.LocalDate;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

@Slf4j
@RestController
@RequestMapping("/productos")
@RequiredArgsConstructor
public class ProductoController {

    private final ProductoService productoService;
    private final ProductoMapper productoMapper;
    private final MovimientoInventarioRepository movimientoRepository;
    private final ProductoStockSucursalRepository stockSucursalRepository;
    private final ProductoVarianteStockSucursalRepository varianteStockSucursalRepository;
    private final com.stockflow.service.StockLoteService stockLoteService;

    /**
     * ✅ ACTUALIZADO: Obtiene productos del tenant actual automáticamente
     */
    @GetMapping
    @PreAuthorize("hasRole('ADMIN') or hasAuthority('PERM_VER_PRODUCTOS')")
    public ResponseEntity<List<ProductoDTO>> obtenerTodos(
            @RequestParam(required = false) Long sucursalId) {
        String tenantId = TenantContext.getCurrentTenant();
        log.info("📦 Obteniendo productos para tenant: {} sucursalId={}", tenantId, sucursalId);

        List<ProductoDTO> dtos = productoMapper.toDTOList(
                productoService.obtenerProductosPorTenant(tenantId));

        // Si viene sucursalId, sobreescribir stockActual con el stock de esa sucursal
        if (sucursalId != null) {
            // Productos sin variantes → producto_stock_sucursal
            Map<Long, Integer> stockSinVariante = stockSucursalRepository
                    .findBySucursalIdAndTenantId(sucursalId, tenantId)
                    .stream()
                    .collect(Collectors.toMap(
                            s -> s.getProducto().getId(),
                            s -> s.getStockActual()
                    ));

            // Productos CON variantes → suma de producto_variante_stock_sucursal
            Map<Long, Integer> stockConVariante = varianteStockSucursalRepository
                    .sumStockPorProductoYSucursal(sucursalId, tenantId)
                    .stream()
                    .collect(Collectors.toMap(
                            row -> ((Number) row[0]).longValue(),
                            row -> ((Number) row[1]).intValue()
                    ));

            dtos.forEach(dto -> {
                if (dto.getId() == null) return;
                if (stockConVariante.containsKey(dto.getId())) {
                    // Tiene variantes: usar suma de pvss
                    dto.setStockActual(stockConVariante.get(dto.getId()));
                } else if (stockSinVariante.containsKey(dto.getId())) {
                    // Sin variantes: usar producto_stock_sucursal
                    dto.setStockActual(stockSinVariante.get(dto.getId()));
                }
            });
        }

        // Enriquecer con la próxima fecha de vencimiento de cada lote (1 query extra)
        Map<Long, LocalDate> vencMap = movimientoRepository
                .findProximaFechaVencimientoPorProducto(tenantId, LocalDate.now())
                .stream()
                .collect(Collectors.toMap(
                        row -> (Long) row[0],
                        row -> (LocalDate) row[1]
                ));
        dtos.forEach(dto -> {
            if (dto.getId() != null) dto.setProximaFechaVencimiento(vencMap.get(dto.getId()));
        });

        // Enriquecer con stock vigente (no vencido) para productos que tienen lotes FEFO
        try {
            List<Long> todosIds = dtos.stream()
                    .filter(d -> d.getId() != null).map(ProductoDTO::getId).collect(Collectors.toList());
            Map<Long, Integer> stockVigenteMap = stockLoteService.getStockVigenteBatch(tenantId, todosIds, sucursalId);
            dtos.forEach(dto -> {
                if (dto.getId() != null && stockVigenteMap.containsKey(dto.getId())) {
                    dto.setStockVigente(stockVigenteMap.get(dto.getId()));
                }
            });
        } catch (Exception e) {
            log.warn("⚠️ No se pudo enriquecer stockVigente: {}", e.getMessage());
        }

        return ResponseEntity.ok(dtos);
    }

    @GetMapping("/{id}")
    @PreAuthorize("hasRole('ADMIN') or hasAuthority('PERM_VER_PRODUCTOS')")
    public ResponseEntity<ProductoDTO> obtenerPorId(@PathVariable Long id) {
        String tenantId = TenantContext.getCurrentTenant();
        return productoService.obtenerProductoPorId(id)
                .filter(p -> tenantId.equals(p.getTenantId()))
                .map(productoMapper::toDTO)
                .map(ResponseEntity::ok)
                .orElse(ResponseEntity.notFound().build());
    }

    @GetMapping("/codigo/{codigoBarras}")
    @PreAuthorize("hasRole('ADMIN') or hasAuthority('PERM_VER_PRODUCTOS')")
    public ResponseEntity<ProductoDTO> obtenerPorCodigoBarras(@PathVariable String codigoBarras) {
        return productoService.obtenerProductoPorCodigoBarras(codigoBarras)
                .map(productoMapper::toDTO)
                .map(ResponseEntity::ok)
                .orElse(ResponseEntity.notFound().build());
    }

    @GetMapping("/buscar")
    @PreAuthorize("hasRole('ADMIN') or hasAuthority('PERM_VER_PRODUCTOS')")
    public ResponseEntity<List<ProductoDTO>> buscarPorNombre(@RequestParam String nombre) {
        String tenantId = TenantContext.getCurrentTenant();
        return ResponseEntity.ok(
                productoMapper.toDTOList(productoService.buscarProductosPorNombre(nombre, tenantId))
        );
    }

    @GetMapping("/bajo-stock")
    @PreAuthorize("hasRole('ADMIN') or hasAuthority('PERM_VER_PRODUCTOS')")
    public ResponseEntity<List<ProductoDTO>> obtenerProductosBajoStock() {
        String tenantId = TenantContext.getCurrentTenant();
        log.info("⚠️ Obteniendo productos con bajo stock para tenant: {}", tenantId);

        return ResponseEntity.ok(
                productoMapper.toDTOList(productoService.obtenerProductosBajoStock(tenantId))
        );
    }

    @PostMapping
    @PreAuthorize("hasAnyRole('ADMIN', 'GESTOR_INVENTARIO') or hasAuthority('PERM_CREAR_PRODUCTO')")
    public ResponseEntity<ProductoDTO> crear(@Valid @RequestBody ProductoDTO productoDTO) {
        String tenantId = TenantContext.getCurrentTenant();
        log.info("➕ Creando producto para tenant: {}", tenantId);
        log.info("🖼️ imagenUrl recibida en crear: {}", productoDTO.getImagenUrl() != null
                ? "SÍ (" + productoDTO.getImagenUrl().length() + " chars)" : "NO");

        productoDTO.setTenantId(tenantId);

        Producto producto = productoMapper.toEntity(productoDTO);
        // Setters explícitos por si el mapper fue compilado antes de agregar estos campos
        producto.setImagenUrl(productoDTO.getImagenUrl());
        producto.setEsGenerico(productoDTO.getEsGenerico() != null ? productoDTO.getEsGenerico() : false);
        producto.setUnidadesPorCaja(productoDTO.getUnidadesPorCaja());
        Producto productoCreado = productoService.crearProducto(producto);

        return ResponseEntity.status(HttpStatus.CREATED)
                .body(productoMapper.toDTO(productoCreado));
    }

    @PutMapping("/{id}")
    @PreAuthorize("hasAnyRole('ADMIN', 'GESTOR_INVENTARIO') or hasAuthority('PERM_EDITAR_PRODUCTO')")
    public ResponseEntity<ProductoDTO> actualizar(
            @PathVariable Long id,
            @Valid @RequestBody ProductoDTO productoDTO) {
        String tenantId = TenantContext.getCurrentTenant();
        log.info("✏️ Actualizando producto ID: {}", id);
        log.info("🖼️ imagenUrl recibida en actualizar: {}", productoDTO.getImagenUrl() != null
                ? "SÍ (" + productoDTO.getImagenUrl().length() + " chars)" : "NO");

        return productoService.obtenerProductoPorId(id)
                .filter(p -> tenantId.equals(p.getTenantId()))
                .map(productoExistente -> {
                    productoMapper.updateEntityFromDTO(productoDTO, productoExistente);
                    // Setters explícitos por si el mapper fue compilado antes de agregar estos campos
                    productoExistente.setImagenUrl(productoDTO.getImagenUrl());
                    productoExistente.setEsGenerico(productoDTO.getEsGenerico() != null ? productoDTO.getEsGenerico() : false);
                    productoExistente.setUnidadesPorCaja(productoDTO.getUnidadesPorCaja());
                    Producto productoActualizado = productoService.actualizarProducto(id, productoExistente);
                    return ResponseEntity.ok(productoMapper.toDTO(productoActualizado));
                })
                .orElse(ResponseEntity.notFound().build());
    }

    @DeleteMapping("/{id}")
    @PreAuthorize("hasRole('ADMIN') or hasAuthority('PERM_ELIMINAR_PRODUCTO')")
    public ResponseEntity<Void> eliminar(@PathVariable Long id) {
        String tenantId = TenantContext.getCurrentTenant();
        log.info("🗑️ Eliminando producto ID: {}", id);
        // Verificar que el producto pertenezca al tenant antes de eliminar
        boolean pertenece = productoService.obtenerProductoPorId(id)
                .map(p -> tenantId.equals(p.getTenantId()))
                .orElse(false);
        if (!pertenece) return ResponseEntity.notFound().build();
        productoService.eliminarProducto(id);
        return ResponseEntity.noContent().build();
    }

    /**
     * POST /api/productos/importar
     * Importación masiva desde Excel/CSV (parseado en frontend, enviado como JSON).
     * Crea nuevos productos o actualiza los existentes por codigoBarras.
     */
    @PostMapping("/importar")
    @PreAuthorize("hasRole('ADMIN') or hasAuthority('PERM_CREAR_PRODUCTO')")
    public ResponseEntity<ProductoImportResultDTO> importar(
            @RequestBody List<ProductoImportRowDTO> filas) {
        String tenantId = TenantContext.getCurrentTenant();
        log.info("📥 Importando {} productos para tenant={}", filas.size(), tenantId);
        if (filas.isEmpty()) {
            return ResponseEntity.badRequest().build();
        }
        ProductoImportResultDTO result = productoService.importar(filas, tenantId);
        return ResponseEntity.ok(result);
    }
}