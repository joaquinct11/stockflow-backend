package com.stockflow.dto.gre;

import com.stockflow.entity.gre.*;
import lombok.Builder;
import lombok.Data;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.util.List;

@Data
@Builder
public class GuiaRemisionResponse {
    private Long id;
    private String tenantId;
    private String serie;
    private Integer correlativo;
    private String numeroCompleto;

    private TipoTrasladoGRE tipoTraslado;
    private String motivoTrasladoCodigo;
    private String motivoTrasladoDescripcion;
    private ModalidadTransporteGRE modalidadTransporte;
    private LocalDate fechaTraslado;
    private BigDecimal pesoBrutoTotal;
    private String unidadMedidaPeso;
    private Integer numeroBultos;
    private String observaciones;

    // Origen
    private Long sucursalOrigenId;
    private String sucursalOrigenNombre;

    // Destino
    private Long sucursalDestinoId;
    private String sucursalDestinoNombre;

    // Destinatario
    private String destinatarioTipoDoc;
    private String destinatarioNumDoc;
    private String destinatarioRazonSocial;
    private String destinatarioDireccion;

    private Long ventaId;

    // Estados
    private EstadoTributarioGRE estadoTributario;
    private EstadoLogisticoGRE estadoLogistico;

    // Respuesta Lucode
    private String lucodeId;
    private String lucodeHash;
    private String xmlUrl;
    private String cdrUrl;
    private String pdfUrl;

    // Auditoría
    private String createdBy;
    private OffsetDateTime createdAt;
    private OffsetDateTime updatedAt;

    private List<GuiaRemisionItemDTO> items;
    private TransporteDTO transporte;
}
