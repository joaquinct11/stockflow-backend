package com.stockflow.mapper;

import com.stockflow.dto.RolDTO;
import com.stockflow.entity.Rol;
import org.mapstruct.Mapper;
import org.mapstruct.Mapping;
import org.mapstruct.MappingTarget;
import java.util.List;

@Mapper(componentModel = "spring")
public interface RolMapper {

    RolDTO toDTO(Rol rol);

    @Mapping(target = "id", ignore = true)
    @Mapping(target = "createdAt", expression = "java(java.time.LocalDateTime.now())")
    Rol toEntity(RolDTO dto);

    List<RolDTO> toDTOList(List<Rol> roles);

    @Mapping(target = "id", ignore = true)
    @Mapping(target = "createdAt", ignore = true)
    void updateEntityFromDTO(RolDTO dto, @MappingTarget Rol rol);
}