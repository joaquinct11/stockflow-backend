package com.stockflow.service;

import com.stockflow.dto.reportes.LoteRiesgoDetalleDTO;
import com.stockflow.dto.reportes.VencimientosRiesgoDTO;
import com.stockflow.repository.*;
import com.stockflow.service.impl.ReportesServiceImpl;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.*;

/**
 * F-05 — Optimización de vencimientos en riesgo.
 * Verifica que getVencimientosRiesgo() use StockLote (fuente de verdad del stock actual)
 * y no MovimientoInventario, que filtre lotes agotados, y que clasifique correctamente
 * por bucket de urgencia (vencido / 7d / 30d / 90d).
 */
@ExtendWith(MockitoExtension.class)
class ReportesF05VencimientosTest {

    @Mock private ProductoRepository              productoRepository;
    @Mock private ProductoStockSucursalRepository stockSucursalRepository;
    @Mock private MovimientoInventarioRepository  movimientoRepository;
    @Mock private RecepcionRepository             recepcionRepository;
    @Mock private VentaRepository                 ventaRepository;
    @Mock private GastoRepository                 gastoRepository;
    @Mock private ComprobanteRepository           comprobanteRepository;
    @Mock private StockLoteRepository             stockLoteRepository;

    @InjectMocks
    private ReportesServiceImpl reportesService;

    // ── Helpers ───────────────────────────────────────────────────────────────

    /** Crea una fila Object[] igual a lo que devolvería el query nativo de Postgres. */
    private Object[] row(long productoId, String nombre, String lote,
                         LocalDate fechaVenc, int stockActual, double costo) {
        return new Object[]{
            productoId,
            nombre,
            lote,
            java.sql.Date.valueOf(fechaVenc),
            stockActual,
            BigDecimal.valueOf(costo)
        };
    }

    /**
     * Envuelve filas en List<Object[]> de forma tipada.
     * List.of(singleObjectArray) tiene ambigüedad de varargs con javac; este helper la evita.
     */
    @SafeVarargs
    private List<Object[]> rows(Object[]... items) {
        return new ArrayList<>(Arrays.asList(items));
    }

    // ── Tests ─────────────────────────────────────────────────────────────────

    /**
     * VEN-VENC-01: Tenant sin ningún lote en riesgo → DTO con todos los campos en cero
     * y lista de urgentes vacía.
     */
    @Test
    void venVenc01_sinLotes_retornaDTOConCerosYListaVacia() {
        when(stockLoteRepository.findLotesEnRiesgoConProducto("tenant1")).thenReturn(List.of());

        VencimientosRiesgoDTO result = reportesService.getVencimientosRiesgo("tenant1");

        assertThat(result.getLotesVencidos()).isEqualTo(0L);
        assertThat(result.getLotesRiesgo7d()).isEqualTo(0L);
        assertThat(result.getLotesRiesgo30d()).isEqualTo(0L);
        assertThat(result.getLotesRiesgo90d()).isEqualTo(0L);
        assertThat(result.getCapitalVencido()).isEqualByComparingTo(BigDecimal.ZERO);
        assertThat(result.getTotalCapitalCritico()).isEqualByComparingTo(BigDecimal.ZERO);
        assertThat(result.getLotesUrgentes()).isEmpty();
    }

    /**
     * VEN-VENC-02: Lote que vence en 5 días → clasificado en riesgo7d, no en vencido ni 30d.
     */
    @Test
    void venVenc02_loteVenceEn5dias_clasificadoEnRiesgo7d() {
        LocalDate hoy = LocalDate.now();
        Object[] r = row(1L, "Ibuprofeno", "IBU-001", hoy.plusDays(5), 20, 15.00);
        when(stockLoteRepository.findLotesEnRiesgoConProducto("tenant1")).thenReturn(rows(r));

        VencimientosRiesgoDTO result = reportesService.getVencimientosRiesgo("tenant1");

        assertThat(result.getLotesRiesgo7d()).isEqualTo(1L);
        assertThat(result.getCapitalRiesgo7d()).isEqualByComparingTo(new BigDecimal("300.00")); // 20 × 15
        assertThat(result.getLotesVencidos()).isEqualTo(0L);
        assertThat(result.getLotesRiesgo30d()).isEqualTo(0L);
    }

    /**
     * VEN-VENC-03: Lote que venció hace 3 días → clasificado en capitalVencido.
     */
    @Test
    void venVenc03_loteVencidoHace3dias_clasificadoEnVencido() {
        LocalDate hoy = LocalDate.now();
        Object[] r = row(2L, "Paracetamol", "PAR-010", hoy.minusDays(3), 50, 8.00);
        when(stockLoteRepository.findLotesEnRiesgoConProducto("tenant1")).thenReturn(rows(r));

        VencimientosRiesgoDTO result = reportesService.getVencimientosRiesgo("tenant1");

        assertThat(result.getLotesVencidos()).isEqualTo(1L);
        assertThat(result.getCapitalVencido()).isEqualByComparingTo(new BigDecimal("400.00")); // 50 × 8
        assertThat(result.getLotesRiesgo7d()).isEqualTo(0L);
    }

    /**
     * VEN-VENC-04: El servicio usa stockLoteRepository.findLotesEnRiesgoConProducto y
     * NO llama al método antiguo movimientoRepository.findEntradasConVencimientoPorTenant.
     * Esta es la prueba clave de la migración a StockLote como fuente de verdad.
     */
    @Test
    void venVenc04_usaStockLoteNoMovimientoInventario() {
        when(stockLoteRepository.findLotesEnRiesgoConProducto(anyString())).thenReturn(List.of());

        reportesService.getVencimientosRiesgo("tenant1");

        verify(stockLoteRepository, times(1)).findLotesEnRiesgoConProducto("tenant1");
        verify(movimientoRepository, never()).findEntradasConVencimientoPorTenant(anyString());
    }

    /**
     * VEN-VENC-05: Lote parcialmente consumido → el campo cantidad del detalle refleja
     * stockActual (5), no el stock original de entrada (100).
     */
    @Test
    void venVenc05_loteParcialmenteConsumido_usaStockActual() {
        LocalDate hoy = LocalDate.now();
        // stockActual = 5 (de los 100 originales, ya se vendieron 95)
        Object[] r = row(3L, "Amoxicilina", "AMX-005", hoy.plusDays(10), 5, 20.00);
        when(stockLoteRepository.findLotesEnRiesgoConProducto("tenant1")).thenReturn(rows(r));

        VencimientosRiesgoDTO result = reportesService.getVencimientosRiesgo("tenant1");

        LoteRiesgoDetalleDTO detalle = result.getLotesUrgentes().get(0);
        assertThat(detalle.getCantidad()).isEqualTo(5);
        assertThat(detalle.getValorRiesgo()).isEqualByComparingTo(new BigDecimal("100.00")); // 5 × 20
        assertThat(result.getCapitalRiesgo30d()).isEqualByComparingTo(new BigDecimal("100.00"));
    }

    /**
     * VEN-VENC-06: Dos lotes del mismo producto → ambos contados y sumados independientemente.
     */
    @Test
    void venVenc06_dosLotesMismoProducto_contadosIndependientemente() {
        LocalDate hoy = LocalDate.now();
        Object[] r1 = row(4L, "Vitamina C", "VIT-001", hoy.plusDays(2),  30, 10.00);
        Object[] r2 = row(4L, "Vitamina C", "VIT-002", hoy.plusDays(4),  20, 10.00);
        when(stockLoteRepository.findLotesEnRiesgoConProducto("tenant1"))
                .thenReturn(List.of(r1, r2));

        VencimientosRiesgoDTO result = reportesService.getVencimientosRiesgo("tenant1");

        assertThat(result.getLotesRiesgo7d()).isEqualTo(2L);
        assertThat(result.getCapitalRiesgo7d()).isEqualByComparingTo(new BigDecimal("500.00")); // 300 + 200
        assertThat(result.getLotesUrgentes()).hasSize(2);
    }

    /**
     * VEN-VENC-07: Aislamiento de tenant → solo se usa el tenantId del parámetro,
     * el repositorio recibe exactamente ese valor y no otro.
     */
    @Test
    void venVenc07_aislamientoTenant_soloUsaTenantSolicitado() {
        when(stockLoteRepository.findLotesEnRiesgoConProducto("tenantA")).thenReturn(List.of());

        reportesService.getVencimientosRiesgo("tenantA");

        verify(stockLoteRepository, times(1)).findLotesEnRiesgoConProducto("tenantA");
        verify(stockLoteRepository, never()).findLotesEnRiesgoConProducto("tenantB");
    }

    /**
     * VEN-VENC-08: Frontera exacta de cada bucket.
     * dias=0 y dias=7 → riesgo7d; dias=8 y dias=30 → riesgo30d; dias=31 y dias=90 → riesgo90d.
     */
    @Test
    void venVenc08_fronterasExactasDeBuckets() {
        LocalDate hoy = LocalDate.now();
        List<Object[]> rows = List.of(
            row(10L, "P-0",  "L0",  hoy,                10, 1.0),  // diasRestantes = 0 → 7d
            row(11L, "P-7",  "L7",  hoy.plusDays(7),    10, 1.0),  // diasRestantes = 7 → 7d
            row(12L, "P-8",  "L8",  hoy.plusDays(8),    10, 1.0),  // diasRestantes = 8 → 30d
            row(13L, "P-30", "L30", hoy.plusDays(30),   10, 1.0),  // diasRestantes = 30 → 30d
            row(14L, "P-31", "L31", hoy.plusDays(31),   10, 1.0),  // diasRestantes = 31 → 90d
            row(15L, "P-90", "L90", hoy.plusDays(90),   10, 1.0)   // diasRestantes = 90 → 90d
        );
        when(stockLoteRepository.findLotesEnRiesgoConProducto("tenant1")).thenReturn(rows);

        VencimientosRiesgoDTO result = reportesService.getVencimientosRiesgo("tenant1");

        assertThat(result.getLotesVencidos()).isEqualTo(0L);
        assertThat(result.getLotesRiesgo7d()).isEqualTo(2L);
        assertThat(result.getLotesRiesgo30d()).isEqualTo(2L);
        assertThat(result.getLotesRiesgo90d()).isEqualTo(2L);
    }

    /**
     * VEN-VENC-09: totalCapitalCritico = capitalVencido + capitalRiesgo7d + capitalRiesgo30d.
     * No incluye capitalRiesgo90d (el riesgo a 90d es monitoreo, no crítico inmediato).
     */
    @Test
    void venVenc09_totalCapitalCritico_excluye90d() {
        LocalDate hoy = LocalDate.now();
        List<Object[]> rows = List.of(
            row(20L, "P-venc", "LV",  hoy.minusDays(1), 10, 100.0),  // vencido   → 1000
            row(21L, "P-7d",   "L7",  hoy.plusDays(5),  10, 50.0),   // riesgo7d  → 500
            row(22L, "P-30d",  "L30", hoy.plusDays(20), 10, 20.0),   // riesgo30d → 200
            row(23L, "P-90d",  "L90", hoy.plusDays(60), 10, 10.0)    // riesgo90d → 100 (no incluido)
        );
        when(stockLoteRepository.findLotesEnRiesgoConProducto("tenant1")).thenReturn(rows);

        VencimientosRiesgoDTO result = reportesService.getVencimientosRiesgo("tenant1");

        // totalCritico = 1000 + 500 + 200 = 1700, sin los 100 de riesgo90d
        assertThat(result.getTotalCapitalCritico()).isEqualByComparingTo(new BigDecimal("1700.00"));
        assertThat(result.getCapitalRiesgo90d()).isEqualByComparingTo(new BigDecimal("100.00"));
    }

    /**
     * VEN-VENC-10: Con 20 lotes, lotesUrgentes devuelve solo 15, ordenados por diasRestantes ASC
     * (vencidos primero con días negativos, luego próximos a vencer).
     */
    @Test
    void venVenc10_top15Urgentes_ordenadosAscendente() {
        LocalDate hoy = LocalDate.now();
        List<Object[]> rows = new ArrayList<>();
        // 5 vencidos: días = -5, -4, -3, -2, -1
        for (int i = 5; i >= 1; i--) {
            rows.add(row(100L + i, "Venc-" + i, "LV" + i, hoy.minusDays(i), 10, 1.0));
        }
        // 5 riesgo7d: días = 1, 2, 3, 4, 5
        for (int i = 1; i <= 5; i++) {
            rows.add(row(200L + i, "7d-" + i, "L7-" + i, hoy.plusDays(i), 10, 1.0));
        }
        // 5 riesgo30d: días = 8, 9, 10, 11, 12
        for (int i = 8; i <= 12; i++) {
            rows.add(row(300L + i, "30d-" + i, "L30-" + i, hoy.plusDays(i), 10, 1.0));
        }
        // 5 riesgo90d: días = 50, 51, 52, 53, 54 — deben quedar fuera del top 15
        for (int i = 50; i <= 54; i++) {
            rows.add(row(400L + i, "90d-" + i, "L90-" + i, hoy.plusDays(i), 10, 1.0));
        }
        when(stockLoteRepository.findLotesEnRiesgoConProducto("tenant1")).thenReturn(rows);

        VencimientosRiesgoDTO result = reportesService.getVencimientosRiesgo("tenant1");

        List<LoteRiesgoDetalleDTO> urgentes = result.getLotesUrgentes();
        assertThat(urgentes).hasSize(15);

        // Primer elemento: el más vencido (diasRestantes más negativo)
        assertThat(urgentes.get(0).getDiasRestantes()).isLessThan(0);
        // Último elemento del top 15: diasRestantes = 12 (no llega a los de 90d)
        assertThat(urgentes.get(14).getDiasRestantes()).isLessThanOrEqualTo(12);

        // Verificar orden ascendente
        for (int i = 0; i < urgentes.size() - 1; i++) {
            assertThat(urgentes.get(i).getDiasRestantes())
                    .isLessThanOrEqualTo(urgentes.get(i + 1).getDiasRestantes());
        }
    }
}
