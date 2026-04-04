package com.stockflow.service;

import com.stockflow.dto.reportes.ReportesResumenDTO;

import java.time.LocalDate;

public interface ReportesService {

    /**
     * Genera un resumen de reportes para el tenant actual en el rango de fechas indicado.
     *
     * @param tenantId identificador del tenant
     * @param desde    fecha de inicio del rango (inclusive)
     * @param hasta    fecha de fin del rango (inclusive)
     * @return DTO con métricas de inventario, movimientos, compras/recepciones y ventas
     */
    ReportesResumenDTO obtenerResumen(String tenantId, LocalDate desde, LocalDate hasta);
}
