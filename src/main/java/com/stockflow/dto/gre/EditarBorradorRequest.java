package com.stockflow.dto.gre;

import com.stockflow.entity.gre.ModalidadTransporteGRE;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import lombok.Data;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;

@Data
public class EditarBorradorRequest {

    private String motivoTrasladoCodigo;
    private String motivoTrasladoDescripcion;

    @NotNull(message = "La modalidad de transporte es obligatoria")
    private ModalidadTransporteGRE modalidadTransporte;

    @NotNull(message = "La fecha de traslado es obligatoria")
    private LocalDate fechaTraslado;

    private BigDecimal pesoBrutoTotal;
    private String unidadMedidaPeso;
    private Integer numeroBultos;
    private String observaciones;

    // Para DELIVERY independiente (sin ventaId): permite asignar/cambiar la sucursal de origen
    private Long sucursalOrigenId;

    // Destinatario (solo para DELIVERY)
    private String destinatarioTipoDoc;
    private String destinatarioNumDoc;
    private String destinatarioRazonSocial;
    private String destinatarioDireccion;

    @NotEmpty(message = "Debe incluir al menos un item")
    @Valid
    private List<GuiaRemisionItemDTO> items;

    @NotNull(message = "Los datos de transporte son obligatorios")
    @Valid
    private TransporteDTO transporte;
}
