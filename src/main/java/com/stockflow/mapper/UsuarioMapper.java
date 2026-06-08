package com.stockflow.mapper;

import com.stockflow.dto.UsuarioDTO;
import com.stockflow.entity.Usuario;
import org.mapstruct.Mapper;
import org.mapstruct.Mapping;
import org.mapstruct.MappingTarget;
import java.util.List;

@Mapper(componentModel = "spring")
public interface UsuarioMapper {

    @Mapping(source = "rol.nombre",      target = "rolNombre")
    @Mapping(source = "apellido",        target = "apellido")
    @Mapping(source = "tipoDocumento",   target = "tipoDocumento")
    @Mapping(source = "numeroDocumento", target = "numeroDocumento")
    @Mapping(source = "numeroCelular",   target = "numeroCelular")
    UsuarioDTO toDTO(Usuario usuario);

    @Mapping(target = "id", ignore = true)
    @Mapping(target = "rol", ignore = true)
    @Mapping(target = "activo", defaultValue = "true")
    @Mapping(source = "tipoDocumento",   target = "tipoDocumento")
    @Mapping(source = "numeroDocumento", target = "numeroDocumento")
    @Mapping(source = "numeroCelular",   target = "numeroCelular")
    Usuario toEntity(UsuarioDTO dto);

    List<UsuarioDTO> toDTOList(List<Usuario> usuarios);

    @Mapping(target = "id", ignore = true)
    @Mapping(target = "rol", ignore = true)
    @Mapping(target = "contraseña", ignore = true)
    void updateEntityFromDTO(UsuarioDTO dto, @MappingTarget Usuario usuario);
}