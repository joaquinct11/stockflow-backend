package com.stockflow.controller;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

@DisplayName("CulqiController — helpers de email tenant-scoped")
class CulqiEmailUtilsTest {

    // ── buildCulqiEmail ──────────────────────────────────────────────────────

    @Nested
    @DisplayName("buildCulqiEmail()")
    class BuildCulqiEmail {

        @Test
        @DisplayName("email normal + tenantId → user+tenantId@domain.com")
        void emailNormal_conTenantId_produceSufijo() {
            String result = CulqiController.buildCulqiEmail("user@gmail.com", "vet-59ba");
            assertThat(result).isEqualTo("user+vet-59ba@gmail.com");
        }

        @Test
        @DisplayName("email largo + tenantId con guiones → formato correcto")
        void emailLargo_tenantIdConGuiones() {
            String result = CulqiController.buildCulqiEmail(
                    "joaquincastillotello2001@gmail.com", "botica-santa-fe-596f");
            assertThat(result).isEqualTo("joaquincastillotello2001+botica-santa-fe-596f@gmail.com");
        }

        @Test
        @DisplayName("tenantId null → devuelve email sin cambios")
        void tenantIdNull_devuelveEmailSinCambios() {
            String result = CulqiController.buildCulqiEmail("user@gmail.com", null);
            assertThat(result).isEqualTo("user@gmail.com");
        }

        @Test
        @DisplayName("tenantId vacío → devuelve email sin cambios")
        void tenantIdVacio_devuelveEmailSinCambios() {
            String result = CulqiController.buildCulqiEmail("user@gmail.com", "");
            assertThat(result).isEqualTo("user@gmail.com");
        }

        @Test
        @DisplayName("email sin @ → devuelve el mismo valor")
        void emailSinArroba_devuelveIgual() {
            String result = CulqiController.buildCulqiEmail("invalido", "vet-59ba");
            assertThat(result).isEqualTo("invalido");
        }

        @Test
        @DisplayName("email null → devuelve null")
        void emailNull_devuelveNull() {
            String result = CulqiController.buildCulqiEmail(null, "vet-59ba");
            assertThat(result).isNull();
        }

        @Test
        @DisplayName("dos tenants del mismo usuario producen emails distintos")
        void dosTenants_mismoUsuario_emailsDistintos() {
            String botica = CulqiController.buildCulqiEmail("user@gmail.com", "botica-596f");
            String vet    = CulqiController.buildCulqiEmail("user@gmail.com", "vet-59ba");
            assertThat(botica).isNotEqualTo(vet);
        }
    }

    // ── normalizarEmailCulqi ─────────────────────────────────────────────────

    @Nested
    @DisplayName("normalizarEmailCulqi()")
    class NormalizarEmail {

        @Test
        @DisplayName("email tenant-scoped → email real (quita sufijo)")
        void emailConSufijo_quitaSufijo() {
            String result = CulqiController.normalizarEmailCulqi("user+vet-59ba@gmail.com");
            assertThat(result).isEqualTo("user@gmail.com");
        }

        @Test
        @DisplayName("email sin sufijo → devuelve igual")
        void emailSinSufijo_devuelveIgual() {
            String result = CulqiController.normalizarEmailCulqi("user@gmail.com");
            assertThat(result).isEqualTo("user@gmail.com");
        }

        @Test
        @DisplayName("idempotente: normalizar dos veces da el mismo resultado")
        void idempotente() {
            String first  = CulqiController.normalizarEmailCulqi("user+vet@gmail.com");
            String second = CulqiController.normalizarEmailCulqi(first);
            assertThat(first).isEqualTo(second);
        }

        @Test
        @DisplayName("null → devuelve null")
        void nullInput_devuelveNull() {
            assertThat(CulqiController.normalizarEmailCulqi(null)).isNull();
        }

        @Test
        @DisplayName("email con + pero sin @ después → devuelve igual (no parseable)")
        void emailConPlusSinAt() {
            // '+' aparece antes de '@' pero no hay '@' → no hay sufijo válido
            String result = CulqiController.normalizarEmailCulqi("invalido+sufijo");
            assertThat(result).isEqualTo("invalido+sufijo");
        }
    }

    // ── extraerTenantIdDeEmail ───────────────────────────────────────────────

    @Nested
    @DisplayName("extraerTenantIdDeEmail()")
    class ExtraerTenantId {

        @Test
        @DisplayName("email tenant-scoped → extrae tenantId correctamente")
        void emailConSufijo_extraeTenantId() {
            String result = CulqiController.extraerTenantIdDeEmail("user+vet-59ba@gmail.com");
            assertThat(result).isEqualTo("vet-59ba");
        }

        @Test
        @DisplayName("email largo → extrae tenantId complejo")
        void emailLargo_extraeTenantIdComplejo() {
            String result = CulqiController.extraerTenantIdDeEmail(
                    "joaquincastillotello2001+botica-santa-fe-596f@gmail.com");
            assertThat(result).isEqualTo("botica-santa-fe-596f");
        }

        @Test
        @DisplayName("email sin sufijo → retorna null")
        void emailSinSufijo_retornaNull() {
            assertThat(CulqiController.extraerTenantIdDeEmail("user@gmail.com")).isNull();
        }

        @Test
        @DisplayName("null → retorna null")
        void nullInput_retornaNull() {
            assertThat(CulqiController.extraerTenantIdDeEmail(null)).isNull();
        }
    }
}
