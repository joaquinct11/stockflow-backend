package com.stockflow.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class DatosEliminacionDTO {
    private long usuarios;
    private long productos;
    private long ventas;
    private long proveedores;
    private long suscripciones;
    private String tenantId;
    private String nombreFarmacia;
}