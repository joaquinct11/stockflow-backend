package com.stockflow.entity.gre;

public enum ModalidadTransporteGRE {
    /** Catálogo 18, código '01' — transportista externo. */
    PUBLICO,
    /** Catálogo 18, código '02' — vehículo propio del negocio. */
    PRIVADO;

    public String codigoSunat() {
        return this == PUBLICO ? "01" : "02";
    }
}
