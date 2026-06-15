package com.stockflow.dto;

import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class ApiSunatComprobanteRequest {

    /** "boleta" o "factura" */
    @JsonProperty("documento")
    private String documento;

    @JsonProperty("serie")
    private String serie;

    @JsonProperty("numero")
    private Integer numero;

    @JsonProperty("fecha_de_emision")
    private String fechaDeEmision;

    @JsonProperty("hora_de_emision")
    private String horaDeEmision;

    /** "PEN" para soles */
    @JsonProperty("moneda")
    private String moneda;

    /** Tipo de operación SUNAT: "0101" = venta interna */
    @JsonProperty("tipo_operacion")
    private String tipoOperacion;

    @JsonProperty("cliente_tipo_de_documento")
    private String clienteTipoDeDocumento;

    @JsonProperty("cliente_numero_de_documento")
    private String clienteNumeroDeDocumento;

    @JsonProperty("cliente_denominacion")
    private String clienteDenominacion;

    @JsonProperty("cliente_direccion")
    private String clienteDireccion;

    @JsonProperty("items")
    private List<ItemRequest> items;

    /** Total con IGV */
    @JsonProperty("total")
    private String total;

    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class ItemRequest {

        @JsonProperty("unidad_de_medida")
        private String unidadDeMedida;

        @JsonProperty("codigo_interno")
        private String codigoInterno;

        @JsonProperty("descripcion")
        private String descripcion;

        @JsonProperty("cantidad")
        private String cantidad;

        /** Valor unitario SIN IGV, 6 decimales */
        @JsonProperty("valor_unitario")
        private String valorUnitario;

        @JsonProperty("porcentaje_igv")
        private String porcentajeIgv;

        /** "10" = gravado */
        @JsonProperty("codigo_tipo_afectacion_igv")
        private String codigoTipoAfectacionIgv;

        @JsonProperty("nombre_tributo")
        private String nombreTributo;
    }
}
