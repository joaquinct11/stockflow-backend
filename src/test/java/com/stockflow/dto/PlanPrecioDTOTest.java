package com.stockflow.dto;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

import static org.assertj.core.api.Assertions.assertThat;

class PlanPrecioDTOTest {

    @Test
    void veterinariaBasicoEsS109YProS189() {
        PlanPrecioDTO dto = PlanPrecioDTO.forRubro("VETERINARIA");
        assertThat(dto.getBasicoMensual()).isEqualTo(109);
        assertThat(dto.getProMensual()).isEqualTo(189);
    }

    @Test
    void veterinariaIgnoraCase() {
        assertThat(PlanPrecioDTO.forRubro("veterinaria").getBasicoMensual()).isEqualTo(109);
        assertThat(PlanPrecioDTO.forRubro("Veterinaria").getProMensual()).isEqualTo(189);
    }

    @ParameterizedTest
    @ValueSource(strings = { "FARMACIA", "BOTICA", "TIENDA_ROPA", "OTRO", "" })
    void rubroS89UsaPrecioDefault(String rubro) {
        PlanPrecioDTO dto = PlanPrecioDTO.forRubro(rubro);
        assertThat(dto.getBasicoMensual()).isEqualTo(89);
        assertThat(dto.getProMensual()).isEqualTo(169);
    }

    @ParameterizedTest
    @ValueSource(strings = { "MINIMARKET", "FERRETERIA", "EMPRESA_SERVICIOS" })
    void rubroS99BasicoS179Pro(String rubro) {
        PlanPrecioDTO dto = PlanPrecioDTO.forRubro(rubro);
        assertThat(dto.getBasicoMensual()).isEqualTo(99);
        assertThat(dto.getProMensual()).isEqualTo(179);
    }

    @Test
    void tiendaBodegaUsaS79BasicoS149Pro() {
        PlanPrecioDTO dto = PlanPrecioDTO.forRubro("TIENDA");
        assertThat(dto.getBasicoMensual()).isEqualTo(79);
        assertThat(dto.getProMensual()).isEqualTo(149);
    }

    @Test
    void restauranteUsaS119BasicoS199Pro() {
        PlanPrecioDTO dto = PlanPrecioDTO.forRubro("RESTAURANTE");
        assertThat(dto.getBasicoMensual()).isEqualTo(119);
        assertThat(dto.getProMensual()).isEqualTo(199);
    }

    @Test
    void rubroNullUsaPrecioDefault() {
        PlanPrecioDTO dto = PlanPrecioDTO.forRubro(null);
        assertThat(dto.getBasicoMensual()).isEqualTo(89);
        assertThat(dto.getProMensual()).isEqualTo(169);
    }
}
