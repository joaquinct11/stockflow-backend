package com.stockflow.dto.gre;

import lombok.Data;

@Data
public class ConfirmarRecepcionRequest {
    /** Observaciones opcionales al confirmar la recepción. */
    private String observaciones;
}
