package com.stockflow.dto;

import lombok.*;
import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;

@Data @NoArgsConstructor @AllArgsConstructor @Builder
public class CajaDTO {
    private Long id;
    private String tenantId;
    private Long usuarioId;
    private String usuarioNombre;
    private BigDecimal montoApertura;
    private BigDecimal totalEfectivo;
    private BigDecimal totalTarjeta;
    private BigDecimal totalYapePlin;
    private BigDecimal totalIngresos;
    private Integer cantidadVentas;
    private BigDecimal totalRetiros;
    private List<RetiroCajaDTO> retiros;
    private BigDecimal montoContado;
    private BigDecimal diferencia;
    private String estado;
    private String observaciones;
    private LocalDateTime fechaApertura;
    private LocalDateTime fechaCierre;
    private String cerradoPorNombre;
    private Long sucursalId;
}
