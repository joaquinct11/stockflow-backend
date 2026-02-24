package com.stockflow.mapper;

import com.stockflow.dto.UsuarioDTO;
import com.stockflow.entity.Usuario;
import org.mapstruct.Mapper;
import org.mapstruct.Mapping;
import org.mapstruct.MappingTarget;
import java.util.List;

@Mapper(componentModel = "spring")
public interface UsuarioMapper {

    @Mapping(source = "rol.nombre", target = "rolNombre")
    UsuarioDTO toDTO(Usuario usuario);

    @Mapping(target = "id", ignore = true)
    @Mapping(target = "rol", ignore = true)
    @Mapping(target = "fechaCreacion", expression = "java(java.time.LocalDateTime.now())")
    @Mapping(target = "activo", defaultValue = "true")
    Usuario toEntity(UsuarioDTO dto);

    List<UsuarioDTO> toDTOList(List<Usuario> usuarios);

    @Mapping(target = "id", ignore = true)
    @Mapping(target = "rol", ignore = true)
    @Mapping(target = "fechaCreacion", ignore = true)
    @Mapping(target = "contraseña", ignore = true)
    void updateEntityFromDTO(UsuarioDTO dto, @MappingTarget Usuario usuario);
}