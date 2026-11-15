package com.stockflow.dto;

import java.time.LocalDateTime;

public interface OtResumenDTO {
    Long getId();
    String getNumeroOt();
    String getEstado();
    String getPrioridad();
    String getEquipoNombre();
    String getClienteNombre();
    String getSucursalNombre();
    String getTecnicoNombre();
    LocalDateTime getCreatedAt();
}
