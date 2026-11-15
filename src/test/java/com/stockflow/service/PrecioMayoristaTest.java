package com.stockflow.service;

import com.stockflow.entity.Producto;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.math.RoundingMode;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Tests unitarios para la lógica de precios mayoristas.
 * Replica el método resolverPrecioMayorista de VentaController
 * para verificar las reglas de negocio sin levantar contexto Spring.
 */
class PrecioMayoristaTest {

    // Replica exacta del método privado en VentaController
    private BigDecimal resolverPrecioMayorista(Producto producto, int cantidad) {
        if (producto.getPrecioMayorista() == null || producto.getCantidadMinimaMayorista() == null) {
            return null;
        }
        if (cantidad >= producto.getCantidadMinimaMayorista()) {
            return producto.getPrecioMayorista();
        }
        return producto.getPrecioVenta();
    }

    private Producto productoConMayorista(double precioVenta, double precioMayorista, int cantMinima) {
        return Producto.builder()
                .nombre("Juguete Test")
                .precioVenta(BigDecimal.valueOf(precioVenta))
                .precioMayorista(BigDecimal.valueOf(precioMayorista))
                .cantidadMinimaMayorista(cantMinima)
                .costoUnitario(BigDecimal.valueOf(10.00))
                .tenantId("tenant-test")
                .build();
    }

    private Producto productoSinMayorista(double precioVenta) {
        return Producto.builder()
                .nombre("Producto Sin Mayorista")
                .precioVenta(BigDecimal.valueOf(precioVenta))
                .costoUnitario(BigDecimal.valueOf(5.00))
                .tenantId("tenant-test")
                .build();
    }

    @Nested
    @DisplayName("Producto sin configuración mayorista")
    class SinMayorista {

        @Test
        @DisplayName("Devuelve null para cualquier cantidad — precio libre")
        void sinConfiguracion_devuelveNull() {
            Producto p = productoSinMayorista(30.00);
            assertThat(resolverPrecioMayorista(p, 1)).isNull();
            assertThat(resolverPrecioMayorista(p, 100)).isNull();
        }

        @Test
        @DisplayName("Solo precioMayorista sin cantidadMinima devuelve null")
        void soloPrecioMayoristaDevuelveNull() {
            Producto p = Producto.builder()
                    .precioVenta(BigDecimal.valueOf(30.00))
                    .precioMayorista(BigDecimal.valueOf(24.00))
                    // cantidadMinimaMayorista = null
                    .build();
            assertThat(resolverPrecioMayorista(p, 20)).isNull();
        }

        @Test
        @DisplayName("Solo cantidadMinima sin precioMayorista devuelve null")
        void soloCantidadMinimaDevuelveNull() {
            Producto p = Producto.builder()
                    .precioVenta(BigDecimal.valueOf(30.00))
                    // precioMayorista = null
                    .cantidadMinimaMayorista(12)
                    .build();
            assertThat(resolverPrecioMayorista(p, 20)).isNull();
        }
    }

    @Nested
    @DisplayName("Producto con configuración mayorista (S/30 unitario, S/24 mayorista, mínimo 12)")
    class ConMayorista {

        final Producto juguete = productoConMayorista(30.00, 24.00, 12);

        @Test
        @DisplayName("Cantidad 1 — aplica precio unitario")
        void cantidad1_precioUnitario() {
            BigDecimal resultado = resolverPrecioMayorista(juguete, 1);
            assertThat(resultado).isEqualByComparingTo("30.00");
        }

        @Test
        @DisplayName("Cantidad 11 — por debajo del mínimo, aplica precio unitario")
        void cantidadMenosDelMinimo_precioUnitario() {
            BigDecimal resultado = resolverPrecioMayorista(juguete, 11);
            assertThat(resultado).isEqualByComparingTo("30.00");
        }

        @Test
        @DisplayName("Cantidad 12 — exactamente el mínimo, aplica precio mayorista")
        void cantidadIgualAlMinimo_precioMayorista() {
            BigDecimal resultado = resolverPrecioMayorista(juguete, 12);
            assertThat(resultado).isEqualByComparingTo("24.00");
        }

        @Test
        @DisplayName("Cantidad 15 — por encima del mínimo, aplica precio mayorista")
        void cantidadMayorAlMinimo_precioMayorista() {
            BigDecimal resultado = resolverPrecioMayorista(juguete, 15);
            assertThat(resultado).isEqualByComparingTo("24.00");
        }

        @Test
        @DisplayName("Cantidad 100 — muy por encima, sigue siendo precio mayorista")
        void cantidadMuyAlta_precioMayorista() {
            BigDecimal resultado = resolverPrecioMayorista(juguete, 100);
            assertThat(resultado).isEqualByComparingTo("24.00");
        }

        @Test
        @DisplayName("Verificar subtotal correcto: 12 × S/24 = S/288.00")
        void subtotalCorrectoConMayorista() {
            BigDecimal precio = resolverPrecioMayorista(juguete, 12);
            BigDecimal subtotal = precio.multiply(BigDecimal.valueOf(12)).setScale(2, RoundingMode.HALF_UP);
            assertThat(subtotal).isEqualByComparingTo("288.00");
        }

        @Test
        @DisplayName("Verificar subtotal correcto: 11 × S/30 = S/330.00")
        void subtotalCorrectoSinMayorista() {
            BigDecimal precio = resolverPrecioMayorista(juguete, 11);
            BigDecimal subtotal = precio.multiply(BigDecimal.valueOf(11)).setScale(2, RoundingMode.HALF_UP);
            assertThat(subtotal).isEqualByComparingTo("330.00");
        }
    }

    @Nested
    @DisplayName("Precio mayorista mayor que precio unitario (atípico pero válido)")
    class MayoristaAtipico {

        @Test
        @DisplayName("precioMayorista > precioVenta — el sistema lo permite, aplica normalmente")
        void precioMayoristaMayor_seAplicaDeTodasFormas() {
            Producto p = productoConMayorista(30.00, 35.00, 10);
            assertThat(resolverPrecioMayorista(p, 9)).isEqualByComparingTo("30.00");
            assertThat(resolverPrecioMayorista(p, 10)).isEqualByComparingTo("35.00");
        }
    }

    @Nested
    @DisplayName("Validación de precio enviado por frontend (simulación de la lógica del controlador)")
    class ValidacionPrecioFrontend {

        final Producto juguete = productoConMayorista(30.00, 24.00, 12);

        @Test
        @DisplayName("Vendedor envía precio correcto para cantidad < mínimo")
        void vendedorPrecioCorrectoMenorMinimo() {
            BigDecimal esperado = resolverPrecioMayorista(juguete, 5); // 30.00
            BigDecimal enviado = new BigDecimal("30.00").setScale(2, RoundingMode.HALF_UP);
            assertThat(enviado.compareTo(esperado.setScale(2, RoundingMode.HALF_UP))).isZero();
        }

        @Test
        @DisplayName("Vendedor envía precio correcto para cantidad >= mínimo")
        void vendedorPrecioCorrectoMayorMinimo() {
            BigDecimal esperado = resolverPrecioMayorista(juguete, 12); // 24.00
            BigDecimal enviado = new BigDecimal("24.00").setScale(2, RoundingMode.HALF_UP);
            assertThat(enviado.compareTo(esperado.setScale(2, RoundingMode.HALF_UP))).isZero();
        }

        @Test
        @DisplayName("Vendedor intenta manipular precio — se detecta la diferencia")
        void vendedorManipulaPrecio_seDifiere() {
            BigDecimal esperado = resolverPrecioMayorista(juguete, 12); // 24.00
            BigDecimal manipulado = new BigDecimal("1.00").setScale(2, RoundingMode.HALF_UP);
            // El controlador lanzaría BadRequestException; aquí verificamos la comparación
            assertThat(manipulado.compareTo(esperado.setScale(2, RoundingMode.HALF_UP))).isNotZero();
        }

        @Test
        @DisplayName("Producto sin mayorista — resolverPrecioMayorista devuelve null, se acepta cualquier precio")
        void productoSinMayorista_aceptaCualquierPrecio() {
            Producto p = productoSinMayorista(30.00);
            BigDecimal resultado = resolverPrecioMayorista(p, 100);
            // null significa: sin restricción de precio mayorista — comportamiento anterior
            assertThat(resultado).isNull();
        }
    }

    @Nested
    @DisplayName("Comportamiento de 'puedeEditarPrecio' según lógica del controlador")
    class LogicaPuedeEditarPrecio {

        // Simula la lógica de autorización del controlador dado un set de authorities.
        // El controlador actual chequea ROLE_ADMIN || ROLE_GERENTE || PERM_DESCUENTO_MANUAL.
        private boolean puedeEditarPrecio(java.util.List<String> authorities) {
            return authorities.stream()
                    .anyMatch(a -> a.equals("ROLE_ADMIN")
                               || a.equals("ROLE_GERENTE")
                               || a.equals("PERM_DESCUENTO_MANUAL"));
        }

        @Test
        @DisplayName("ROLE_ADMIN → puede editar precio")
        void roleAdmin_puedeEditar() {
            assertThat(puedeEditarPrecio(java.util.List.of("ROLE_ADMIN"))).isTrue();
        }

        @Test
        @DisplayName("ROLE_GERENTE → puede editar precio")
        void roleGerente_puedeEditar() {
            assertThat(puedeEditarPrecio(java.util.List.of(
                    "ROLE_GERENTE", "PERM_CREAR_VENTA"
            ))).isTrue();
        }

        @Test
        @DisplayName("ROLE_VENDEDOR sin permisos extras → no puede editar precio")
        void roleVendedor_noPuedeEditar() {
            assertThat(puedeEditarPrecio(java.util.List.of(
                    "ROLE_VENDEDOR",
                    "PERM_CREAR_VENTA", "PERM_VER_PRODUCTOS", "PERM_VER_MIS_VENTAS"
            ))).isFalse();
        }

        @Test
        @DisplayName("PERM_DESCUENTO_MANUAL existe en el catálogo (V116) — es asignable a usuarios")
        void permDescuentoManual_existeEnCatalogo() {
            // Verifica que RolePermissionDefaults.ALL_PERMISSIONS incluye DESCUENTO_MANUAL.
            // Esto confirma que puede insertarse en la tabla permisos y asignarse via
            // AdminController.asignarPermisos a usuarios individuales (ej. VENDEDORs de TIENDA_ROPA).
            boolean existeEnCatalogo = com.stockflow.config.RolePermissionDefaults.ALL_PERMISSIONS
                    .contains("DESCUENTO_MANUAL");
            assertThat(existeEnCatalogo)
                    .as("DESCUENTO_MANUAL debe estar en ALL_PERMISSIONS para ser asignable via UsuarioPermisoService")
                    .isTrue();
        }

        @Test
        @DisplayName("VENDEDOR con PERM_DESCUENTO_MANUAL → puede editar precio (TIENDA_ROPA flow)")
        void vendedorConDescuentoManual_puedeEditar() {
            assertThat(puedeEditarPrecio(java.util.List.of(
                    "ROLE_VENDEDOR", "PERM_CREAR_VENTA", "PERM_DESCUENTO_MANUAL"
            ))).isTrue();
        }
    }

    @Nested
    @DisplayName("Precio arbitrario en productos SIN configuración mayorista (compatibilidad preexistente)")
    class PrecioArbitrarioSinMayorista {

        // Contexto: ANTES de este feature, el backend confiaba en el precio del frontend.
        // Para productos sin campos mayorista, esa confianza se conserva intencionalmente.

        final Producto productoLegacy = productoSinMayorista(50.00);

        @Test
        @DisplayName("precioEsperado es null para producto sin mayorista — no hay restricción de precio")
        void sinMayorista_precioEsperadoNull() {
            BigDecimal precioEsperado = resolverPrecioMayorista(productoLegacy, 5);
            assertThat(precioEsperado).isNull();
            // La lógica del controlador: if (!esAdmin && precioEsperado != null) { lanzar error }
            // Como precioEsperado == null → la condición siempre es false → precio libre
        }

        @Test
        @DisplayName("Precio arbitrario bajo (ej. S/1) pasa la validación: precioEsperado es null")
        void precioArbitrarioBajo_pasaValidacion() {
            // Replica: precioFinal = detalleDTO.getPrecioUnitario() cuando precioEsperado == null
            BigDecimal precioEsperado = resolverPrecioMayorista(productoLegacy, 1);
            BigDecimal precioEnviado = new BigDecimal("1.00");
            // Simula la condición del controlador
            boolean seValidaPrice = (precioEsperado != null);
            assertThat(seValidaPrice).isFalse(); // confirma que no se valida
            // precioFinal = precioEnviado (comportamiento preexistente conservado)
            BigDecimal precioFinal = seValidaPrice ? precioEsperado : precioEnviado;
            assertThat(precioFinal).isEqualByComparingTo("1.00");
        }

        @Test
        @DisplayName("Config parcial: solo precioMayorista → null, precio libre (misma ruta que sin config)")
        void configParcialSoloPrecio_precioLibre() {
            Producto p = Producto.builder()
                    .precioVenta(BigDecimal.valueOf(50.00))
                    .precioMayorista(BigDecimal.valueOf(40.00))
                    // cantidadMinimaMayorista = null → config incompleta
                    .build();
            assertThat(resolverPrecioMayorista(p, 20)).isNull();
        }

        @Test
        @DisplayName("Config parcial: solo cantidadMinima → null, precio libre (misma ruta que sin config)")
        void configParcialSoloCantidad_precioLibre() {
            Producto p = Producto.builder()
                    .precioVenta(BigDecimal.valueOf(50.00))
                    // precioMayorista = null → config incompleta
                    .cantidadMinimaMayorista(10)
                    .build();
            assertThat(resolverPrecioMayorista(p, 20)).isNull();
        }
    }
}
