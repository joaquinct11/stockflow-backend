package com.stockflow.mapper;

import com.stockflow.dto.DetalleVentaDTO;
import com.stockflow.entity.DetalleVenta;
import org.mapstruct.Mapper;
import org.mapstruct.Mapping;
import java.util.List;

@Mapper(componentModel = "spring")
public interface DetalleVentaMapper {

    @Mapping(source = "producto.id", target = "productoId")
    @Mapping(source = "producto.nombre", target = "productoNombre")
    DetalleVentaDTO toDTO(DetalleVenta detalle);

    @Mapping(target = "id", ignore = true)
    @Mapping(target = "venta", ignore = true)
    @Mapping(target = "producto", ignore = true)
    DetalleVenta toEntity(DetalleVentaDTO dto);

    List<DetalleVentaDTO> toDTOList(List<DetalleVenta> detalles);
}