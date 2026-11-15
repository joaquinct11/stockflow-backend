package com.stockflow.dto;

public interface EquipoResumenDTO {

    Long getId();
    Long getClienteId();
    String getClienteNombre();
    String getNombre();
    String getTipo();
    String getMarca();
    String getModelo();
    String getNumeroSerie();
    String getUbicacion();
    Boolean getActivo();
}
