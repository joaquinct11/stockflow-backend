package com.stockflow.service;

import com.stockflow.entity.PlanCatalogo;
import com.stockflow.exception.BadRequestException;
import com.stockflow.repository.PlanCatalogoRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.math.BigDecimal;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class PlanCatalogoServiceTest {

    @Mock
    private PlanCatalogoRepository planCatalogoRepository;

    @InjectMocks
    private PlanCatalogoService planCatalogoService;

    private PlanCatalogo vetBasico;
    private PlanCatalogo vetPro;
    private PlanCatalogo boticaBasico;
    private PlanCatalogo tiendaRopaBasico;
    private PlanCatalogo tiendaBodegaBasico;

    @BeforeEach
    void setUp() {
        vetBasico = PlanCatalogo.builder()
                .codigo("VETERINARIA_BASICO").segmento("VETERINARIA").rubroOperativo("VETERINARIA")
                .nivel("BASICO").precioMensual(new BigDecimal("109.00"))
                .maxUsuarios(10).maxSucursales(1).maxProductos(2000).activo(true).build();

        vetPro = PlanCatalogo.builder()
                .codigo("VETERINARIA_PRO").segmento("VETERINARIA").rubroOperativo("VETERINARIA")
                .nivel("PRO").precioMensual(new BigDecimal("189.00"))
                .maxUsuarios(15).maxSucursales(5).maxProductos(5000).activo(true).build();

        boticaBasico = PlanCatalogo.builder()
                .codigo("BOTICA_BASICO").segmento("FARMACIA_BOTICA").rubroOperativo("BOTICA")
                .nivel("BASICO").precioMensual(new BigDecimal("89.00"))
                .maxUsuarios(5).maxSucursales(1).maxProductos(2000).activo(true).build();

        tiendaRopaBasico = PlanCatalogo.builder()
                .codigo("TIENDA_ROPA_BASICO").segmento("TIENDA_ROPA").rubroOperativo("TIENDA_ROPA")
                .nivel("BASICO").precioMensual(new BigDecimal("89.00"))
                .maxUsuarios(5).maxSucursales(1).maxProductos(2000).activo(true).build();

        tiendaBodegaBasico = PlanCatalogo.builder()
                .codigo("TIENDA_BODEGA_BASICO").segmento("TIENDA_BODEGA").rubroOperativo("TIENDA")
                .nivel("BASICO").precioMensual(new BigDecimal("79.00"))
                .maxUsuarios(5).maxSucursales(1).maxProductos(2000).activo(true).build();
    }

    // ── Precios por segmento ─────────────────────────────────────────────────

    @Test
    void veterinariaBasicoTienePrecio109() {
        when(planCatalogoRepository.findByCodigoAndActivoTrue("VETERINARIA_BASICO"))
                .thenReturn(Optional.of(vetBasico));
        var dto = planCatalogoService.obtenerPorCodigo("VETERINARIA_BASICO");
        assertThat(dto.getPrecioMensual()).isEqualByComparingTo("109.00");
    }

    @Test
    void veterinariaProTienePrecio189() {
        when(planCatalogoRepository.findByCodigoAndActivoTrue("VETERINARIA_PRO"))
                .thenReturn(Optional.of(vetPro));
        var dto = planCatalogoService.obtenerPorCodigo("VETERINARIA_PRO");
        assertThat(dto.getPrecioMensual()).isEqualByComparingTo("189.00");
    }

    @Test
    void boticaBasicoTienePrecio89() {
        when(planCatalogoRepository.findByCodigoAndActivoTrue("BOTICA_BASICO"))
                .thenReturn(Optional.of(boticaBasico));
        var dto = planCatalogoService.obtenerPorCodigo("BOTICA_BASICO");
        assertThat(dto.getPrecioMensual()).isEqualByComparingTo("89.00");
    }

    @Test
    void tiendaBodegaBasicoTienePrecio79() {
        when(planCatalogoRepository.findByCodigoAndActivoTrue("TIENDA_BODEGA_BASICO"))
                .thenReturn(Optional.of(tiendaBodegaBasico));
        var dto = planCatalogoService.obtenerPorCodigo("TIENDA_BODEGA_BASICO");
        assertThat(dto.getPrecioMensual()).isEqualByComparingTo("79.00");
    }

    // ── Límites por plan ─────────────────────────────────────────────────────

    @Test
    void veterinariaBasicoPermite10Usuarios() {
        when(planCatalogoRepository.findByCodigoAndActivoTrue("VETERINARIA_BASICO"))
                .thenReturn(Optional.of(vetBasico));
        var dto = planCatalogoService.obtenerPorCodigo("VETERINARIA_BASICO");
        assertThat(dto.getMaxUsuarios()).isEqualTo(10);
    }

    @Test
    void tiendaRopaBasicoPermite5Usuarios() {
        when(planCatalogoRepository.findByCodigoAndActivoTrue("TIENDA_ROPA_BASICO"))
                .thenReturn(Optional.of(tiendaRopaBasico));
        var dto = planCatalogoService.obtenerPorCodigo("TIENDA_ROPA_BASICO");
        assertThat(dto.getMaxUsuarios()).isEqualTo(5);
    }

    @Test
    void todoBasicoPermite1SucursalY2000Productos() {
        for (PlanCatalogo plan : List.of(vetBasico, boticaBasico, tiendaRopaBasico, tiendaBodegaBasico)) {
            assertThat(plan.getMaxSucursales()).isEqualTo(1);
            assertThat(plan.getMaxProductos()).isEqualTo(2000);
        }
    }

    @Test
    void todoProPermite5SucursalesY5000Productos() {
        assertThat(vetPro.getMaxSucursales()).isEqualTo(5);
        assertThat(vetPro.getMaxProductos()).isEqualTo(5000);
        assertThat(vetPro.getMaxUsuarios()).isEqualTo(15);
    }

    // ── Validación de compatibilidad rubro-plan ──────────────────────────────

    @Test
    void validarYObtener_compatibleNoLanzaExcepcion() {
        when(planCatalogoRepository.findByCodigoAndActivoTrue("VETERINARIA_BASICO"))
                .thenReturn(Optional.of(vetBasico));
        assertThatNoException().isThrownBy(
                () -> planCatalogoService.validarYObtener("VETERINARIA_BASICO", "VETERINARIA"));
    }

    @Test
    void validarYObtener_incompatibleLanzaBadRequest() {
        when(planCatalogoRepository.findByCodigoAndActivoTrue("BOTICA_BASICO"))
                .thenReturn(Optional.of(boticaBasico));
        assertThatThrownBy(
                () -> planCatalogoService.validarYObtener("BOTICA_BASICO", "VETERINARIA"))
                .isInstanceOf(BadRequestException.class)
                .hasMessageContaining("no es compatible");
    }

    @Test
    void planInactivoLanzaBadRequest() {
        when(planCatalogoRepository.findByCodigoAndActivoTrue("VETERINARIA_BASICO"))
                .thenReturn(Optional.empty());
        assertThatThrownBy(() -> planCatalogoService.obtenerPorCodigo("VETERINARIA_BASICO"))
                .isInstanceOf(BadRequestException.class);
    }

    @Test
    void codigoInexistenteLanzaBadRequest() {
        when(planCatalogoRepository.findByCodigoAndActivoTrue("INVALIDO_BASICO"))
                .thenReturn(Optional.empty());
        assertThatThrownBy(() -> planCatalogoService.obtenerPorCodigo("INVALIDO_BASICO"))
                .isInstanceOf(BadRequestException.class);
    }

    // ── Precios por rubro operativo (unificados con catálogo) ────────────────

    @Test
    void getPreciosPorRubroOperativo_veterinariaDevuelve109Y189() {
        when(planCatalogoRepository.findByRubroOperativoAndActivoTrueOrderByNivelAsc("VETERINARIA"))
                .thenReturn(List.of(vetBasico, vetPro));
        var dto = planCatalogoService.getPreciosPorRubroOperativo("VETERINARIA");
        assertThat(dto.getBasicoMensual()).isEqualTo(109);
        assertThat(dto.getProMensual()).isEqualTo(189);
    }

    @Test
    void getPreciosPorRubroOperativo_tiendaDevuelve79Y149() {
        PlanCatalogo tiendaPro = PlanCatalogo.builder()
                .codigo("TIENDA_BODEGA_PRO").segmento("TIENDA_BODEGA").rubroOperativo("TIENDA")
                .nivel("PRO").precioMensual(new BigDecimal("149.00"))
                .maxUsuarios(15).maxSucursales(5).maxProductos(5000).activo(true).build();
        when(planCatalogoRepository.findByRubroOperativoAndActivoTrueOrderByNivelAsc("TIENDA"))
                .thenReturn(List.of(tiendaBodegaBasico, tiendaPro));
        var dto = planCatalogoService.getPreciosPorRubroOperativo("TIENDA");
        assertThat(dto.getBasicoMensual()).isEqualTo(79);
        assertThat(dto.getProMensual()).isEqualTo(149);
    }

    @Test
    void getPreciosPorRubroOperativo_rubroDesconocidoCaeEnFallbackOTRO() {
        PlanCatalogo otroBasico = PlanCatalogo.builder()
                .codigo("OTRO_BASICO").segmento("OTRO").rubroOperativo("OTRO")
                .nivel("BASICO").precioMensual(new BigDecimal("89.00"))
                .maxUsuarios(5).maxSucursales(1).maxProductos(2000).activo(true).build();
        PlanCatalogo otroPro = PlanCatalogo.builder()
                .codigo("OTRO_PRO").segmento("OTRO").rubroOperativo("OTRO")
                .nivel("PRO").precioMensual(new BigDecimal("169.00"))
                .maxUsuarios(15).maxSucursales(5).maxProductos(5000).activo(true).build();
        when(planCatalogoRepository.findByRubroOperativoAndActivoTrueOrderByNivelAsc("DESCONOCIDO"))
                .thenReturn(List.of());
        when(planCatalogoRepository.findByRubroOperativoAndActivoTrueOrderByNivelAsc("OTRO"))
                .thenReturn(List.of(otroBasico, otroPro));
        var dto = planCatalogoService.getPreciosPorRubroOperativo("DESCONOCIDO");
        assertThat(dto.getBasicoMensual()).isEqualTo(89);
        assertThat(dto.getProMensual()).isEqualTo(169);
    }

    // ── Listado por segmento diferencia TIENDA_ROPA de TIENDA_BODEGA ─────────

    @Test
    void listarPorSegmento_noConfundeTiendaRopaConTiendaBodega() {
        when(planCatalogoRepository.findBySegmentoAndActivoTrueOrderByNivelAsc("TIENDA_ROPA"))
                .thenReturn(List.of(tiendaRopaBasico));
        when(planCatalogoRepository.findBySegmentoAndActivoTrueOrderByNivelAsc("TIENDA_BODEGA"))
                .thenReturn(List.of(tiendaBodegaBasico));

        var ropa   = planCatalogoService.listarPorSegmento("TIENDA_ROPA");
        var bodega = planCatalogoService.listarPorSegmento("TIENDA_BODEGA");

        assertThat(ropa).hasSize(1).allMatch(p -> p.getCodigo().startsWith("TIENDA_ROPA"));
        assertThat(bodega).hasSize(1).allMatch(p -> p.getCodigo().startsWith("TIENDA_BODEGA"));
        assertThat(ropa.get(0).getPrecioMensual()).isEqualByComparingTo("89.00");
        assertThat(bodega.get(0).getPrecioMensual()).isEqualByComparingTo("79.00");
    }
}
