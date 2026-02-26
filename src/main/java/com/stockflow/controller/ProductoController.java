package com.stockflow.controller;

import com.stockflow.dto.ProductoDTO;
import com.stockflow.entity.Producto;
import com.stockflow.mapper.ProductoMapper;
import com.stockflow.service.ProductoService;
import com.stockflow.util.TenantContext;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
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
    public ResponseEntity<List<ProductoDTO>> obtenerTodos() {
        String tenantId = TenantContext.getCurrentTenant();
        log.info("📦 Obteniendo productos para tenant: {}", tenantId);

        return ResponseEntity.ok(
                productoMapper.toDTOList(productoService.obtenerProductosPorTenant(tenantId))
        );
    }

    @GetMapping("/{id}")
    public ResponseEntity<ProductoDTO> obtenerPorId(@PathVariable Long id) {
        return productoService.obtenerProductoPorId(id)
                .map(productoMapper::toDTO)
                .map(ResponseEntity::ok)
                .orElse(ResponseEntity.notFound().build());
    }

    @GetMapping("/codigo/{codigoBarras}")
    public ResponseEntity<ProductoDTO> obtenerPorCodigoBarras(@PathVariable String codigoBarras) {
        return productoService.obtenerProductoPorCodigoBarras(codigoBarras)
                .map(productoMapper::toDTO)
                .map(ResponseEntity::ok)
                .orElse(ResponseEntity.notFound().build());
    }

    @GetMapping("/buscar")
    public ResponseEntity<List<ProductoDTO>> buscarPorNombre(@RequestParam String nombre) {
        return ResponseEntity.ok(
                productoMapper.toDTOList(productoService.buscarProductosPorNombre(nombre))
        );
    }

    /**
     * ✅ ACTUALIZADO: Usa tenantId automáticamente
     */
    @GetMapping("/bajo-stock")
    public ResponseEntity<List<ProductoDTO>> obtenerProductosBajoStock() {
        String tenantId = TenantContext.getCurrentTenant();
        log.info("⚠️ Obteniendo productos con bajo stock para tenant: {}", tenantId);

        return ResponseEntity.ok(
                productoMapper.toDTOList(productoService.obtenerProductosBajoStock(tenantId))
        );
    }

    /**
     * ✅ ACTUALIZADO: Setea tenantId automáticamente al crear
     */
    @PostMapping
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
    public ResponseEntity<ProductoDTO> actualizar(
            @PathVariable Long id,
            @Valid @RequestBody ProductoDTO productoDTO) {
        log.info("✏️ Actualizando producto ID: {}", id);

        return productoService.obtenerProductoPorId(id)
                .map(productoExistente -> {
                    productoMapper.updateEntityFromDTO(productoDTO, productoExistente);
                    Producto productoActualizado = productoService.actualizarProducto(id, productoExistente);
                    return ResponseEntity.ok(productoMapper.toDTO(productoActualizado));
                })
                .orElse(ResponseEntity.notFound().build());
    }

    @DeleteMapping("/{id}")
    public ResponseEntity<Void> eliminar(@PathVariable Long id) {
        log.info("🗑️ Eliminando producto ID: {}", id);
        productoService.eliminarProducto(id);
        return ResponseEntity.noContent().build();
    }
}