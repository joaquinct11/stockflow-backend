package com.stockflow.dto;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;

@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class OrdenCompraRequestDTO {

    @NotNull(message = "El proveedor es requerido")
    private Long proveedorId;

    private String observaciones;

    @NotEmpty(message = "La OC debe tener al menos un ítem")
    @Valid
    private List<OrdenCompraItemDTO> items;
}
