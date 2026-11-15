package com.stockflow.controller;

import com.stockflow.dto.PlanCatalogoDTO;
import com.stockflow.dto.PlanPrecioDTO;
import com.stockflow.service.PlanCatalogoService;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.HttpStatus;

import java.math.BigDecimal;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class PlanCatalogoControllerTest {

    @Mock
    private PlanCatalogoService planCatalogoService;

    @InjectMocks
    private PlanCatalogoController controller;

    // ── GET /auth/planes/precios?rubro=X ────────────────────────────────────

    @Test
    void preciosPorRubro_delegaAlServicio() {
        when(planCatalogoService.getPreciosPorRubroOperativo("VETERINARIA"))
                .thenReturn(new PlanPrecioDTO(109, 189));

        var response = controller.preciosPorRubro("VETERINARIA");

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(response.getBody()).isNotNull();
        assertThat(response.getBody().getBasicoMensual()).isEqualTo(109);
        assertThat(response.getBody().getProMensual()).isEqualTo(189);
        verify(planCatalogoService).getPreciosPorRubroOperativo("VETERINARIA");
        verifyNoMoreInteractions(planCatalogoService);
    }

    @Test
    void preciosPorRubro_ferreteria99Y179() {
        when(planCatalogoService.getPreciosPorRubroOperativo("FERRETERIA"))
                .thenReturn(new PlanPrecioDTO(99, 179));

        var response = controller.preciosPorRubro("FERRETERIA");

        assertThat(response.getBody().getBasicoMensual()).isEqualTo(99);
        assertThat(response.getBody().getProMensual()).isEqualTo(179);
    }

    @Test
    void preciosPorRubro_rubroDefaultDevuelve89Y169() {
        when(planCatalogoService.getPreciosPorRubroOperativo("OTRO"))
                .thenReturn(new PlanPrecioDTO(89, 169));

        var response = controller.preciosPorRubro("OTRO");

        assertThat(response.getBody().getBasicoMensual()).isEqualTo(89);
        assertThat(response.getBody().getProMensual()).isEqualTo(169);
    }

    // ── GET /auth/planes/catalogo?segmento=X ────────────────────────────────

    @Test
    void porSegmento_delegaAlServicio() {
        PlanCatalogoDTO dto = PlanCatalogoDTO.builder()
                .codigo("VETERINARIA_BASICO").segmento("VETERINARIA").rubroOperativo("VETERINARIA")
                .nivel("BASICO").precioMensual(new BigDecimal("109.00"))
                .maxUsuarios(10).maxSucursales(1).maxProductos(2000).build();
        when(planCatalogoService.listarPorSegmento("VETERINARIA")).thenReturn(List.of(dto));

        var response = controller.porSegmento("VETERINARIA");

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(response.getBody()).hasSize(1);
        assertThat(response.getBody().get(0).getCodigo()).isEqualTo("VETERINARIA_BASICO");
        verify(planCatalogoService).listarPorSegmento("VETERINARIA");
    }

    @Test
    void porCodigo_delegaAlServicio() {
        PlanCatalogoDTO dto = PlanCatalogoDTO.builder()
                .codigo("BOTICA_BASICO").segmento("FARMACIA_BOTICA").rubroOperativo("BOTICA")
                .nivel("BASICO").precioMensual(new BigDecimal("89.00"))
                .maxUsuarios(5).maxSucursales(1).maxProductos(2000).build();
        when(planCatalogoService.obtenerPorCodigo("BOTICA_BASICO")).thenReturn(dto);

        var response = controller.porCodigo("BOTICA_BASICO");

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(response.getBody().getRubroOperativo()).isEqualTo("BOTICA");
        verify(planCatalogoService).obtenerPorCodigo("BOTICA_BASICO");
    }
}
