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

    /**
     * Bloquea las sucursales adicionales (no principal) al hacer downgrade PRO → BÁSICO.
     * Las sucursales bloqueadas quedan con activo=false y bloqueadaPorPlan=true.
     * No se eliminan: pueden recuperarse al volver a PRO.
     * Devuelve cuántas sucursales fueron bloqueadas.
     */
    int bloquearSucursalesAdicionales(String tenantId);

    /**
     * Desbloquea las sucursales que fueron bloqueadas por downgrade al volver a PRO.
     * Solo reactiva las que tienen bloqueadaPorPlan=true (no toca las desactivadas manualmente).
     * Devuelve cuántas sucursales fueron reactivadas.
     */
    int desbloquearSucursalesAdicionales(String tenantId);

    /**
     * Lista las sucursales del tenant incluyendo las bloqueadas por plan (para mostrar en UI).
     */
    List<SucursalDTO> listarConBloqueadas(String tenantId);
}
