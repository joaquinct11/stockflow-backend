package com.stockflow.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDate;

@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class RecepcionDetalleResponseDTO {

    private Long id;
    private Long productoId;
    private String productoNombre;
    private Integer cantidadRecibida;
    private LocalDate fechaVencimiento;
}
