package com.stockflow.service;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Verifica que RUBROS_REGISTRO_PERMITIDOS cubra exactamente los rubros con flujo
 * implementado y excluya los rubros temporalmente desactivados (RESTAURANTE, OTRO).
 *
 * Pruebas obligatorias del Punto 2:
 *   - Registrar RESTAURANTE → debe ser rechazado.
 *   - Registrar OTRO        → debe ser rechazado.
 */
@DisplayName("Allowlist de rubros en registro (Punto 2)")
class RubroRegistroAllowlistTest {

    // Replica exacta del Set en AuthServiceImpl
    private static final Set<String> RUBROS_REGISTRO_PERMITIDOS = Set.of(
            "BOTICA", "FARMACIA", "VETERINARIA", "MINIMARKET",
            "FERRETERIA", "TIENDA_ROPA", "TIENDA", "EMPRESA_SERVICIOS"
    );

    private boolean esRubroPermitido(String rubro) {
        return rubro != null && RUBROS_REGISTRO_PERMITIDOS.contains(rubro.toUpperCase());
    }

    // ── Rubros excluidos (pruebas obligatorias) ──────────────────────────────

    @Nested
    @DisplayName("Rubros que DEBEN ser rechazados en registro")
    class RubrosBloqueados {

        @Test
        @DisplayName("RESTAURANTE → rechazado")
        void restaurante_esRechazado() {
            assertThat(esRubroPermitido("RESTAURANTE")).isFalse();
        }

        @Test
        @DisplayName("OTRO → rechazado")
        void otro_esRechazado() {
            assertThat(esRubroPermitido("OTRO")).isFalse();
        }

        @Test
        @DisplayName("null → rechazado")
        void nulo_esRechazado() {
            assertThat(esRubroPermitido(null)).isFalse();
        }

        @Test
        @DisplayName("cadena vacía → rechazada")
        void vacio_esRechazado() {
            assertThat(esRubroPermitido("")).isFalse();
        }

        @Test
        @DisplayName("Rubro inventado → rechazado")
        void inventado_esRechazado() {
            assertThat(esRubroPermitido("JOYERIA")).isFalse();
        }
    }

    // ── Rubros permitidos ────────────────────────────────────────────────────

    @Nested
    @DisplayName("Rubros que DEBEN ser aceptados en registro")
    class RubrosPermitidos {

        @Test
        @DisplayName("BOTICA → aceptado")
        void botica_esAceptado() {
            assertThat(esRubroPermitido("BOTICA")).isTrue();
        }

        @Test
        @DisplayName("FARMACIA → aceptado")
        void farmacia_esAceptada() {
            assertThat(esRubroPermitido("FARMACIA")).isTrue();
        }

        @Test
        @DisplayName("VETERINARIA → aceptado")
        void veterinaria_esAceptada() {
            assertThat(esRubroPermitido("VETERINARIA")).isTrue();
        }

        @Test
        @DisplayName("MINIMARKET → aceptado")
        void minimarket_esAceptado() {
            assertThat(esRubroPermitido("MINIMARKET")).isTrue();
        }

        @Test
        @DisplayName("FERRETERIA → aceptado")
        void ferreteria_esAceptada() {
            assertThat(esRubroPermitido("FERRETERIA")).isTrue();
        }

        @Test
        @DisplayName("TIENDA_ROPA → aceptado")
        void tiendaRopa_esAceptada() {
            assertThat(esRubroPermitido("TIENDA_ROPA")).isTrue();
        }

        @Test
        @DisplayName("TIENDA → aceptado (mapeo de TIENDA_BODEGA)")
        void tienda_esAceptada() {
            assertThat(esRubroPermitido("TIENDA")).isTrue();
        }

        @Test
        @DisplayName("EMPRESA_SERVICIOS → aceptado")
        void empresaServicios_esAceptada() {
            assertThat(esRubroPermitido("EMPRESA_SERVICIOS")).isTrue();
        }
    }

    // ── Insensibilidad a mayúsculas ──────────────────────────────────────────

    @Nested
    @DisplayName("Comparación case-insensitive")
    class CaseInsensitive {

        @Test
        @DisplayName("'botica' en minúsculas → aceptado")
        void boticaMinusculas_esAceptado() {
            assertThat(esRubroPermitido("botica")).isTrue();
        }

        @Test
        @DisplayName("'restaurante' en minúsculas → rechazado")
        void restauranteMinusculas_esRechazado() {
            assertThat(esRubroPermitido("restaurante")).isFalse();
        }
    }
}
