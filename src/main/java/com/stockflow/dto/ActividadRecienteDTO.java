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
public class ActividadRecienteDTO {

    /** VENTA | COMPROBANTE | ENTRADA | AJUSTE | ORDEN_COMPRA | RECEPCION */
    private String tipo;

    /** Texto principal, ej: "Venta B001-00428 por S/ 86,50" */
    private String descripcion;

    /** Subtexto secundario, ej: "boleta aceptada" — puede ser null */
    private String detalle;

    /** Nombre abreviado del usuario que realizó la acción */
    private String usuarioNombre;

    private LocalDateTime fechaHora;
}
