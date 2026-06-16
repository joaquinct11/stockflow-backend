package com.stockflow.dto;

import com.fasterxml.jackson.annotation.JsonFormat;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;
import java.time.LocalDate;

@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class RecepcionDetalleRequestDTO {

    @NotNull(message = "El producto es requerido")
    private Long productoId;

    @NotNull(message = "La cantidad recibida es requerida")
    @Min(value = 0, message = "La cantidad no puede ser negativa")
    private Integer cantidadRecibida;

    @JsonFormat(pattern = "yyyy-MM-dd")
    private LocalDate fechaVencimiento;

    private String lote;

    private String registroSanitario;

    private BigDecimal precioVenta;
}
