package com.stockflow.controller;

import com.stockflow.dto.ProductoDTO;
import com.stockflow.entity.Producto;
import com.stockflow.entity.ProductoStockSucursal;
import com.stockflow.entity.Sucursal;
import com.stockflow.mapper.ProductoMapper;
import com.stockflow.repository.MovimientoInventarioRepository;
import com.stockflow.repository.ProductoStockSucursalRepository;
import com.stockflow.repository.ProductoVarianteStockSucursalRepository;
import com.stockflow.service.ProductoService;
import com.stockflow.service.StockLoteService;
import com.stockflow.util.TenantContext;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;
import org.springframework.http.ResponseEntity;

import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.when;

/**
 * Verifica que el filtro de presencia por sucursal usa containsKey (fila existente en
 * producto_stock_sucursal), no stock > 0. Un producto con stock=0 en la sucursal sigue
 * perteneciendo a ella y debe aparecer en la respuesta.
 */
@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
@DisplayName("ProductoController — filtro presencia por sucursal (no stock > 0)")
class ProductoFiltroSucursalTest {

    private static final String TENANT_VET    = "vet-ca35";
    private static final String TENANT_OTRO   = "otro-tenant-00";
    private static final Long   SUCURSAL_18   = 18L;
    private static final Long   SUCURSAL_19   = 19L;
    private static final Long   PROD_89       = 89L;
    private static final Long   PROD_OTRO     = 999L;

    @Mock private ProductoService                        productoService;
    @Mock private ProductoMapper                         productoMapper;
    @Mock private MovimientoInventarioRepository         movimientoRepository;
    @Mock private ProductoStockSucursalRepository        stockSucursalRepository;
    @Mock private ProductoVarianteStockSucursalRepository varianteStockSucursalRepository;
    @Mock private StockLoteService                       stockLoteService;

    @InjectMocks private ProductoController productoController;

    private Producto prod89;
    private ProductoDTO dto89;

    @BeforeEach
    void setUp() {
        TenantContext.setCurrentTenant(TENANT_VET);

        prod89 = Producto.builder().id(PROD_89).nombre("TEST VET 001").tenantId(TENANT_VET).activo(true)
                .stockActual(0).stockMinimo(10).stockMaximo(500).build();

        dto89 = ProductoDTO.builder().id(PROD_89).nombre("TEST VET 001").stockActual(0).build();

        // Defaults: sin variantes, sin vencimientos, stockLote lanza excepción (controlada)
        when(varianteStockSucursalRepository.sumStockPorProductoYSucursal(any(), any()))
                .thenReturn(List.of());
        when(movimientoRepository.findProximaFechaVencimientoPorProducto(any(), any()))
                .thenReturn(List.of());
        when(stockLoteService.getStockVigenteBatch(any(), any(), any()))
                .thenReturn(Map.of());
        when(productoService.obtenerProductosPorTenant(TENANT_VET))
                .thenReturn(List.of(prod89));
        when(productoMapper.toDTOList(List.of(prod89)))
                .thenReturn(List.of(dto89));
    }

    @AfterEach
    void tearDown() {
        TenantContext.clear();
    }

    // ── Helpers ──────────────────────────────────────────────────────────────

    private ProductoStockSucursal stockRow(Producto p, Long sucursalId, int stock) {
        Sucursal suc = Sucursal.builder().id(sucursalId).build();
        return ProductoStockSucursal.builder()
                .producto(p).sucursal(suc).tenantId(TENANT_VET).stockActual(stock).build();
    }

    private ResponseEntity<List<ProductoDTO>> get(Long sucursalId) {
        return productoController.obtenerTodos(sucursalId, false);
    }

    // ── Caso A: stock=0 en sucursal → debe aparecer ───────────────────────────

    @Test
    @DisplayName("A — producto con stock=0 en sucursal 18 debe aparecer")
    void productoConStockCero_tieneFilaEnSucursal_debeAparecer() {
        when(stockSucursalRepository.findBySucursalIdAndTenantId(SUCURSAL_18, TENANT_VET))
                .thenReturn(List.of(stockRow(prod89, SUCURSAL_18, 0)));
        when(movimientoRepository.findProductoIdsConMovimientoEnSucursal(TENANT_VET, SUCURSAL_18))
                .thenReturn(List.of());

        List<ProductoDTO> result = get(SUCURSAL_18).getBody();

        assertThat(result).hasSize(1);
        assertThat(result.get(0).getId()).isEqualTo(PROD_89);
    }

    // ── Caso B: stock > 0 en sucursal → debe aparecer ────────────────────────

    @Test
    @DisplayName("B — producto con stock=50 en sucursal 18 debe aparecer")
    void productoConStockPositivo_tieneFilaEnSucursal_debeAparecer() {
        when(stockSucursalRepository.findBySucursalIdAndTenantId(SUCURSAL_18, TENANT_VET))
                .thenReturn(List.of(stockRow(prod89, SUCURSAL_18, 50)));
        when(movimientoRepository.findProductoIdsConMovimientoEnSucursal(TENANT_VET, SUCURSAL_18))
                .thenReturn(List.of());

        List<ProductoDTO> result = get(SUCURSAL_18).getBody();

        assertThat(result).hasSize(1);
        assertThat(result.get(0).getId()).isEqualTo(PROD_89);
    }

    // ── Caso C: sin fila de stock pero con movimiento → debe aparecer ─────────

    @Test
    @DisplayName("C — producto sin fila de stock pero con movimiento histórico en sucursal 18")
    void productoSinFilaStock_conMovimiento_debeAparecer() {
        when(stockSucursalRepository.findBySucursalIdAndTenantId(SUCURSAL_18, TENANT_VET))
                .thenReturn(List.of()); // sin fila en producto_stock_sucursal
        when(movimientoRepository.findProductoIdsConMovimientoEnSucursal(TENANT_VET, SUCURSAL_18))
                .thenReturn(List.of(PROD_89)); // pero tuvo movimiento

        List<ProductoDTO> result = get(SUCURSAL_18).getBody();

        assertThat(result).hasSize(1);
        assertThat(result.get(0).getId()).isEqualTo(PROD_89);
    }

    // ── Caso D: producto en sucursal 18 y 19 → aparece en ambas ──────────────

    @Test
    @DisplayName("D — producto con fila en sucursal 18 y 19 aparece en ambas consultas")
    void productoEnDosSucursales_apareceEnAmbas() {
        when(stockSucursalRepository.findBySucursalIdAndTenantId(SUCURSAL_18, TENANT_VET))
                .thenReturn(List.of(stockRow(prod89, SUCURSAL_18, 0)));
        when(movimientoRepository.findProductoIdsConMovimientoEnSucursal(TENANT_VET, SUCURSAL_18))
                .thenReturn(List.of());

        when(stockSucursalRepository.findBySucursalIdAndTenantId(SUCURSAL_19, TENANT_VET))
                .thenReturn(List.of(stockRow(prod89, SUCURSAL_19, 5)));
        when(movimientoRepository.findProductoIdsConMovimientoEnSucursal(TENANT_VET, SUCURSAL_19))
                .thenReturn(List.of());

        List<ProductoDTO> enSucursal18 = get(SUCURSAL_18).getBody();
        List<ProductoDTO> enSucursal19 = get(SUCURSAL_19).getBody();

        assertThat(enSucursal18).hasSize(1);
        assertThat(enSucursal19).hasSize(1);
        assertThat(enSucursal18.get(0).getId()).isEqualTo(PROD_89);
        assertThat(enSucursal19.get(0).getId()).isEqualTo(PROD_89);
    }

    // ── Caso E: producto en sucursal 18 pero NO en 19 → no aparece en 19 ─────

    @Test
    @DisplayName("E — producto en sucursal 18 pero no en 19 → no aparece en consulta de sucursal 19")
    void productoSoloEnSucursal18_noApareceEnSucursal19() {
        when(stockSucursalRepository.findBySucursalIdAndTenantId(SUCURSAL_19, TENANT_VET))
                .thenReturn(List.of()); // sin fila para sucursal 19
        when(movimientoRepository.findProductoIdsConMovimientoEnSucursal(TENANT_VET, SUCURSAL_19))
                .thenReturn(List.of()); // sin movimiento en sucursal 19

        List<ProductoDTO> result = get(SUCURSAL_19).getBody();

        assertThat(result).isEmpty();
    }

    // ── Caso F: producto de otro tenant → nunca debe aparecer ─────────────────

    @Test
    @DisplayName("F — producto de otro tenant no aparece aunque su sucursal coincida")
    void productoOtroTenant_nuncaAparece() {
        // El productoService ya filtra por tenantId — devuelve solo productos del tenant activo
        // producto de otro tenant no llega al pipeline del controller
        Producto prodOtro = Producto.builder().id(PROD_OTRO).nombre("OTRO TENANT")
                .tenantId(TENANT_OTRO).activo(true).stockActual(100).build();

        // El servicio devuelve SOLO productos de vet-ca35 (no del otro tenant)
        when(productoService.obtenerProductosPorTenant(TENANT_VET))
                .thenReturn(List.of(prod89)); // sin prodOtro
        when(productoMapper.toDTOList(List.of(prod89)))
                .thenReturn(List.of(dto89));

        // Aunque el stock repo devolviera algo, no importa — prodOtro no está en dtos
        when(stockSucursalRepository.findBySucursalIdAndTenantId(SUCURSAL_18, TENANT_VET))
                .thenReturn(List.of(stockRow(prod89, SUCURSAL_18, 0)));
        when(movimientoRepository.findProductoIdsConMovimientoEnSucursal(TENANT_VET, SUCURSAL_18))
                .thenReturn(List.of());

        List<ProductoDTO> result = get(SUCURSAL_18).getBody();

        // Solo producto 89, nunca PROD_OTRO
        assertThat(result).hasSize(1);
        assertThat(result.stream().map(ProductoDTO::getId)).doesNotContain(PROD_OTRO);
    }

    // ── Caso G: stock=0 y sin movimiento pero con fila válida → debe aparecer ─
    // (reproducción exacta del bug real: producto 89, tenant vet-ca35, sucursal 18, stock 0)

    @Test
    @DisplayName("G — reproducción exacta: prod 89, vet-ca35, sucursal 18, stock=0, sin movimiento → aparece")
    void reproduccionExactaBug_producto89_sucursal18_stock0_sinMovimiento() {
        when(stockSucursalRepository.findBySucursalIdAndTenantId(SUCURSAL_18, TENANT_VET))
                .thenReturn(List.of(stockRow(prod89, SUCURSAL_18, 0))); // fila existe, stock=0
        when(movimientoRepository.findProductoIdsConMovimientoEnSucursal(TENANT_VET, SUCURSAL_18))
                .thenReturn(List.of()); // sin movimiento

        ResponseEntity<List<ProductoDTO>> response = get(SUCURSAL_18);

        assertThat(response.getStatusCode().value()).isEqualTo(200);
        assertThat(response.getBody()).isNotEmpty();
        assertThat(response.getBody().get(0).getId()).isEqualTo(89L);
    }

    // ── Caso H: producto con variantes — presencia basada en stockConVariante ─

    @Test
    @DisplayName("H — producto con variantes: fila en variante_stock_sucursal con stock=0 → aparece")
    void productoConVariantes_filaEnVarianteStock_stock0_debeAparecer() {
        // producto_variante_stock_sucursal devuelve row [productoId, sumStock=0]
        List<Object[]> varianteRows = new java.util.ArrayList<>();
        varianteRows.add(new Object[]{PROD_89, 0});
        when(varianteStockSucursalRepository.sumStockPorProductoYSucursal(SUCURSAL_18, TENANT_VET))
                .thenReturn(varianteRows); // tiene fila (stockConVariante.containsKey(89) = true)
        when(stockSucursalRepository.findBySucursalIdAndTenantId(SUCURSAL_18, TENANT_VET))
                .thenReturn(List.of()); // sin fila en producto_stock_sucursal (es producto con variantes)
        when(movimientoRepository.findProductoIdsConMovimientoEnSucursal(TENANT_VET, SUCURSAL_18))
                .thenReturn(List.of());

        List<ProductoDTO> result = get(SUCURSAL_18).getBody();

        assertThat(result).hasSize(1);
        assertThat(result.get(0).getId()).isEqualTo(PROD_89);
    }

    // ── Sin fila y sin movimiento → no aparece ───────────────────────────────

    @Test
    @DisplayName("Sin fila en producto_stock_sucursal y sin movimiento → no aparece")
    void sinFilaYSinMovimiento_noApareceEnConsulta() {
        when(stockSucursalRepository.findBySucursalIdAndTenantId(SUCURSAL_18, TENANT_VET))
                .thenReturn(List.of()); // sin fila
        when(movimientoRepository.findProductoIdsConMovimientoEnSucursal(TENANT_VET, SUCURSAL_18))
                .thenReturn(List.of()); // sin movimiento

        List<ProductoDTO> result = get(SUCURSAL_18).getBody();

        assertThat(result).isEmpty();
    }

    // ── Sin sucursalId → devuelve todos los productos del tenant (sin filtrar) ─

    @Test
    @DisplayName("Sin sucursalId → devuelve todos los productos del tenant sin filtrar por sucursal")
    void sinSucursalId_devuelveTodosLosDeTenant() {
        List<ProductoDTO> result = get(null).getBody();

        // Sin sucursalId no se aplica el filtro → todos los productos del tenant
        assertThat(result).hasSize(1);
        assertThat(result.get(0).getId()).isEqualTo(PROD_89);
    }
}
