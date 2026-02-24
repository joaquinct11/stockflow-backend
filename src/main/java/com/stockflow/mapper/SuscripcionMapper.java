package com.stockflow.mapper;

import com.stockflow.dto.SuscripcionDTO;
import com.stockflow.entity.Suscripcion;
import org.mapstruct.Mapper;
import org.mapstruct.Mapping;
import org.mapstruct.MappingTarget;
import java.util.List;

@Mapper(componentModel = "spring")
public interface SuscripcionMapper {

    @Mapping(source = "usuarioPrincipal.id", target = "usuarioPrincipalId")
    SuscripcionDTO toDTO(Suscripcion suscripcion);

    @Mapping(target = "id", ignore = true)
    @Mapping(target = "usuarioPrincipal", ignore = true)
    @Mapping(target = "fechaInicio", expression = "java(java.time.LocalDateTime.now())")
    @Mapping(target = "intentosFallidos", defaultValue = "0")
    Suscripcion toEntity(SuscripcionDTO dto);

    List<SuscripcionDTO> toDTOList(List<Suscripcion> suscripciones);

    @Mapping(target = "id", ignore = true)
    @Mapping(target = "usuarioPrincipal", ignore = true)
    @Mapping(target = "fechaInicio", ignore = true)
    @Mapping(target = "preapprovalId", ignore = true)
    void updateEntityFromDTO(SuscripcionDTO dto, @MappingTarget Suscripcion suscripcion);
}