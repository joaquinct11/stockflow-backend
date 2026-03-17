package com.stockflow.mapper;

import com.stockflow.dto.ProductoDTO;
import com.stockflow.entity.Producto;
import com.stockflow.entity.UnidadMedida;
import org.mapstruct.Mapper;
import org.mapstruct.Mapping;
import org.mapstruct.MappingTarget;
import java.util.List;

@Mapper(componentModel = "spring")
public interface ProductoMapper {

    // ENTITY → DTO
    @Mapping(source = "unidadMedida.id", target = "unidadMedidaId")
    ProductoDTO toDTO(Producto producto);

    // DTO → ENTITY
    @Mapping(target = "id", ignore = true)
    @Mapping(target = "createdAt", expression = "java(java.time.LocalDateTime.now())")
    @Mapping(target = "stockActual", defaultValue = "0")
    @Mapping(target = "stockMinimo", defaultValue = "10")
    @Mapping(target = "stockMaximo", defaultValue = "500")
    @Mapping(target = "activo", defaultValue = "true")
    @Mapping(source = "unidadMedidaId", target = "unidadMedida")
    Producto toEntity(ProductoDTO dto);

    List<ProductoDTO> toDTOList(List<Producto> productos);

    @Mapping(target = "id", ignore = true)
    @Mapping(target = "createdAt", ignore = true)
    @Mapping(source = "unidadMedidaId", target = "unidadMedida")
    void updateEntityFromDTO(ProductoDTO dto, @MappingTarget Producto producto);

    // 🔑 Long → Objeto
    default UnidadMedida map(Long id) {
        if (id == null) return null;
        UnidadMedida unidad = new UnidadMedida();
        unidad.setId(id);
        return unidad;
    }

    // 🔑 Objeto → Long
    default Long map(UnidadMedida unidad) {
        return unidad != null ? unidad.getId() : null;
    }
}