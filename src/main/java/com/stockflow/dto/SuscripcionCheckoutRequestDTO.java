package com.stockflow.dto;

import jakarta.validation.constraints.AssertTrue;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class SuscripcionCheckoutRequestDTO {

    @NotBlank(message = "Plan es requerido")
    @Pattern(regexp = "BASICO|PRO", message = "Plan debe ser BASICO o PRO")
    private String planId;

    /**
     * Tipo de documento del pagador (DNI, CE, RUC, PASAPORTE).
     * Opcional pero muy recomendado para Mercado Pago Perú: sin este dato
     * el botón "Confirmar" puede quedar deshabilitado si el pagador no
     * tiene su perfil de MP completamente verificado.
     * Debe enviarse junto con {@link #numeroDocumento}.
     */
    @Pattern(regexp = "DNI|CE|RUC|PASAPORTE", message = "tipoDocumento debe ser DNI, CE, RUC o PASAPORTE")
    private String tipoDocumento;

    /**
     * Número de documento del pagador.
     * Debe enviarse junto con {@link #tipoDocumento}.
     */
    @Size(min = 6, max = 20, message = "numeroDocumento debe tener entre 6 y 20 caracteres")
    private String numeroDocumento;

    /**
     * Validación de coherencia: tipoDocumento y numeroDocumento deben
     * enviarse juntos o no enviarse ninguno.
     */
    @AssertTrue(message = "tipoDocumento y numeroDocumento deben enviarse juntos o ninguno")
    public boolean isDocumentoConsistente() {
        boolean tieneTipo   = tipoDocumento   != null && !tipoDocumento.isBlank();
        boolean tieneNumero = numeroDocumento != null && !numeroDocumento.isBlank();
        return tieneTipo == tieneNumero;
    }
}
