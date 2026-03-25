package com.stockflow.service;

import java.util.List;

public interface UsuarioPermisoService {

    /**
     * Returns the permission codes (nombres) assigned directly to a user within a tenant.
     */
    List<String> obtenerPermisosCodigos(Long usuarioId, String tenantId);

    /**
     * Replaces all permission assignments for the given user+tenant with the provided codes.
     * Unknown permission codes are silently ignored.
     */
    void asignarPermisos(Long usuarioId, List<String> permisoCodigos, String tenantId);
}
