package com.stockflow.mapper;

import com.stockflow.dto.ProveedorDTO;
import com.stockflow.entity.Proveedor;
import org.mapstruct.Mapper;
import org.mapstruct.Mapping;
import org.mapstruct.MappingTarget;
import java.util.List;

@Mapper(componentModel = "spring")
public interface ProveedorMapper {

    ProveedorDTO toDTO(Proveedor proveedor);

    @Mapping(target = "id", ignore = true)
    @Mapping(target = "createdAt", expression = "java(java.time.LocalDateTime.now())")
    @Mapping(target = "activo", defaultValue = "true")
    Proveedor toEntity(ProveedorDTO dto);

    List<ProveedorDTO> toDTOList(List<Proveedor> proveedores);

    @Mapping(target = "id", ignore = true)
    @Mapping(target = "createdAt", ignore = true)
    void updateEntityFromDTO(ProveedorDTO dto, @MappingTarget Proveedor proveedor);
}