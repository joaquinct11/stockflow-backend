package com.stockflow.mapper;

import com.stockflow.dto.OrdenTrabajoDTO;
import com.stockflow.entity.OtHistorialEstado;
import com.stockflow.entity.OrdenTrabajo;
import org.mapstruct.Mapper;
import org.mapstruct.Mapping;

@Mapper(componentModel = "spring")
public interface OrdenTrabajoMapper {

    @Mapping(source = "equipo.id",            target = "equipoClienteId")
    @Mapping(source = "equipo.nombre",        target = "equipoNombre")
    @Mapping(source = "equipo.tipo",          target = "equipoTipo")
    @Mapping(source = "equipo.marca",         target = "equipoMarca")
    @Mapping(source = "equipo.modelo",        target = "equipoModelo")
    @Mapping(source = "equipo.numeroSerie",   target = "equipoNumeroSerie")
    @Mapping(source = "cliente.id",           target = "clienteId")
    @Mapping(source = "cliente.nombre",       target = "clienteNombre")
    @Mapping(source = "sucursal.id",          target = "sucursalId")
    @Mapping(source = "sucursal.nombre",      target = "sucursalNombre")
    @Mapping(source = "tecnico.id",           target = "tecnicoId")
    @Mapping(source = "tecnico.nombre",       target = "tecnicoNombre")
    @Mapping(source = "recibidoPor",          target = "recibidoPor")
    @Mapping(source = "documentoReceptor",    target = "documentoReceptor")
    @Mapping(source = "fechaEntrega",         target = "fechaEntrega")
    @Mapping(source = "entregadoPor",         target = "entregadoPor")
    @Mapping(source = "observacionesEntrega", target = "observacionesEntrega")
    @Mapping(source = "conformidadCliente",   target = "conformidadCliente")
    @Mapping(source = "ventaId",              target = "ventaId")
    @Mapping(target = "historial",            ignore = true)
    OrdenTrabajoDTO toDTO(OrdenTrabajo ot);

    @Mapping(source = "estadoAnterior", target = "estadoAnterior")
    @Mapping(source = "estadoNuevo",    target = "estadoNuevo")
    OrdenTrabajoDTO.HistorialEstadoDTO toHistorialDTO(OtHistorialEstado h);
}
