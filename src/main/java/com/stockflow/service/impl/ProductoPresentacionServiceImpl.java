package com.stockflow.service.impl;

import com.stockflow.dto.ProductoPresentacionDTO;
import com.stockflow.entity.Producto;
import com.stockflow.entity.ProductoPresentacion;
import com.stockflow.entity.UnidadMedida;
import com.stockflow.repository.ProductoPresentacionRepository;
import com.stockflow.repository.ProductoRepository;
import com.stockflow.repository.UnidadMedidaRepository;
import com.stockflow.service.ProductoPresentacionService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

import java.util.List;

@Service
@RequiredArgsConstructor
public class ProductoPresentacionServiceImpl implements ProductoPresentacionService {

    private final ProductoPresentacionRepository repo;
    private final ProductoRepository productoRepo;
    private final UnidadMedidaRepository unidadRepo;

    @Override
    public List<ProductoPresentacionDTO> listarPorProducto(Long productoId, String tenantId) {
        return repo.findByProductoIdAndTenantId(productoId, tenantId)
                .stream()
                .map(this::toDTO)
                .toList();
    }

    @Override
    @Transactional
    public ProductoPresentacionDTO crear(Long productoId, ProductoPresentacionDTO dto, String tenantId) {
        Producto producto = productoRepo.findById(productoId)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Producto no encontrado"));

        if (!producto.getTenantId().equals(tenantId)) {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN, "Sin acceso al producto");
        }

        UnidadMedida unidad = unidadRepo.findById(dto.getUnidadMedidaId())
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Unidad de medida no encontrada"));

        if (repo.existsByProductoIdAndUnidadMedidaId(productoId, dto.getUnidadMedidaId())) {
            throw new ResponseStatusException(HttpStatus.CONFLICT,
                    "Ya existe una presentación con esa unidad de medida para este producto");
        }

        // Si se marca como principal, desmarcar las demás
        if (Boolean.TRUE.equals(dto.getEsPrincipal())) {
            repo.findByProductoIdAndTenantId(productoId, tenantId)
                    .forEach(p -> { p.setEsPrincipal(false); repo.save(p); });
        }

        ProductoPresentacion presentacion = ProductoPresentacion.builder()
                .producto(producto)
                .unidadMedida(unidad)
                .precioVenta(dto.getPrecioVenta())
                .factor(dto.getFactor() != null ? dto.getFactor() : 1)
                .esPrincipal(dto.getEsPrincipal() != null && dto.getEsPrincipal())
                .tenantId(tenantId)
                .build();

        return toDTO(repo.save(presentacion));
    }

    @Override
    @Transactional
    public ProductoPresentacionDTO actualizar(Long id, ProductoPresentacionDTO dto, String tenantId) {
        ProductoPresentacion presentacion = repo.findById(id)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Presentación no encontrada"));

        if (!tenantId.equals(presentacion.getTenantId())) {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN, "Sin acceso");
        }

        UnidadMedida unidad = unidadRepo.findById(dto.getUnidadMedidaId())
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Unidad de medida no encontrada"));

        // Verificar unicidad si cambió la unidad
        if (!presentacion.getUnidadMedida().getId().equals(dto.getUnidadMedidaId())
                && repo.existsByProductoIdAndUnidadMedidaId(presentacion.getProducto().getId(), dto.getUnidadMedidaId())) {
            throw new ResponseStatusException(HttpStatus.CONFLICT,
                    "Ya existe una presentación con esa unidad de medida para este producto");
        }

        if (Boolean.TRUE.equals(dto.getEsPrincipal())) {
            repo.findByProductoIdAndTenantId(presentacion.getProducto().getId(), tenantId)
                    .forEach(p -> { p.setEsPrincipal(false); repo.save(p); });
        }

        presentacion.setUnidadMedida(unidad);
        presentacion.setPrecioVenta(dto.getPrecioVenta());
        presentacion.setFactor(dto.getFactor() != null ? dto.getFactor() : 1);
        presentacion.setEsPrincipal(dto.getEsPrincipal() != null && dto.getEsPrincipal());

        return toDTO(repo.save(presentacion));
    }

    @Override
    @Transactional
    public void eliminar(Long id, String tenantId) {
        ProductoPresentacion presentacion = repo.findById(id)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Presentación no encontrada"));

        if (!tenantId.equals(presentacion.getTenantId())) {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN, "Sin acceso");
        }

        repo.delete(presentacion);
    }

    private ProductoPresentacionDTO toDTO(ProductoPresentacion p) {
        return ProductoPresentacionDTO.builder()
                .id(p.getId())
                .productoId(p.getProducto().getId())
                .unidadMedidaId(p.getUnidadMedida().getId())
                .unidadMedidaNombre(p.getUnidadMedida().getNombre())
                .unidadMedidaAbreviatura(p.getUnidadMedida().getAbreviatura())
                .precioVenta(p.getPrecioVenta())
                .factor(p.getFactor())
                .esPrincipal(p.getEsPrincipal())
                .tenantId(p.getTenantId())
                .build();
    }
}
