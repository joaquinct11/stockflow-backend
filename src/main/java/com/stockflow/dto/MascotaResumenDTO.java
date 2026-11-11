package com.stockflow.dto;

import java.math.BigDecimal;

/**
 * Proyección Spring Data para listados de mascotas.
 * El JOIN con clientes se resuelve en una sola query — sin N+1.
 */
public interface MascotaResumenDTO {
    Long getId();
    Long getClienteId();
    String getClienteNombre();
    String getNombre();
    String getEspecie();
    String getRaza();
    String getSexo();
    BigDecimal getPesoKg();
    Boolean getActivo();
}
