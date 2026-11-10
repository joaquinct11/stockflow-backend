package com.stockflow.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import lombok.Data;

@Data
public class EntregarOtRequestDTO {

    @NotBlank(message = "El nombre de quien recibe es obligatorio")
    @Size(max = 200, message = "El nombre no puede superar los 200 caracteres")
    private String recibidoPor;

    @Size(max = 50, message = "El documento no puede superar los 50 caracteres")
    private String documentoReceptor;

    @NotNull(message = "Debe indicar si el cliente manifiesta conformidad")
    private Boolean conformidadCliente;

    @Size(max = 2000, message = "Las observaciones no pueden superar los 2000 caracteres")
    private String observaciones;
}
