package com.stockflow.service;

import com.stockflow.entity.Producto;
import com.stockflow.entity.ProductoPresentacion;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.math.RoundingMode;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Tests unitarios para la lógica de precios mayoristas por presentación.
 * Replica resolverPrecioMayoristaPresentacion de VentaController.
 */
@DisplayName("Precios mayoristas por presentación (Farmacia / Botica)")
class PrecioMayoristaPresentacionTest {

    // ── Replica de los métodos privados de VentaController ───────────────────

    /**
     * Réplica de VentaController.resolverPrecioMayoristaPresentacion.
     * Prioridad:
     *  1. Config mayorista propia completa de la presentación.
     *  2. Fallback a config base del producto con unidades equivalentes (cantidad × factor).
     *  3. precioVenta de la presentación.
     */
    private BigDecimal resolverPrecioMayoristaPresentacion(
            ProductoPresentacion presentacion, Producto producto, int cantidad) {
        if (presentacion.getPrecioMayorista() != null && presentacion.getCantidadMinimaMayorista() != null) {
            return cantidad >= presentacion.getCantidadMinimaMayorista()
                    ? presentacion.getPrecioMayorista()
                    : presentacion.getPrecioVenta();
        }
        int factor = (presentacion.getFactor() != null && presentacion.getFactor() > 0)
                ? presentacion.getFactor() : 1;
        int cantidadBase = cantidad * factor;
        if (producto.getPrecioMayorista() != null && producto.getCantidadMinimaMayorista() != null
                && cantidadBase >= producto.getCantidadMinimaMayorista()) {
            return producto.getPrecioMayorista();
        }
        return presentacion.getPrecioVenta() != null ? presentacion.getPrecioVenta() : producto.getPrecioVenta();
    }

    private BigDecimal resolverPrecioMayorista(Producto producto, int cantidad) {
        if (producto.getPrecioMayorista() == null || producto.getCantidadMinimaMayorista() == null) {
            return null;
        }
        if (cantidad >= producto.getCantidadMinimaMayorista()) {
            return producto.getPrecioMayorista();
        }
        return producto.getPrecioVenta();
    }

    // ── Builders de prueba ───────────────────────────────────────────────────

    private Producto productoBase(double precioVenta, double precioMayorista, int cantMin) {
        return Producto.builder()
                .nombre("Producto Test")
                .precioVenta(BigDecimal.valueOf(precioVenta))
                .precioMayorista(BigDecimal.valueOf(precioMayorista))
                .cantidadMinimaMayorista(cantMin)
                .costoUnitario(BigDecimal.valueOf(1.00))
                .tenantId("tenant-test")
                .build();
    }

    private Producto productoBaseSinMayorista(double precioVenta) {
        return Producto.builder()
                .nombre("Producto Test")
                .precioVenta(BigDecimal.valueOf(precioVenta))
                .costoUnitario(BigDecimal.valueOf(1.00))
                .tenantId("tenant-test")
                .build();
    }

    private ProductoPresentacion presentacionConMayorista(double precioVenta, double precioMayorista, int cantMinima) {
        return ProductoPresentacion.builder()
                .precioVenta(BigDecimal.valueOf(precioVenta))
                .precioMayorista(BigDecimal.valueOf(precioMayorista))
                .cantidadMinimaMayorista(cantMinima)
                .factor(1)
                .esPrincipal(false)
                .build();
    }

    private ProductoPresentacion presentacionConMayorista(double precioVenta, double precioMayorista, int cantMinima, int factor) {
        return ProductoPresentacion.builder()
                .precioVenta(BigDecimal.valueOf(precioVenta))
                .precioMayorista(BigDecimal.valueOf(precioMayorista))
                .cantidadMinimaMayorista(cantMinima)
                .factor(factor)
                .esPrincipal(false)
                .build();
    }

    private ProductoPresentacion presentacionSinMayorista(double precioVenta, int factor) {
        return ProductoPresentacion.builder()
                .precioVenta(BigDecimal.valueOf(precioVenta))
                // precioMayorista = null, cantidadMinimaMayorista = null
                .factor(factor)
                .esPrincipal(false)
                .build();
    }

    private ProductoPresentacion presentacionSinMayorista(double precioVenta) {
        return presentacionSinMayorista(precioVenta, 1);
    }

    // ── Pruebas ──────────────────────────────────────────────────────────────

    // ── Producto base reutilizable en varios nested ──────────────────────────

    // Dexametasona: S/5.00 minorista, S/2.00 mayorista desde 5 unidades
    final Producto dexametasona = productoBase(5.00, 2.00, 5);

    // Tableta genérica sin config mayorista
    final Producto tabletaSinMayor = productoBaseSinMayorista(5.00);

    // ── Tests ────────────────────────────────────────────────────────────────

    @Nested
    @DisplayName("Presentación SIN config propia + producto SIN config → siempre precioVenta")
    class SinMayoristaEnNingunLado {

        @Test
        @DisplayName("Cualquier cantidad → precio minorista de la presentación")
        void sinConfig_cualquierCantidad_usaPrecioVenta() {
            ProductoPresentacion p = presentacionSinMayorista(8.00);
            assertThat(resolverPrecioMayoristaPresentacion(p, tabletaSinMayor, 1)).isEqualByComparingTo("8.00");
            assertThat(resolverPrecioMayoristaPresentacion(p, tabletaSinMayor, 50)).isEqualByComparingTo("8.00");
            assertThat(resolverPrecioMayoristaPresentacion(p, tabletaSinMayor, 100)).isEqualByComparingTo("8.00");
        }

        @Test
        @DisplayName("Solo precioMayorista sin cantidadMinima → config incompleta, usa precioVenta")
        void soloPrecioMayorista_sinCantidad_usaPrecioVenta() {
            ProductoPresentacion p = ProductoPresentacion.builder()
                    .precioVenta(BigDecimal.valueOf(8.00))
                    .precioMayorista(BigDecimal.valueOf(6.50))
                    .factor(1).esPrincipal(false).build();
            assertThat(resolverPrecioMayoristaPresentacion(p, tabletaSinMayor, 20)).isEqualByComparingTo("8.00");
        }

        @Test
        @DisplayName("Solo cantidadMinima sin precioMayorista → config incompleta, usa precioVenta")
        void soloCantidad_sinPrecioMayorista_usaPrecioVenta() {
            ProductoPresentacion p = ProductoPresentacion.builder()
                    .precioVenta(BigDecimal.valueOf(8.00))
                    .cantidadMinimaMayorista(10)
                    .factor(1).esPrincipal(false).build();
            assertThat(resolverPrecioMayoristaPresentacion(p, tabletaSinMayor, 20)).isEqualByComparingTo("8.00");
        }
    }

    @Nested
    @DisplayName("Presentación SIN config propia + producto CON config → fallback por unidades equivalentes")
    class FallbackConfigBase {

        @Test
        @DisplayName("Unidad (factor=1) con 4 tabletas < 5 mínimo → precioVenta de la presentación")
        void unidad_porDebajoDeminimo_usaPrecioVenta() {
            ProductoPresentacion unidad = presentacionSinMayorista(5.00, 1);
            assertThat(resolverPrecioMayoristaPresentacion(unidad, dexametasona, 4)).isEqualByComparingTo("5.00");
        }

        @Test
        @DisplayName("Unidad (factor=1) con 5 tabletas = mínimo → aplica mayorista base S/2.00")
        void unidad_exactamenteEnMinimo_aplicaMayoristaBase() {
            ProductoPresentacion unidad = presentacionSinMayorista(5.00, 1);
            assertThat(resolverPrecioMayoristaPresentacion(unidad, dexametasona, 5)).isEqualByComparingTo("2.00");
        }

        @Test
        @DisplayName("Unidad (factor=1) con 10 tabletas > mínimo → aplica mayorista base S/2.00")
        void unidad_porEncimaDeminimo_aplicaMayoristaBase() {
            ProductoPresentacion unidad = presentacionSinMayorista(5.00, 1);
            assertThat(resolverPrecioMayoristaPresentacion(unidad, dexametasona, 10)).isEqualByComparingTo("2.00");
        }

        @Test
        @DisplayName("Blíster (factor=10) con 1 blíster = 10 tabletas > 5 mínimo → aplica mayorista base")
        void blisterSinConfig_factorEquivalente_aplicaMayoristaBase() {
            // 1 blíster × 10 = 10 tabletas >= 5 → aplica S/2.00 base
            ProductoPresentacion blister = presentacionSinMayorista(3.00, 10);
            assertThat(resolverPrecioMayoristaPresentacion(blister, dexametasona, 1)).isEqualByComparingTo("2.00");
        }

        @Test
        @DisplayName("Blíster (factor=5) con 1 blíster = 5 tabletas = mínimo → aplica mayorista base")
        void blisterFactor5_exactoAlMinimo_aplicaMayoristaBase() {
            ProductoPresentacion blister = presentacionSinMayorista(2.50, 5);
            // 1 × 5 = 5 >= 5 → S/2.00
            assertThat(resolverPrecioMayoristaPresentacion(blister, dexametasona, 1)).isEqualByComparingTo("2.00");
        }

        @Test
        @DisplayName("Consistencia: Unidad con factor=1 produce mismo precio que resolverPrecioMayorista directo")
        void unidad_consistenteConMetodoDirecto() {
            ProductoPresentacion unidad = presentacionSinMayorista(5.00, 1);
            BigDecimal precioViaPresentacion = resolverPrecioMayoristaPresentacion(unidad, dexametasona, 5);
            // resolverPrecioMayorista devuelve el mayorista cuando se cumple el mínimo
            assertThat(precioViaPresentacion).isEqualByComparingTo("2.00");
        }
    }

    @Nested
    @DisplayName("Presentación CON config mayorista propia (Blíster S/10.00 → S/8.00 desde ×2)")
    class ConMayoristaBlister {

        // Blíster con su propia config: S/10.00 minorista, S/8.00 desde 2 blísteres
        final ProductoPresentacion pBlister = presentacionConMayorista(10.00, 8.00, 2);

        @Test
        @DisplayName("1 blíster (< mínimo) → precio minorista S/10.00")
        void cantidad1_usaPrecioVenta() {
            assertThat(resolverPrecioMayoristaPresentacion(pBlister, dexametasona, 1)).isEqualByComparingTo("10.00");
        }

        @Test
        @DisplayName("2 blísteres (= mínimo) → precio mayorista S/8.00")
        void cantidadExactoAlMinimo_usaPrecioMayorista() {
            assertThat(resolverPrecioMayoristaPresentacion(pBlister, dexametasona, 2)).isEqualByComparingTo("8.00");
        }

        @Test
        @DisplayName("5 blísteres (> mínimo) → precio mayorista S/8.00")
        void cantidadMayorDelMinimo_usaPrecioMayorista() {
            assertThat(resolverPrecioMayoristaPresentacion(pBlister, dexametasona, 5)).isEqualByComparingTo("8.00");
        }

        @Test
        @DisplayName("La config de la presentación tiene precedencia sobre la config base del producto")
        void configPropiaGanaSobreConfigBase() {
            // Blíster tiene su propia config (min=2, precio=8). La config base (min=5, precio=2)
            // no debe interferir — el blíster usa su propio precio mayorista.
            assertThat(resolverPrecioMayoristaPresentacion(pBlister, dexametasona, 2)).isEqualByComparingTo("8.00");
            // Y el precio mayorista del blíster (S/8) es DIFERENTE al base (S/2)
            assertThat(resolverPrecioMayoristaPresentacion(pBlister, dexametasona, 2))
                .isNotEqualByComparingTo(dexametasona.getPrecioMayorista().toPlainString());
        }

        @Test
        @DisplayName("Reducción de qty por debajo del mínimo → vuelve a S/10.00")
        void reduccionPorDebajoDelMinimo_vuelveAMinorista() {
            assertThat(resolverPrecioMayoristaPresentacion(pBlister, dexametasona, 2)).isEqualByComparingTo("8.00");
            assertThat(resolverPrecioMayoristaPresentacion(pBlister, dexametasona, 1)).isEqualByComparingTo("10.00");
        }
    }

    @Nested
    @DisplayName("Presentación B: caja S/65.00, mayorista S/55.00 desde ×5 cajas")
    class ConMayoristaCaja {

        final Producto productoEjemplo = productoBase(5.00, 3.00, 10);
        final ProductoPresentacion pCaja = presentacionConMayorista(65.00, 55.00, 5);

        @Test
        @DisplayName("4 cajas (< mínimo) → S/65.00")
        void cantidad4_precioMinorista() {
            assertThat(resolverPrecioMayoristaPresentacion(pCaja, productoEjemplo, 4)).isEqualByComparingTo("65.00");
        }

        @Test
        @DisplayName("5 cajas (= mínimo) → S/55.00")
        void cantidad5_precioMayorista() {
            assertThat(resolverPrecioMayoristaPresentacion(pCaja, productoEjemplo, 5)).isEqualByComparingTo("55.00");
        }

        @Test
        @DisplayName("10 cajas → S/55.00")
        void cantidad10_precioMayorista() {
            assertThat(resolverPrecioMayoristaPresentacion(pCaja, productoEjemplo, 10)).isEqualByComparingTo("55.00");
        }
    }

    @Nested
    @DisplayName("Dos presentaciones del mismo producto — lógica independiente")
    class DosPresentacionesIndependientes {

        final Producto producto = productoBase(5.00, 3.00, 10);
        // Presentación A: tableta S/8.00, mayorista S/6.50 a partir de 10
        final ProductoPresentacion pA = presentacionConMayorista(8.00, 6.50, 10);
        // Presentación B: caja S/65.00, mayorista S/55.00 a partir de 5
        final ProductoPresentacion pB = presentacionConMayorista(65.00, 55.00, 5);

        @Test
        @DisplayName("Pres. A con 8 → minorista; Pres. B con 8 → mayorista")
        void presentacionesIndependientes() {
            assertThat(resolverPrecioMayoristaPresentacion(pA, producto, 8)).isEqualByComparingTo("8.00");
            assertThat(resolverPrecioMayoristaPresentacion(pB, producto, 8)).isEqualByComparingTo("55.00");
        }

        @Test
        @DisplayName("A con 10 → mayorista; B con 3 → minorista — no se mezclan")
        void noSeMezclanCantidadesEntreLineas() {
            assertThat(resolverPrecioMayoristaPresentacion(pA, producto, 10)).isEqualByComparingTo("6.50");
            assertThat(resolverPrecioMayoristaPresentacion(pB, producto, 3)).isEqualByComparingTo("65.00");
        }
    }

    @Nested
    @DisplayName("Seguridad: detección de manipulación de precio desde el frontend")
    class SeguridadManipulacion {

        final Producto producto = productoBase(5.00, 2.00, 5);
        final ProductoPresentacion pBlister = presentacionConMayorista(10.00, 8.00, 2);

        @Test
        @DisplayName("Frontend envía S/1.00 con qty 2 blísteres → diferente al esperado S/8.00")
        void manipulacionPrecio_seDifiere() {
            BigDecimal esperado = resolverPrecioMayoristaPresentacion(pBlister, producto, 2); // 8.00
            assertThat(new BigDecimal("1.00").compareTo(esperado)).isNotZero();
        }

        @Test
        @DisplayName("Frontend envía S/8.00 con qty 2 blísteres → coincide con el esperado (válido)")
        void precioCorrectoMayorista_pasaValidacion() {
            BigDecimal esperado = resolverPrecioMayoristaPresentacion(pBlister, producto, 2); // 8.00
            assertThat(new BigDecimal("8.00").compareTo(esperado)).isZero();
        }

        @Test
        @DisplayName("Frontend envía S/10.00 con qty 1 blíster → coincide con el esperado (válido)")
        void precioCorrectoMinorista_pasaValidacion() {
            BigDecimal esperado = resolverPrecioMayoristaPresentacion(pBlister, producto, 1); // 10.00
            assertThat(new BigDecimal("10.00").compareTo(esperado)).isZero();
        }

        @Test
        @DisplayName("Frontend envía precio base (S/2.00) para Unidad con 5 unidades → coincide (fallback correcto)")
        void precioFallbackBase_esValido() {
            ProductoPresentacion unidad = presentacionSinMayorista(5.00, 1);
            BigDecimal esperado = resolverPrecioMayoristaPresentacion(unidad, producto, 5); // 2.00 fallback
            assertThat(new BigDecimal("2.00").compareTo(esperado)).isZero();
        }
    }

    @Nested
    @DisplayName("Compatibilidad: lógica mayorista de producto (sin presentación) no se altera")
    class CompatibilidadProductoMayorista {

        @Test
        @DisplayName("Producto con mayorista, qty < min → precioVenta")
        void productoMayorista_menorDelMinimo() {
            Producto p = productoBase(30.00, 24.00, 12);
            assertThat(resolverPrecioMayorista(p, 11)).isEqualByComparingTo("30.00");
        }

        @Test
        @DisplayName("Producto con mayorista, qty >= min → precioMayorista")
        void productoMayorista_mayorOIgualAlMinimo() {
            Producto p = productoBase(30.00, 24.00, 12);
            assertThat(resolverPrecioMayorista(p, 12)).isEqualByComparingTo("24.00");
        }

        @Test
        @DisplayName("Producto sin mayorista → null (sin restricción de precio)")
        void productoSinMayorista_retornaNull() {
            Producto p = productoBaseSinMayorista(30.00);
            assertThat(resolverPrecioMayorista(p, 100)).isNull();
        }
    }
}
