package com.stockflow.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class SuscripcionEstadoResponseDTO {
    private String estado;
    private String planId;
    private String preapprovalId;
    private String mpPaymentId;
    private LocalDateTime fechaProximoCobro;
}
