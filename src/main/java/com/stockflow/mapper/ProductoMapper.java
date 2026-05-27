package com.stockflow.mapper;

import com.stockflow.dto.ProductoDTO;
import com.stockflow.entity.Categoria;
import com.stockflow.entity.Producto;
import com.stockflow.entity.UnidadMedida;
import org.mapstruct.Mapper;
import org.mapstruct.Mapping;
import org.mapstruct.MappingTarget;
import org.mapstruct.Named;

import java.util.List;

@Mapper(componentModel = "spring")
public interface ProductoMapper {

    // ENTITY → DTO
    @Mapping(source = "unidadMedida.id", target = "unidadMedidaId")
    @Mapping(source = "categoriaRef.id", target = "categoriaId")
    @Mapping(source = "categoriaRef.nombre", target = "categoriaNombre")
    @Mapping(source = "imagenUrl", target = "imagenUrl")
    ProductoDTO toDTO(Producto producto);

    // DTO → ENTITY
    @Mapping(target = "id", ignore = true)
    @Mapping(target = "createdAt", expression = "java(java.time.LocalDateTime.now())")
    @Mapping(target = "stockActual", defaultValue = "0")
    @Mapping(target = "stockMinimo", defaultValue = "10")
    @Mapping(target = "stockMaximo", defaultValue = "500")
    @Mapping(target = "activo", defaultValue = "true")
    @Mapping(source = "unidadMedidaId", target = "unidadMedida", qualifiedByName = "longToUnidadMedida")
    @Mapping(source = "categoriaId", target = "categoriaRef", qualifiedByName = "longToCategoria")
    Producto toEntity(ProductoDTO dto);

    List<ProductoDTO> toDTOList(List<Producto> productos);

    @Mapping(target = "id", ignore = true)
    @Mapping(target = "createdAt", ignore = true)
    @Mapping(source = "unidadMedidaId", target = "unidadMedida", qualifiedByName = "longToUnidadMedida")
    @Mapping(source = "categoriaId", target = "categoriaRef", qualifiedByName = "longToCategoria")
    @Mapping(source = "imagenUrl", target = "imagenUrl")
    void updateEntityFromDTO(ProductoDTO dto, @MappingTarget Producto producto);

    // 🔑 Long → UnidadMedida
    @Named("longToUnidadMedida")
    default UnidadMedida longToUnidadMedida(Long id) {
        if (id == null) return null;
        UnidadMedida unidad = new UnidadMedida();
        unidad.setId(id);
        return unidad;
    }

    // 🔑 UnidadMedida → Long
    default Long map(UnidadMedida unidad) {
        return unidad != null ? unidad.getId() : null;
    }

    // 🔑 Long → Categoria
    @Named("longToCategoria")
    default Categoria longToCategoria(Long id) {
        if (id == null) return null;
        Categoria categoria = new Categoria();
        categoria.setId(id);
        return categoria;
    }

    // 🔑 Categoria → Long (usado implícitamente por MapStruct para categoriaRef.id)
    default Long map(Categoria categoria) {
        return categoria != null ? categoria.getId() : null;
    }
}
