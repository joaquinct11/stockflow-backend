package com.stockflow.mapper;

import com.stockflow.dto.GastoDTO;
import com.stockflow.entity.Gasto;
import org.mapstruct.Mapper;
import org.mapstruct.Mapping;
import org.mapstruct.MappingTarget;

import java.util.List;

@Mapper(componentModel = "spring")
public interface GastoMapper {

    GastoDTO toDTO(Gasto gasto);

    @Mapping(target = "id", ignore = true)
    @Mapping(target = "createdAt", expression = "java(java.time.LocalDateTime.now())")
    @Mapping(target = "activo", constant = "true")
    @Mapping(target = "deletedAt", ignore = true)
    Gasto toEntity(GastoDTO dto);

    List<GastoDTO> toDTOList(List<Gasto> gastos);

    @Mapping(target = "id", ignore = true)
    @Mapping(target = "createdAt", ignore = true)
    @Mapping(target = "deletedAt", ignore = true)
    @Mapping(target = "tenantId", ignore = true)
    @Mapping(target = "registradoPor", ignore = true)
    void updateEntityFromDTO(GastoDTO dto, @MappingTarget Gasto gasto);
}
