package com.stockflow.dto;

import lombok.AllArgsConstructor;
import lombok.Getter;

import java.util.Map;

/**
 * Precios vigentes por rubro. Fuente de verdad del servidor; el frontend nunca
 * debe hardcodear precios que puedan cambiar.
 */
@Getter
@AllArgsConstructor
public class PlanPrecioDTO {

    /** Precio mensual del plan Básico en soles. */
    private final int basicoMensual;

    /** Precio mensual del plan Pro en soles. */
    private final int proMensual;

    // ── Tabla de precios por rubro ────────────────────────────────────────────

    private static final Map<String, PlanPrecioDTO> PRECIOS = Map.of(
        "VETERINARIA",       new PlanPrecioDTO(109, 189),
        "TIENDA",            new PlanPrecioDTO(79,  149),
        "TIENDA_ROPA",       new PlanPrecioDTO(89,  169),
        "FERRETERIA",        new PlanPrecioDTO(99,  179),
        "MINIMARKET",        new PlanPrecioDTO(99,  179),
        "RESTAURANTE",       new PlanPrecioDTO(119, 199),
        "EMPRESA_SERVICIOS", new PlanPrecioDTO(99,  179),
        "FARMACIA",          new PlanPrecioDTO(89,  169),
        "BOTICA",            new PlanPrecioDTO(89,  169)
    );

    private static final PlanPrecioDTO DEFAULT = new PlanPrecioDTO(89, 169);

    public static PlanPrecioDTO forRubro(String rubro) {
        if (rubro == null) return DEFAULT;
        return PRECIOS.getOrDefault(rubro.toUpperCase(), DEFAULT);
    }
}
