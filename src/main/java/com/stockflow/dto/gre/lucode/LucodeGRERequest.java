package com.stockflow.dto.gre.lucode;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.Builder;
import lombok.Data;

import java.util.List;

/**
 * Request para POST /api/v3/dispatches de ApiSunat (Lucode).
 *
 * Campos según documentación oficial:
 *   https://docs.apisunat.pe/guias-de-remision/guias-de-remision-remitente
 *
 * Notas:
 * - modalidad_de_transporte: "01" = público, "02" = privado
 * - motivo_de_traslado: código catálogo 20 SUNAT
 * - Transporte privado requiere conductores + vehiculos + fecha_inicio_de_traslado
 * - Transporte público requiere transportista + fecha_entrega_a_transportista
 */
@Data
@Builder
@JsonInclude(JsonInclude.Include.NON_NULL)
public class LucodeGRERequest {

    private String documento;             // "guia_remision_remitente"
    private String serie;
    private Integer numero;

    @JsonProperty("fecha_de_emision")
    private String fechaDeEmision;        // yyyy-mm-dd

    @JsonProperty("hora_de_emision")
    private String horaDeEmision;         // HH:mm

    @JsonProperty("motivo_de_traslado")
    private String motivoDeTraslado;      // código catálogo 20

    @JsonProperty("modalidad_de_transporte")
    private String modalidadDeTransporte; // "01" público, "02" privado

    @JsonProperty("destinatario_tipo_de_documento")
    private String destinatarioTipoDeDocumento;

    @JsonProperty("destinatario_numero_de_documento")
    private String destinatarioNumeroDeDocumento;

    @JsonProperty("destinatario_denominacion")
    private String destinatarioDenominacion;

    @JsonProperty("destinatario_direccion")
    private String destinatarioDireccion;

    @JsonProperty("punto_de_partida")
    private PuntoUbicacion puntoDePartida;

    @JsonProperty("punto_de_llegada")
    private PuntoUbicacion puntoDeLlegada;

    @JsonProperty("peso_bruto_total")
    private String pesoBrutoTotal;         // decimal como string, ej: "1.900"

    @JsonProperty("peso_bruto_unidad_de_medida")
    private String pesoBrutoUnidadDeMedida; // "KGM"

    @JsonProperty("numero_de_bultos")
    private Integer numeroDeBultos;

    private String observaciones;

    @JsonProperty("documentos_relacionados")
    private List<DocumentoRelacionado> documentosRelacionados;

    private List<ItemGRE> items;

    // ── Transporte privado ──────────────────────────────────────────────────
    private List<ConductorGRE> conductores;
    private List<VehiculoGRE> vehiculos;

    @JsonProperty("fecha_inicio_de_traslado")
    private String fechaInicioDeTraslado;  // yyyy-mm-dd (privado)

    // ── Transporte público ──────────────────────────────────────────────────
    private TransportistaGRE transportista;

    @JsonProperty("fecha_entrega_a_transportista")
    private String fechaEntregaATransportista; // yyyy-mm-dd (público)

    // ─────────────────────────────────────────────────────────────────────────

    @Data
    @Builder
    @JsonInclude(JsonInclude.Include.NON_NULL)
    public static class PuntoUbicacion {
        private String ubigeo;
        private String direccion;
    }

    @Data
    @Builder
    @JsonInclude(JsonInclude.Include.NON_NULL)
    public static class DocumentoRelacionado {
        private String documento;  // "boleta", "factura", etc.
        private String serie;
        private Integer numero;
        @JsonProperty("ruc_emisor")
        private String rucEmisor;
    }

    @Data
    @Builder
    @JsonInclude(JsonInclude.Include.NON_NULL)
    public static class ItemGRE {
        @JsonProperty("codigo_interno")
        private String codigoInterno;
        private String descripcion;
        @JsonProperty("unidad_de_medida")
        private String unidadDeMedida;
        private String cantidad;           // decimal como string
    }

    @Data
    @Builder
    @JsonInclude(JsonInclude.Include.NON_NULL)
    public static class ConductorGRE {
        private String conductor;          // "principal"
        @JsonProperty("tipo_de_documento")
        private String tipoDeDocumento;
        @JsonProperty("numero_de_documento")
        private String numeroDeDocumento;
        private String nombres;
        private String apellidos;
        @JsonProperty("numero_licencia_conducir")
        private String numeroLicenciaConducir;
    }

    @Data
    @Builder
    @JsonInclude(JsonInclude.Include.NON_NULL)
    public static class VehiculoGRE {
        private String vehiculo;           // "principal"
        @JsonProperty("numero_de_placa")
        private String numeroDePlaca;
    }

    @Data
    @Builder
    @JsonInclude(JsonInclude.Include.NON_NULL)
    public static class TransportistaGRE {
        private String ruc;
        private String denominacion;
        @JsonProperty("numero_registro_MTC")
        private String numeroRegistroMTC;
        @JsonProperty("numero_autorizacion")
        private String numeroAutorizacion;
        @JsonProperty("codigo_entidad_autorizadora")
        private String codigoEntidadAutorizadora;
    }
}
