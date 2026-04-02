package com.stockflow.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;
import java.util.List;

@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class RecepcionResponseDTO {

    private Long id;
    private String tenantId;
    private Long ocId;
    private Long proveedorId;
    private String proveedorNombre;
    private Long usuarioReceptorId;
    private String usuarioReceptorNombre;
    private String estado;
    private String tipoComprobante;
    private String serie;
    private String numero;
    private String urlAdjunto;
    private String observaciones;
    private LocalDateTime createdAt;
    private LocalDateTime fechaConfirmacion;
    private List<RecepcionDetalleResponseDTO> detalles;
}
