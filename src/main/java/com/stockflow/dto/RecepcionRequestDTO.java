package com.stockflow.dto;

import jakarta.validation.constraints.NotNull;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class RecepcionRequestDTO {

    /** Si se indica, la recepción está basada en esta OC. */
    private Long ocId;

    /** Requerido si no se indica ocId; si se indica ocId se puede omitir (se toma del OC). */
    private Long proveedorId;

    private String observaciones;
}
