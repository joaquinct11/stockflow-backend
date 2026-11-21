package com.stockflow.dto.gre;

import jakarta.validation.constraints.NotBlank;
import lombok.Data;
import lombok.NoArgsConstructor;
import lombok.AllArgsConstructor;

/**
 * Datos obtenidos de Lucode (vía consulta manual al OSE) necesarios para
 * reconciliar una GRE que quedó en estado ERROR local pero fue aceptada por SUNAT.
 *
 * El admin debe obtener estos valores consultando el OSE antes de invocar la reconciliación.
 * La operación no llama a Lucode — solo aplica los datos localmente de forma segura.
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
public class ReconciliarGRERequest {

    @NotBlank(message = "El hash de Lucode es obligatorio para confirmar la aceptación")
    private String lucodeHash;

    @NotBlank(message = "La URL del XML es obligatoria")
    private String xmlUrl;

    @NotBlank(message = "La URL del CDR es obligatoria")
    private String cdrUrl;

    private String pdfA4Url;
}
