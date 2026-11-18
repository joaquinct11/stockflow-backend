package com.stockflow.dto.gre;

import jakarta.validation.constraints.NotBlank;
import lombok.Data;

import java.time.LocalDate;

@Data
public class TransporteDTO {

    @NotBlank(message = "La dirección de partida es obligatoria")
    private String direccionPartida;

    private String ubigeoPartida;

    @NotBlank(message = "La dirección de llegada es obligatoria")
    private String direccionLlegada;

    private String ubigeoLlegada;

    // ── Transporte PRIVADO ──────────────────────────────────────────────────
    private String vehiculoPlaca;
    private String conductorTipoDoc;
    private String conductorNumDoc;
    private String conductorNombres;
    private String conductorApellidos;
    private String conductorLicencia;
    private LocalDate fechaInicioTraslado;

    // ── Transporte PÚBLICO ──────────────────────────────────────────────────
    private String transportistaRuc;
    private String transportistaDenominacion;
    private String transportistaNroRegistroMtc;
    private String transportistaNroAutorizacion;
    private String transportistaCodigoEntidadAutorizadora;
    private LocalDate fechaEntregaTransportista;
}
