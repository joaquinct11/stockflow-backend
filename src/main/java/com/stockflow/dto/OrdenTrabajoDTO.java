package com.stockflow.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;

@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class OrdenTrabajoDTO {

    private Long id;
    private String tenantId;
    private String numeroOt;
    private Short anio;
    private Long ventaId;

    private Long equipoClienteId;
    private String equipoNombre;
    private String equipoTipo;
    private String equipoMarca;
    private String equipoModelo;
    private String equipoNumeroSerie;

    private Long clienteId;
    private String clienteNombre;

    private Long sucursalId;
    private String sucursalNombre;

    private Long tecnicoId;
    private String tecnicoNombre;

    private String estado;
    private String prioridad;
    private String descripcionProblema;
    private String diagnostico;
    private String trabajoRealizado;
    private String observaciones;

    private LocalDateTime fechaCierreTecnico;
    private BigDecimal montoManoObra;
    private BigDecimal totalFacturado;

    // Datos de entrega (Fase 2E)
    private String recibidoPor;
    private String documentoReceptor;
    private LocalDateTime fechaEntrega;
    private Long entregadoPor;
    private String observacionesEntrega;
    private Boolean conformidadCliente;

    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;

    private List<HistorialEstadoDTO> historial;

    @Data
    @NoArgsConstructor
    @AllArgsConstructor
    @Builder
    public static class HistorialEstadoDTO {
        private Long id;
        private String estadoAnterior;
        private String estadoNuevo;
        private Long usuarioId;
        private String observacion;
        private LocalDateTime createdAt;
    }
}
