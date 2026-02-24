package com.stockflow.mapper;

import com.stockflow.dto.VentaDTO;
import com.stockflow.entity.Venta;
import org.mapstruct.Mapper;
import org.mapstruct.Mapping;
import java.util.List;

@Mapper(componentModel = "spring", uses = {DetalleVentaMapper.class})
public interface VentaMapper {

    @Mapping(source = "vendedor.id", target = "vendedorId")
    @Mapping(source = "vendedor.nombre", target = "vendedorNombre")
    VentaDTO toDTO(Venta venta);

    @Mapping(target = "id", ignore = true)
    @Mapping(target = "vendedor", ignore = true)
    @Mapping(target = "createdAt", expression = "java(java.time.LocalDateTime.now())")
    @Mapping(target = "estado", defaultValue = "COMPLETADA")
    Venta toEntity(VentaDTO dto);

    List<VentaDTO> toDTOList(List<Venta> ventas);
}