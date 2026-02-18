package com.stockflow.controller;

import com.stockflow.dto.ProductoDTO;
import com.stockflow.entity.Producto;
import com.stockflow.service.ProductoService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import jakarta.validation.Valid;
import java.util.List;
import java.util.stream.Collectors;

@RestController
@RequestMapping("/productos")
@RequiredArgsConstructor
public class ProductoController {

    private final ProductoService productoService;

    private ProductoDTO convertToDTO(Producto producto) {
        return ProductoDTO.builder()
                .id(producto.getId())
                .nombre(producto.getNombre())
                .codigoBarras(producto.getCodigoBarras())
                .categoria(producto.getCategoria())
                .stockActual(producto.getStockActual())
                .stockMinimo(producto.getStockMinimo())
                .stockMaximo(producto.getStockMaximo())
                .costoUnitario(producto.getCostoUnitario())
                .precioVenta(producto.getPrecioVenta())
                .fechaVencimiento(producto.getFechaVencimiento())
                .lote(producto.getLote())
                .activo(producto.getActivo())
                .tenantId(producto.getTenantId())
                .build();
    }

    private Producto convertToEntity(ProductoDTO dto) {
        return Producto.builder()
                .nombre(dto.getNombre())
                .codigoBarras(dto.getCodigoBarras())
                .categoria(dto.getCategoria())
                .stockActual(dto.getStockActual() != null ? dto.getStockActual() : 0)
                .stockMinimo(dto.getStockMinimo() != null ? dto.getStockMinimo() : 10)
                .stockMaximo(dto.getStockMaximo() != null ? dto.getStockMaximo() : 500)
                .costoUnitario(dto.getCostoUnitario())
                .precioVenta(dto.getPrecioVenta())
                .fechaVencimiento(dto.getFechaVencimiento())
                .lote(dto.getLote())
                .activo(dto.getActivo() != null ? dto.getActivo() : true)
                .tenantId(dto.getTenantId())
                .build();
    }

    @GetMapping
    public ResponseEntity<List<ProductoDTO>> obtenerTodos() {
        List<Producto> productos = productoService.obtenerProductosActivos();
        List<ProductoDTO> productosDTO = productos.stream()
                .map(this::convertToDTO)
                .collect(Collectors.toList());
        return ResponseEntity.ok(productosDTO);
    }

    @GetMapping("/{id}")
    public ResponseEntity<ProductoDTO> obtenerPorId(@PathVariable Long id) {
        return productoService.obtenerProductoPorId(id)
                .map(this::convertToDTO)
                .map(ResponseEntity::ok)
                .orElse(ResponseEntity.notFound().build());
    }

    @GetMapping("/codigo/{codigoBarras}")
    public ResponseEntity<ProductoDTO> obtenerPorCodigoBarras(@PathVariable String codigoBarras) {
        return productoService.obtenerProductoPorCodigoBarras(codigoBarras)
                .map(this::convertToDTO)
                .map(ResponseEntity::ok)
                .orElse(ResponseEntity.notFound().build());
    }

    @GetMapping("/buscar")
    public ResponseEntity<List<ProductoDTO>> buscarPorNombre(@RequestParam String nombre) {
        List<Producto> productos = productoService.buscarProductosPorNombre(nombre);
        List<ProductoDTO> productosDTO = productos.stream()
                .map(this::convertToDTO)
                .collect(Collectors.toList());
        return ResponseEntity.ok(productosDTO);
    }

    @GetMapping("/tenant/{tenantId}")
    public ResponseEntity<List<ProductoDTO>> obtenerPorTenant(@PathVariable String tenantId) {
        List<Producto> productos = productoService.obtenerProductosPorTenant(tenantId);
        List<ProductoDTO> productosDTO = productos.stream()
                .map(this::convertToDTO)
                .collect(Collectors.toList());
        return ResponseEntity.ok(productosDTO);
    }

    @GetMapping("/tenant/{tenantId}/bajo-stock")
    public ResponseEntity<List<ProductoDTO>> obtenerProductosBajoStock(@PathVariable String tenantId) {
        List<Producto> productos = productoService.obtenerProductosBajoStock(tenantId);
        List<ProductoDTO> productosDTO = productos.stream()
                .map(this::convertToDTO)
                .collect(Collectors.toList());
        return ResponseEntity.ok(productosDTO);
    }

    @PostMapping
    public ResponseEntity<ProductoDTO> crear(@Valid @RequestBody ProductoDTO productoDTO) {
        Producto producto = convertToEntity(productoDTO);
        Producto productoCreado = productoService.crearProducto(producto);
        return ResponseEntity.status(HttpStatus.CREATED).body(convertToDTO(productoCreado));
    }

    @PutMapping("/{id}")
    public ResponseEntity<ProductoDTO> actualizar(@PathVariable Long id, @Valid @RequestBody ProductoDTO productoDTO) {
        try {
            Producto producto = convertToEntity(productoDTO);
            Producto productoActualizado = productoService.actualizarProducto(id, producto);
            return ResponseEntity.ok(convertToDTO(productoActualizado));
        } catch (RuntimeException e) {
            return ResponseEntity.notFound().build();
        }
    }

    @DeleteMapping("/{id}")
    public ResponseEntity<Void> eliminar(@PathVariable Long id) {
        productoService.eliminarProducto(id);
        return ResponseEntity.noContent().build();
    }
}