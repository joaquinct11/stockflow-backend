package com.stockflow.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class DeleteAccountValidationDTO {

    private boolean requiereConfirmacion;
    private String mensaje;
    private String tipo; // "USUARIO_NORMAL" o "TENANT_OWNER"
    private DatosEliminacionDTO datosAEliminar;
}