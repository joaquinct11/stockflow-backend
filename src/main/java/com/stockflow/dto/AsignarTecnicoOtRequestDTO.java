package com.stockflow.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class AsignarTecnicoOtRequestDTO {

    /** ID del usuario a asignar como técnico. Null para desasignar. */
    private Long tecnicoId;

    private String observacion;
}
