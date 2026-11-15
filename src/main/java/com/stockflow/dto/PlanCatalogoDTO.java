package com.stockflow.dto;

import com.stockflow.entity.PlanCatalogo;
import lombok.Builder;
import lombok.Getter;

import java.math.BigDecimal;

@Getter
@Builder
public class PlanCatalogoDTO {

    private final String codigo;
    private final String segmento;
    private final String rubroOperativo;
    private final String nivel;
    private final BigDecimal precioMensual;
    private final int maxUsuarios;
    private final int maxSucursales;
    private final int maxProductos;
    private final String labelVisible;

    public static PlanCatalogoDTO from(PlanCatalogo p) {
        return PlanCatalogoDTO.builder()
                .codigo(p.getCodigo())
                .segmento(p.getSegmento())
                .rubroOperativo(p.getRubroOperativo())
                .nivel(p.getNivel())
                .precioMensual(p.getPrecioMensual())
                .maxUsuarios(p.getMaxUsuarios())
                .maxSucursales(p.getMaxSucursales())
                .maxProductos(p.getMaxProductos())
                .labelVisible(p.getLabelVisible())
                .build();
    }
}
