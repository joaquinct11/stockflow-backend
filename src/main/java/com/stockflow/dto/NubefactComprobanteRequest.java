package com.stockflow.dto;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.Builder;
import lombok.Data;

import java.math.BigDecimal;
import java.util.List;

/**
 * Cuerpo de la petición hacia la API de Nubefact (OSE).
 * Referencia: https://www.nubefact.com/api-doc/
 *
 * Snake_case requerido por Nubefact; usamos @JsonProperty para mantener
 * las convenciones Java en el código Java.
 */
@Data
@Builder
@JsonInclude(JsonInclude.Include.NON_NULL)
public class NubefactComprobanteRequest {

    // ── Cabecera del comprobante ──────────────────────────────────────────────

    @JsonProperty("operacion")
    private String operacion;               // "generar_comprobante"

    @JsonProperty("tipo_de_comprobante")
    private Integer tipoDeComprobante;      // 1=Factura, 2=Boleta

    @JsonProperty("serie")
    private String serie;                   // "B001" | "F001"

    @JsonProperty("numero")
    private Integer numero;                 // correlativo numérico

    @JsonProperty("sunat_transaction")
    private Integer sunatTransaction;       // 1 = operación normal

    // ── Receptor ─────────────────────────────────────────────────────────────

    @JsonProperty("cliente_tipo_de_documento")
    private Integer clienteTipoDeDocumento; // 0=Sin doc, 1=DNI, 6=RUC

    @JsonProperty("cliente_numero_de_documento")
    private String clienteNumeroDeDocumento;

    @JsonProperty("cliente_denominacion")
    private String clienteDenominacion;

    @JsonProperty("cliente_direccion")
    private String clienteDireccion;

    @JsonProperty("cliente_email")
    private String clienteEmail;

    // ── Fecha y moneda ────────────────────────────────────────────────────────

    @JsonProperty("fecha_de_emision")
    private String fechaDeEmision;          // "dd/MM/yyyy"

    @JsonProperty("moneda")
    private Integer moneda;                 // 1 = PEN (soles)

    @JsonProperty("tipo_de_cambio")
    private String tipoDeCambio;

    // ── IGV ──────────────────────────────────────────────────────────────────

    @JsonProperty("porcentaje_de_igv")
    private Double porcentajeDeIgv;         // 18.0

    // ── Totales ───────────────────────────────────────────────────────────────

    @JsonProperty("total_gravada")
    private BigDecimal totalGravada;        // base imponible (sin IGV)

    @JsonProperty("total_inafecta")
    private String totalInafecta;

    @JsonProperty("total_exonerada")
    private String totalExonerada;

    @JsonProperty("total_igv")
    private BigDecimal totalIgv;

    @JsonProperty("total_gratuita")
    private String totalGratuita;

    @JsonProperty("total")
    private BigDecimal total;               // total con IGV

    // ── Opciones de envío ────────────────────────────────────────────────────

    @JsonProperty("enviar_automaticamente_a_la_sunat")
    private Boolean enviarAutomaticamenteALaSunat;

    @JsonProperty("enviar_automaticamente_al_cliente")
    private Boolean enviarAutomaticamenteAlCliente;

    // ── Ítems ─────────────────────────────────────────────────────────────────

    @JsonProperty("items")
    private List<ItemRequest> items;

    // ── Campos opcionales frecuentes ─────────────────────────────────────────

    @JsonProperty("descuento_global")
    private String descuentoGlobal;

    @JsonProperty("observaciones")
    private String observaciones;

    // ── Inner: ítem de detalle ────────────────────────────────────────────────

    @Data
    @Builder
    @JsonInclude(JsonInclude.Include.NON_NULL)
    public static class ItemRequest {

        @JsonProperty("unidad_de_medida")
        private String unidadDeMedida;      // "NIU" = unidad

        @JsonProperty("codigo")
        private String codigo;              // código de barras / SKU

        @JsonProperty("descripcion")
        private String descripcion;

        @JsonProperty("cantidad")
        private BigDecimal cantidad;

        /** Precio unitario SIN IGV. */
        @JsonProperty("valor_unitario")
        private BigDecimal valorUnitario;

        /** Precio unitario CON IGV (lo que cobra al cliente). */
        @JsonProperty("precio_unitario")
        private BigDecimal precioUnitario;

        @JsonProperty("descuento")
        private String descuento;

        /** cantidad × valorUnitario (sin IGV). */
        @JsonProperty("subtotal")
        private BigDecimal subtotal;

        /** 1=Gravado, 2=Exonerado, 3=Inafecto */
        @JsonProperty("tipo_de_igv")
        private Integer tipoDeIgv;

        /** subtotal × (igvRate). */
        @JsonProperty("igv")
        private BigDecimal igv;

        /** cantidad × precioUnitario (con IGV). */
        @JsonProperty("total")
        private BigDecimal total;

        @JsonProperty("anticipo_regularizacion")
        private Boolean anticipoRegularizacion;

        @JsonProperty("anticipo_documento_serie")
        private String anticipoDocumentoSerie;

        @JsonProperty("anticipo_documento_numero")
        private String anticipoDocumentoNumero;
    }
}
