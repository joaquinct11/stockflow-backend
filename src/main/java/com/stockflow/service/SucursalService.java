package com.stockflow.service;

import com.stockflow.dto.SucursalDTO;

import java.util.List;

public interface SucursalService {

    List<SucursalDTO> listar(String tenantId);

    SucursalDTO obtenerPorId(Long id, String tenantId);

    SucursalDTO crear(SucursalDTO dto, String tenantId);

    SucursalDTO actualizar(Long id, SucursalDTO dto, String tenantId);

    void desactivar(Long id, String tenantId);

    /**
     * Crea la sucursal principal para el tenant y migra todos los datos existentes a ella.
     * Llamado al activar el plan PRO desde el webhook de Culqi.
     */
    SucursalDTO inicializarPrincipal(String tenantId);

    /**
     * Resincroniza el stock de la sucursal principal con productos.stock_actual.
     * Corrige desincronizaciones causadas por recepciones registradas antes de la corrección.
     */
    void resyncStockPrincipal(String tenantId);
}
