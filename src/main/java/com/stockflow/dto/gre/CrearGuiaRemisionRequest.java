package com.stockflow.dto.gre;

import com.stockflow.entity.gre.ModalidadTransporteGRE;
import com.stockflow.entity.gre.TipoTrasladoGRE;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import lombok.Data;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;

@Data
public class CrearGuiaRemisionRequest {

    @NotNull(message = "El tipo de traslado es obligatorio")
    private TipoTrasladoGRE tipoTraslado;

    /** Código catálogo 20 SUNAT. '04' = traslado entre establecimientos, '01' = venta, etc. */
    private String motivoTrasladoCodigo = "04";

    private String motivoTrasladoDescripcion;

    @NotNull(message = "La modalidad de transporte es obligatoria")
    private ModalidadTransporteGRE modalidadTransporte;

    @NotNull(message = "La fecha de traslado es obligatoria")
    private LocalDate fechaTraslado;

    private BigDecimal pesoBrutoTotal;
    private String unidadMedidaPeso = "KGM";
    private Integer numeroBultos;
    private String observaciones;

    // ── Sucursales (para SUCURSALES) ────────────────────────────────────────
    private Long sucursalOrigenId;
    private Long sucursalDestinoId;

    // ── Destinatario (para DELIVERY) ────────────────────────────────────────
    private String destinatarioTipoDoc;
    private String destinatarioNumDoc;
    private String destinatarioRazonSocial;
    private String destinatarioDireccion;

    /** Venta asociada (solo para DELIVERY). */
    private Long ventaId;

    @NotEmpty(message = "Debe incluir al menos un item")
    @Valid
    private List<GuiaRemisionItemDTO> items;

    @NotNull(message = "Los datos de transporte son obligatorios")
    @Valid
    private TransporteDTO transporte;

    /**
     * Clave de idempotencia proporcionada por el cliente.
     * Si no se envía, se genera automáticamente en el servicio.
     */
    private String idempotencyKey;
}
