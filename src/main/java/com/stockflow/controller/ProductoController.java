package com.stockflow.controller;

import com.stockflow.dto.ProductoDTO;
import com.stockflow.entity.Producto;
import com.stockflow.mapper.ProductoMapper;
import com.stockflow.service.ProductoService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import jakarta.validation.Valid;
import java.util.List;

@RestController
@RequestMapping("/productos")
@RequiredArgsConstructor
public class ProductoController {

    private final ProductoService productoService;
    private final ProductoMapper productoMapper;

    @GetMapping
    public ResponseEntity<List<ProductoDTO>> obtenerTodos() {
        return ResponseEntity.ok(
                productoMapper.toDTOList(productoService.obtenerProductosActivos())
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

    @GetMapping("/tenant/{tenantId}")
    public ResponseEntity<List<ProductoDTO>> obtenerPorTenant(@PathVariable String tenantId) {
        return ResponseEntity.ok(
                productoMapper.toDTOList(productoService.obtenerProductosPorTenant(tenantId))
        );
    }

    @GetMapping("/tenant/{tenantId}/bajo-stock")
    public ResponseEntity<List<ProductoDTO>> obtenerProductosBajoStock(@PathVariable String tenantId) {
        return ResponseEntity.ok(
                productoMapper.toDTOList(productoService.obtenerProductosBajoStock(tenantId))
        );
    }

    @PostMapping
    public ResponseEntity<ProductoDTO> crear(@Valid @RequestBody ProductoDTO productoDTO) {
        Producto producto = productoMapper.toEntity(productoDTO);
        Producto productoCreado = productoService.crearProducto(producto);
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(productoMapper.toDTO(productoCreado));
    }

    @PutMapping("/{id}")
    public ResponseEntity<ProductoDTO> actualizar(
            @PathVariable Long id,
            @Valid @RequestBody ProductoDTO productoDTO) {
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
        productoService.eliminarProducto(id);
        return ResponseEntity.noContent().build();
    }
}