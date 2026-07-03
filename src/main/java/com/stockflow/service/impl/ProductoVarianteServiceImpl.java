package com.stockflow.service.impl;

import com.stockflow.dto.ProductoVarianteDTO;
import com.stockflow.entity.ProductoVariante;
import com.stockflow.exception.BadRequestException;
import com.stockflow.exception.ResourceNotFoundException;
import com.stockflow.repository.ProductoRepository;
import com.stockflow.repository.ProductoVarianteRepository;
import com.stockflow.service.ProductoVarianteService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.List;

@Slf4j
@Service
@RequiredArgsConstructor
public class ProductoVarianteServiceImpl implements ProductoVarianteService {

    private final ProductoVarianteRepository varianteRepository;
    private final ProductoRepository productoRepository;
    private final com.stockflow.repository.ProductoVarianteStockSucursalRepository varianteStockSucursalRepository;

    @Override
    public List<ProductoVarianteDTO> getByProducto(Long productoId, String tenantId, Long sucursalId) {
        List<ProductoVarianteDTO> variantes = varianteRepository.findByProductoIdAndTenantId(productoId, tenantId)
                .stream().map(this::toDTO).toList();

        if (sucursalId != null) {
            java.util.Map<Long, Integer> stockPorVariante = varianteStockSucursalRepository
                    .findByVarianteIdInAndSucursalId(
                            variantes.stream().map(ProductoVarianteDTO::getId).toList(),
                            sucursalId)
                    .stream()
                    .collect(java.util.stream.Collectors.toMap(
                            pvss -> pvss.getVarianteId(),
                            pvss -> pvss.getStockActual() != null ? pvss.getStockActual() : 0
                    ));
            variantes.forEach(v -> v.setStockActual(stockPorVariante.getOrDefault(v.getId(), 0)));
        }

        return variantes;
    }

    @Override
    @Transactional
    public ProductoVarianteDTO create(ProductoVarianteDTO dto, String tenantId) {
        productoRepository.findById(dto.getProductoId())
                .filter(p -> tenantId.equals(p.getTenantId()))
                .orElseThrow(() -> new ResourceNotFoundException("Producto no encontrado"));

        if (dto.getTalla() == null && dto.getColor() == null && dto.getSku() == null) {
            throw new BadRequestException("La variante debe tener al menos talla, color o SKU");
        }

        ProductoVariante variante = ProductoVariante.builder()
                .productoId(dto.getProductoId())
                .talla(dto.getTalla())
                .color(dto.getColor())
                .stockActual(dto.getStockActual() != null ? dto.getStockActual() : 0)
                .stockMinimo(dto.getStockMinimo() != null ? dto.getStockMinimo() : 0)
                .sku(dto.getSku())
                .activo(dto.getActivo() != null ? dto.getActivo() : true)
                .tenantId(tenantId)
                .createdAt(LocalDateTime.now())
                .build();

        ProductoVariante saved = varianteRepository.save(variante);
        syncProductoStock(dto.getProductoId(), tenantId);
        log.info("✅ Variante creada: producto={} talla={} color={}", dto.getProductoId(), dto.getTalla(), dto.getColor());
        return toDTO(saved);
    }

    @Override
    @Transactional
    public ProductoVarianteDTO update(Long id, ProductoVarianteDTO dto, String tenantId) {
        ProductoVariante variante = varianteRepository.findByIdAndTenantId(id, tenantId)
                .orElseThrow(() -> new ResourceNotFoundException("Variante no encontrada"));

        variante.setTalla(dto.getTalla());
        variante.setColor(dto.getColor());
        variante.setSku(dto.getSku());
        if (dto.getStockActual() != null) variante.setStockActual(dto.getStockActual());
        if (dto.getStockMinimo() != null) variante.setStockMinimo(dto.getStockMinimo());
        if (dto.getActivo() != null) variante.setActivo(dto.getActivo());

        ProductoVariante saved = varianteRepository.save(variante);
        syncProductoStock(variante.getProductoId(), tenantId);
        return toDTO(saved);
    }

    @Override
    @Transactional
    public void delete(Long id, String tenantId) {
        ProductoVariante variante = varianteRepository.findByIdAndTenantId(id, tenantId)
                .orElseThrow(() -> new ResourceNotFoundException("Variante no encontrada"));
        Long productoId = variante.getProductoId();
        varianteRepository.delete(variante);
        syncProductoStock(productoId, tenantId);
    }

    // Mantiene productos.stock_actual = suma de todas las variantes activas
    private void syncProductoStock(Long productoId, String tenantId) {
        int total = varianteRepository.findByProductoIdAndActivoTrueAndTenantId(productoId, tenantId)
                .stream().mapToInt(v -> v.getStockActual() != null ? v.getStockActual() : 0).sum();
        productoRepository.findById(productoId).ifPresent(p -> {
            p.setStockActual(total);
            productoRepository.save(p);
        });
    }

    private ProductoVarianteDTO toDTO(ProductoVariante v) {
        return ProductoVarianteDTO.builder()
                .id(v.getId())
                .productoId(v.getProductoId())
                .talla(v.getTalla())
                .color(v.getColor())
                .stockActual(v.getStockActual())
                .stockMinimo(v.getStockMinimo())
                .sku(v.getSku())
                .activo(v.getActivo())
                .tenantId(v.getTenantId())
                .build();
    }
}
