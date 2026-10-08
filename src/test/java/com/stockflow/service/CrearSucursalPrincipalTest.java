package com.stockflow.service;

import com.stockflow.entity.Sucursal;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Verifica que Sucursal.builder() inicializa correctamente los campos booleanos
 * con @Builder.Default, y que @PrePersist actúa como red de seguridad JPA para
 * cualquier ruta de construcción que no use el builder.
 *
 * Bug original: bloqueadaPorPlan quedaba null al usar builder sin @Builder.Default,
 * causando "null value in column bloqueada_por_plan violates not-null constraint"
 * durante inicializarPrincipal() en el upgrade PRO.
 */
@DisplayName("Sucursal — inicialización de campos booleanos con builder y @PrePersist")
class CrearSucursalPrincipalTest {

    @Nested
    @DisplayName("bloqueadaPorPlan nunca es null al usar builder")
    class BloqueadaPorPlan {

        @Test
        @DisplayName("builder sin .bloqueadaPorPlan() → valor false, no null")
        void sinSetterExplicito_noEsNull() {
            Sucursal s = Sucursal.builder()
                    .tenantId("vet-59ba")
                    .nombre("Sucursal Principal")
                    .esPrincipal(true)
                    .activo(true)
                    .build();

            assertThat(s.getBloqueadaPorPlan())
                    .as("bloqueadaPorPlan no debe ser null al construir sin setter explícito")
                    .isNotNull();
        }

        @Test
        @DisplayName("valor por defecto de bloqueadaPorPlan es false")
        void valorPorDefectoEsFalse() {
            Sucursal s = Sucursal.builder()
                    .tenantId("vet-59ba")
                    .nombre("Sucursal Principal")
                    .build();

            assertThat(s.getBloqueadaPorPlan()).isFalse();
        }

        @Test
        @DisplayName("se puede establecer bloqueadaPorPlan = true explícitamente")
        void setterExplicito_true() {
            Sucursal s = Sucursal.builder()
                    .tenantId("vet-59ba")
                    .nombre("Sucursal extra")
                    .bloqueadaPorPlan(true)
                    .build();

            assertThat(s.getBloqueadaPorPlan()).isTrue();
        }
    }

    @Nested
    @DisplayName("activo y esPrincipal también tienen defaults correctos")
    class OtrosCamposBooleanos {

        @Test
        @DisplayName("activo por defecto es true")
        void activoDefecto_true() {
            Sucursal s = Sucursal.builder()
                    .tenantId("t1")
                    .nombre("S1")
                    .build();

            assertThat(s.getActivo())
                    .as("activo no debe ser null")
                    .isNotNull();
            assertThat(s.getActivo()).isTrue();
        }

        @Test
        @DisplayName("esPrincipal por defecto es false")
        void esPrincipalDefecto_false() {
            Sucursal s = Sucursal.builder()
                    .tenantId("t1")
                    .nombre("S1")
                    .build();

            assertThat(s.getEsPrincipal())
                    .as("esPrincipal no debe ser null")
                    .isNotNull();
            assertThat(s.getEsPrincipal()).isFalse();
        }

        @Test
        @DisplayName("sucursal principal: esPrincipal=true, activo=true, bloqueadaPorPlan=false")
        void sucursalPrincipal_todosLosBooleanosSonCorrectos() {
            Sucursal principal = Sucursal.builder()
                    .tenantId("vet-59ba")
                    .nombre("Sucursal Principal")
                    .esPrincipal(true)
                    .activo(true)
                    .bloqueadaPorPlan(false)
                    .build();

            assertThat(principal.getEsPrincipal()).isTrue();
            assertThat(principal.getActivo()).isTrue();
            assertThat(principal.getBloqueadaPorPlan()).isFalse();
        }
    }

    @Nested
    @DisplayName("@PrePersist — red de seguridad JPA antes de INSERT")
    class PrePersistDefaults {

        @Test
        @DisplayName("@PrePersist asigna false a bloqueadaPorPlan si era null")
        void prePersist_asignaBloqueadaPorPlanFalse() {
            Sucursal s = new Sucursal();
            s.aplicarDefaults();
            assertThat(s.getBloqueadaPorPlan()).isFalse();
        }

        @Test
        @DisplayName("@PrePersist asigna true a activo si era null")
        void prePersist_asignaActivoTrue() {
            Sucursal s = new Sucursal();
            s.aplicarDefaults();
            assertThat(s.getActivo()).isTrue();
        }

        @Test
        @DisplayName("@PrePersist asigna false a esPrincipal si era null")
        void prePersist_asignaEsPrincipalFalse() {
            Sucursal s = new Sucursal();
            s.aplicarDefaults();
            assertThat(s.getEsPrincipal()).isFalse();
        }

        @Test
        @DisplayName("@PrePersist no sobreescribe valores ya asignados")
        void prePersist_noSobrescribeValoresExistentes() {
            Sucursal s = new Sucursal();
            s.setBloqueadaPorPlan(true);
            s.setActivo(false);
            s.setEsPrincipal(true);

            s.aplicarDefaults();

            assertThat(s.getBloqueadaPorPlan()).isTrue();
            assertThat(s.getActivo()).isFalse();
            assertThat(s.getEsPrincipal()).isTrue();
        }
    }
}
