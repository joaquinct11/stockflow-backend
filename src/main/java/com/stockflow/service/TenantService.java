package com.stockflow.service;

import com.stockflow.dto.DatosEliminacionDTO;
import com.stockflow.entity.Tenant;
import java.util.Optional;

public interface TenantService {
    Tenant crearTenant(String nombreFarmacia);
    Optional<Tenant> obtenerPorTenantId(String tenantId);
    boolean existeTenant(String tenantId);
    void desactivarTenant(String tenantId);
    void reactivarTenant(String tenantId);
    void eliminarPermanentemente(String tenantId);
    DatosEliminacionDTO obtenerDatosEliminacion(String tenantId);  // ✅ NUEVO
}