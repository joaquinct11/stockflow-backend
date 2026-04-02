package com.stockflow.dto;

import jakarta.validation.constraints.NotBlank;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class ComprobanteProveedorDTO {

    @NotBlank(message = "El tipo de comprobante es requerido")
    private String tipoComprobante; // FACTURA | BOLETA

    @NotBlank(message = "La serie es requerida")
    private String serie;

    @NotBlank(message = "El número es requerido")
    private String numero;

    private String urlAdjunto;
}
