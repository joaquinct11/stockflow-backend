package com.stockflow.mapper;

import com.stockflow.dto.ClienteDTO;
import com.stockflow.entity.Cliente;
import org.mapstruct.Mapper;
import org.mapstruct.Mapping;
import org.mapstruct.MappingTarget;

import java.util.List;

@Mapper(componentModel = "spring")
public interface ClienteMapper {

    ClienteDTO toDTO(Cliente cliente);

    @Mapping(target = "id", ignore = true)
    @Mapping(target = "createdAt", expression = "java(java.time.LocalDateTime.now())")
    @Mapping(target = "activo", defaultValue = "true")
    Cliente toEntity(ClienteDTO dto);

    List<ClienteDTO> toDTOList(List<Cliente> clientes);

    @Mapping(target = "id", ignore = true)
    @Mapping(target = "createdAt", ignore = true)
    void updateEntityFromDTO(ClienteDTO dto, @MappingTarget Cliente cliente);
}
