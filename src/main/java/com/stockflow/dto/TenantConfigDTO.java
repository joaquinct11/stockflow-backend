package com.stockflow.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class TenantConfigDTO {

    private String tenantId;
    private String nombreNegocio;
    private String ruc;
    private String direccion;
    private String telefono;
    private String emailContacto;
    private String ciudad;

    /** Base64 del logo (data:image/png;base64,...) — nullable */
    private String logoBase64;

    /** Símbolo de moneda, ej: "S/.", "$" */
    private String moneda;

    /** Porcentaje de IGV, ej: 18.0 */
    private Double igvPorcentaje;

    /** Texto libre al pie de los PDFs generados */
    private String piePaginaPdf;

    private String serieBoleta;
    private String serieFactura;

    /** URL base del OSE (Nubefact / Efact / etc) configurada por el tenant. */
    private String oseUrl;

    /**
     * Token API del OSE.
     * Se devuelve enmascarado en GET para no exponer el valor completo;
     * se acepta el token completo en PUT para actualizarlo.
     */
    private String oseToken;

    /** Rubro del negocio. Posibles valores: BOTICA, FARMACIA, MINIMARKET, FERRETERIA, RESTAURANTE, TIENDA, OTRO */
    private String rubro;
}
