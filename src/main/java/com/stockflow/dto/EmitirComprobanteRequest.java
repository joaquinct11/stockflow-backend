package com.stockflow.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class EmitirComprobanteRequest {

    @NotNull(message = "El ventaId es requerido")
    private Long ventaId;

    @NotBlank(message = "El tipo de comprobante es requerido (BOLETA o FACTURA)")
    private String tipo;  // BOLETA | FACTURA

    /** Serie explícita; si null se usa la default (B001/F001). */
    private String serie;

    private String receptorDocTipo;    // DNI | RUC
    private String receptorDocNumero;
    private String receptorNombre;
    private String receptorDireccion;
}
