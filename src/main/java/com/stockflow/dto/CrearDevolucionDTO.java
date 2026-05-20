package com.stockflow.dto;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
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
public class CrearDevolucionDTO {

    @NotNull(message = "El ventaId es requerido")
    private Long ventaId;

    @NotBlank(message = "El motivo es requerido")
    private String motivo;

    private String observaciones;

    private boolean reponerStock = true;

    @NotEmpty(message = "Debe incluir al menos un detalle de devolución")
    @Valid
    private List<DevolucionDetalleItemDTO> detalles;
}
