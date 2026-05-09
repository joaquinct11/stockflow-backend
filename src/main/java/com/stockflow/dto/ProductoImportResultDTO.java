package com.stockflow.dto;

import lombok.Builder;
import lombok.Data;

import java.util.List;

/** Resultado de una importación masiva de productos. */
@Data
@Builder
public class ProductoImportResultDTO {

    private int total;
    private int creados;
    private int actualizados;
    private int errores;
    private List<FilaError> filaErrores;

    @Data
    @Builder
    public static class FilaError {
        private int fila;          // número de fila en el archivo (1-indexed, sin cabecera)
        private String nombre;     // nombre del producto en esa fila
        private String motivo;     // descripción del error
    }
}
