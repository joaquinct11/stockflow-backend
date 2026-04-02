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
public class OrdenCompraResponseDTO {

    private Long id;
    private String tenantId;
    private Long proveedorId;
    private String proveedorNombre;
    private Long usuarioCreadorId;
    private String usuarioCreadorNombre;
    private String estado;
    private String observaciones;
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;
    private List<OrdenCompraItemDTO> items;
}
