package com.stockflow.mapper;

import com.stockflow.dto.EquipoClienteDTO;
import com.stockflow.entity.EquipoCliente;
import org.mapstruct.Mapper;
import org.mapstruct.Mapping;

import java.util.List;

@Mapper(componentModel = "spring")
public interface EquipoClienteMapper {

    @Mapping(source = "cliente.id", target = "clienteId")
    @Mapping(source = "cliente.nombre", target = "clienteNombre")
    EquipoClienteDTO toDTO(EquipoCliente equipo);

    List<EquipoClienteDTO> toDTOList(List<EquipoCliente> equipos);
}
