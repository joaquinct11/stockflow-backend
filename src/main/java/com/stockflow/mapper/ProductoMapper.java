package com.stockflow.mapper;

import com.stockflow.dto.ProductoDTO;
import com.stockflow.entity.Producto;
import org.mapstruct.Mapper;
import org.mapstruct.Mapping;
import org.mapstruct.MappingTarget;
import java.util.List;

@Mapper(componentModel = "spring")
public interface ProductoMapper {

    ProductoDTO toDTO(Producto producto);

    @Mapping(target = "id", ignore = true)
    @Mapping(target = "createdAt", expression = "java(java.time.LocalDateTime.now())")
    @Mapping(target = "stockActual", defaultValue = "0")
    @Mapping(target = "stockMinimo", defaultValue = "10")
    @Mapping(target = "stockMaximo", defaultValue = "500")
    @Mapping(target = "activo", defaultValue = "true")
    Producto toEntity(ProductoDTO dto);

    List<ProductoDTO> toDTOList(List<Producto> productos);

    @Mapping(target = "id", ignore = true)
    @Mapping(target = "createdAt", ignore = true)
    void updateEntityFromDTO(ProductoDTO dto, @MappingTarget Producto producto);
}