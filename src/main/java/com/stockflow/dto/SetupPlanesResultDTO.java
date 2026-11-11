package com.stockflow.dto;

import lombok.Builder;
import lombok.Getter;

import java.math.BigDecimal;
import java.util.List;

@Getter
@Builder
public class SetupPlanesResultDTO {

    private String entorno;               // "INTEGRACION" | "PRODUCCION"
    private int totalPlanesCreados;
    private int totalPlanesOmitidos;
    private int totalCodigosAsignados;
    private int totalErrores;

    private List<GrupoCreado>   planesCreados;
    private List<GrupoOmitido>  planesOmitidos;
    private List<GrupoError>    errores;

    @Getter
    @Builder
    public static class GrupoCreado {
        private BigDecimal precioMensual;
        private String     culqiPlanId;
        private List<String> codigosAsignados;
    }

    @Getter
    @Builder
    public static class GrupoOmitido {
        private BigDecimal precioMensual;
        private String     culqiPlanIdExistente;
        private List<String> codigosYaConfigurados;
    }

    @Getter
    @Builder
    public static class GrupoError {
        private BigDecimal precioMensual;
        private String     error;
        private List<String> codigosAfectados;
    }
}
