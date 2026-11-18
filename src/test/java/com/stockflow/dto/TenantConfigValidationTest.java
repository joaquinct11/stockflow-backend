package com.stockflow.dto;

import jakarta.validation.ConstraintViolation;
import jakarta.validation.Validation;
import jakarta.validation.Validator;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;

@DisplayName("TenantConfigDTO — validación Bean Validation de ubigeoDespacho")
class TenantConfigValidationTest {

    private static Validator validator;

    @BeforeAll
    static void setup() {
        validator = Validation.buildDefaultValidatorFactory().getValidator();
    }

    private Set<ConstraintViolation<TenantConfigDTO>> validate(String ubigeo) {
        TenantConfigDTO dto = TenantConfigDTO.builder().ubigeoDespacho(ubigeo).build();
        return validator.validateProperty(dto, "ubigeoDespacho");
    }

    @Test
    @DisplayName("ubigeoDespacho null → válido (campo opcional)")
    void ubigeo_null_esValido() {
        assertThat(validate(null)).isEmpty();
    }

    @Test
    @DisplayName("ubigeoDespacho vacío → válido (tenant sin GRE)")
    void ubigeo_vacio_esValido() {
        assertThat(validate("")).isEmpty();
    }

    @Test
    @DisplayName("ubigeoDespacho 6 dígitos → válido")
    void ubigeo_6digitos_esValido() {
        assertThat(validate("150101")).isEmpty();
    }

    @Test
    @DisplayName("ubigeoDespacho 5 dígitos → inválido")
    void ubigeo_5digitos_esInvalido() {
        assertThat(validate("12345")).isNotEmpty();
    }

    @Test
    @DisplayName("ubigeoDespacho 7 dígitos → inválido")
    void ubigeo_7digitos_esInvalido() {
        assertThat(validate("1234567")).isNotEmpty();
    }

    @Test
    @DisplayName("ubigeoDespacho con letra → inválido")
    void ubigeo_conLetra_esInvalido() {
        assertThat(validate("15010a")).isNotEmpty();
    }

    @Test
    @DisplayName("ubigeoDespacho con espacios → inválido")
    void ubigeo_conEspacios_esInvalido() {
        assertThat(validate(" 150101")).isNotEmpty();
    }
}
