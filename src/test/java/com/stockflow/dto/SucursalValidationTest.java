package com.stockflow.dto;

import jakarta.validation.ConstraintViolation;
import jakarta.validation.Validation;
import jakarta.validation.Validator;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;

@DisplayName("SucursalDTO — validación Bean Validation de ubigeo")
class SucursalValidationTest {

    private static Validator validator;

    @BeforeAll
    static void setup() {
        validator = Validation.buildDefaultValidatorFactory().getValidator();
    }

    private Set<ConstraintViolation<SucursalDTO>> validate(String ubigeo) {
        SucursalDTO dto = SucursalDTO.builder().nombre("Sucursal Test").ubigeo(ubigeo).build();
        return validator.validateProperty(dto, "ubigeo");
    }

    @Test
    @DisplayName("ubigeo null → válido (campo opcional)")
    void ubigeo_null_esValido() {
        assertThat(validate(null)).isEmpty();
    }

    @Test
    @DisplayName("ubigeo vacío → válido (sucursal sin GRE)")
    void ubigeo_vacio_esValido() {
        assertThat(validate("")).isEmpty();
    }

    @Test
    @DisplayName("ubigeo 6 dígitos → válido")
    void ubigeo_6digitos_esValido() {
        assertThat(validate("040101")).isEmpty();
    }

    @Test
    @DisplayName("ubigeo 5 dígitos → inválido")
    void ubigeo_5digitos_esInvalido() {
        assertThat(validate("12345")).isNotEmpty();
    }

    @Test
    @DisplayName("ubigeo 7 dígitos → inválido")
    void ubigeo_7digitos_esInvalido() {
        assertThat(validate("1234567")).isNotEmpty();
    }

    @Test
    @DisplayName("ubigeo con letra → inválido")
    void ubigeo_conLetra_esInvalido() {
        assertThat(validate("04010a")).isNotEmpty();
    }

    @Test
    @DisplayName("ubigeo con espacios → inválido")
    void ubigeo_conEspacios_esInvalido() {
        assertThat(validate("040101 ")).isNotEmpty();
    }

    @Test
    @DisplayName("nombre requerido — validación preexistente no fue alterada")
    void nombre_requerido_sigueActivo() {
        SucursalDTO dto = SucursalDTO.builder().nombre("").build();
        Set<ConstraintViolation<SucursalDTO>> violations = validator.validateProperty(dto, "nombre");
        assertThat(violations).isNotEmpty();
    }
}
