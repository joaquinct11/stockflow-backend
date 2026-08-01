package com.stockflow.service;

import com.stockflow.dto.superadmin.*;

import java.util.List;

public interface SuperAdminService {
    List<TenantResumenDTO>        listarTenants();
    SuperAdminStatsDTO            obtenerStats();
    SuperAdminFinanzasDTO         obtenerFinanzas();
    List<SuscripcionHistorialDTO> obtenerCobros(String tenantId);
    TenantResumenDTO              actualizarSuscripcion(String tenantId, SuscripcionManualUpdateDTO dto);
    TenantResumenDTO              extenderTrial(String tenantId, int dias);
    void                          toggleActivo(String tenantId, boolean activo);
    void                          actualizarOse(String tenantId, OseUpdateDTO dto);
}
