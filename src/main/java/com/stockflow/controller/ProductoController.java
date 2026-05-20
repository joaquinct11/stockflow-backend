package com.stockflow.controller;

import com.stockflow.dto.ProductoDTO;
import com.stockflow.dto.ProductoImportResultDTO;
import com.stockflow.dto.ProductoImportRowDTO;
import com.stockflow.entity.Producto;
import com.stockflow.mapper.ProductoMapper;
import com.stockflow.service.ProductoService;
import com.stockflow.util.TenantContext;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;
import jakarta.validation.Valid;
import java.util.List;

@Slf4j
@RestController
@RequestMapping("/productos")
@RequiredArgsConstructor
public class ProductoController {

    private final ProductoService productoService;
    private final ProductoMapper productoMapper;

    /**
     * ✅ ACTUALIZADO: Obtiene productos del tenant actual automáticamente
     */
    @GetMapping
    @PreAuthorize("hasRole('ADMIN') or hasAuthority('PERM_VER_PRODUCTOS')")
    public ResponseEntity<List<ProductoDTO>> obtenerTodos() {
        String tenantId = TenantContext.getCurrentTenant();
        log.info("📦 Obteniendo productos para tenant: {}", tenantId);

        return ResponseEntity.ok(
                productoMapper.toDTOList(productoService.obtenerProductosPorTenant(tenantId))
        );
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

        // Setear tenantId del contexto
        productoDTO.setTenantId(tenantId);

        Producto producto = productoMapper.toEntity(productoDTO);
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

        return productoService.obtenerProductoPorId(id)
                .filter(p -> tenantId.equals(p.getTenantId()))
                .map(productoExistente -> {
                    productoMapper.updateEntityFromDTO(productoDTO, productoExistente);
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