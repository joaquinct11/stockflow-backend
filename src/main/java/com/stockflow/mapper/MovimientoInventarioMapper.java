package com.stockflow.mapper;

import com.stockflow.dto.MovimientoInventarioDTO;
import com.stockflow.entity.MovimientoInventario;
import org.mapstruct.Mapper;
import org.mapstruct.Mapping;
import java.util.List;

@Mapper(componentModel = "spring")
public interface MovimientoInventarioMapper {

    @Mapping(source = "producto.id", target = "productoId")
    @Mapping(source = "usuario.id", target = "usuarioId")
    MovimientoInventarioDTO toDTO(MovimientoInventario movimiento);

    @Mapping(target = "id", ignore = true)
    @Mapping(target = "producto", ignore = true)
    @Mapping(target = "usuario", ignore = true)
    @Mapping(target = "createdAt", expression = "java(java.time.LocalDateTime.now())")
    MovimientoInventario toEntity(MovimientoInventarioDTO dto);

    List<MovimientoInventarioDTO> toDTOList(List<MovimientoInventario> movimientos);
}