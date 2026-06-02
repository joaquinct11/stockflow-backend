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
public class CulqiSuscribirRequestDTO {

    /**
     * Token generado por Culqi.js a partir de los datos de la tarjeta.
     * Se obtiene en el callback window.culqi() del frontend.
     * Ejemplo: "tkn_live_xxxxx"
     */
    @NotBlank(message = "tokenId es requerido")
    private String tokenId;

    /**
     * ID del plan a suscribir. Si se omite, se usa el planIdBasico configurado
     * en las propiedades del servidor.
     */
    private String planId;
}
