package com.stockflow.dto.gre;

import com.stockflow.entity.gre.EstadoLogisticoGRE;
import com.stockflow.entity.gre.EstadoTributarioGRE;
import com.stockflow.entity.gre.ModalidadTransporteGRE;
import com.stockflow.entity.gre.TipoTrasladoGRE;
import lombok.Builder;
import lombok.Data;

import java.time.LocalDate;
import java.time.OffsetDateTime;

@Data
@Builder
public class GuiaRemisionListResponse {
    private Long id;
    private String numeroCompleto;
    private TipoTrasladoGRE tipoTraslado;
    private ModalidadTransporteGRE modalidadTransporte;
    private LocalDate fechaTraslado;
    private String sucursalOrigenNombre;
    private String sucursalDestinoNombre;
    private String destinatarioRazonSocial;
    private EstadoTributarioGRE estadoTributario;
    private EstadoLogisticoGRE estadoLogistico;
    private Long ventaId;
    private String pdfUrl;
    private OffsetDateTime createdAt;
}
