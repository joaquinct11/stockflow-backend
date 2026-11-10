package com.stockflow.mapper;

import com.stockflow.dto.MascotaDTO;
import com.stockflow.entity.Mascota;
import org.mapstruct.Mapper;
import org.mapstruct.Mapping;

import java.util.List;

@Mapper(componentModel = "spring")
public interface MascotaMapper {

    @Mapping(source = "cliente.id", target = "clienteId")
    MascotaDTO toDTO(Mascota mascota);

    List<MascotaDTO> toDTOList(List<Mascota> mascotas);
}
