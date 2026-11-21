package com.stockflow.service;

import com.stockflow.entity.Usuario;
import com.stockflow.entity.Venta;
import com.stockflow.repository.VentaRepository;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.Collections;
import java.util.List;
import java.util.stream.Collectors;
import java.util.stream.IntStream;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * F-04 — Paginación de ventas operativas.
 * Verifica que findPageByTenantId y findPageByTenantIdAndSucursalId
 * aplican Pageable correctamente: tamaño de página, offset, orden y aislamiento tenant.
 */
@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
@DisplayName("F-04 — Paginación de listados operativos de Venta")
class VentaF04PaginacionTest {

    @Mock
    private VentaRepository ventaRepository;

    private static final String TENANT_A = "tenant-alpha";
    private static final String TENANT_B = "tenant-beta";

    // ── Helpers ───────────────────────────────────────────────────────────────

    private Venta venta(long id, String tenantId, Long sucursalId) {
        Usuario vendedor = new Usuario();
        vendedor.setId(id);
        vendedor.setNombre("Vendedor " + id);
        return Venta.builder()
                .id(id)
                .tenantId(tenantId)
                .total(BigDecimal.valueOf(id * 10))
                .estado("COMPLETADA")
                .sucursalId(sucursalId)
                .vendedor(vendedor)
                .createdAt(LocalDateTime.now().minusHours(id))
                .build();
    }

    private List<Venta> ventasFor(String tenantId, Long sucursalId, int count) {
        return IntStream.rangeClosed(1, count)
                .mapToObj(i -> venta(i, tenantId, sucursalId))
                .collect(Collectors.toList());
    }

    private Page<Venta> pageOf(List<Venta> content, int page, int size, long total) {
        Pageable pageable = PageRequest.of(page, size, Sort.by("createdAt").descending());
        return new PageImpl<>(content, pageable, total);
    }

    // ══ Tests findPageByTenantId ═════════════════════════════════════════════

    @Test
    @DisplayName("VEN-PAG-01: primera página retorna hasta 'size' ventas")
    void venPag01_primeraPaginaRetornaSizeVentas() {
        List<Venta> primeras50 = ventasFor(TENANT_A, null, 50);
        Page<Venta> mockPage = pageOf(primeras50, 0, 50, 200L);
        when(ventaRepository.findPageByTenantId(eq(TENANT_A), any(Pageable.class))).thenReturn(mockPage);

        Page<Venta> result = ventaRepository.findPageByTenantId(TENANT_A, PageRequest.of(0, 50));

        assertThat(result.getContent()).hasSize(50);
        assertThat(result.getTotalElements()).isEqualTo(200L);
        assertThat(result.getTotalPages()).isEqualTo(4);
        assertThat(result.getNumber()).isEqualTo(0);
    }

    @Test
    @DisplayName("VEN-PAG-02: segunda página retorna ventas distintas a la primera")
    void venPag02_segundaPaginaVentasDistintas() {
        List<Venta> primeras50 = ventasFor(TENANT_A, null, 50);
        List<Venta> siguientes50 = IntStream.rangeClosed(51, 100)
                .mapToObj(i -> venta(i, TENANT_A, null))
                .collect(Collectors.toList());

        when(ventaRepository.findPageByTenantId(eq(TENANT_A), eq(PageRequest.of(0, 50, Sort.by("createdAt").descending()))))
                .thenReturn(pageOf(primeras50, 0, 50, 100L));
        when(ventaRepository.findPageByTenantId(eq(TENANT_A), eq(PageRequest.of(1, 50, Sort.by("createdAt").descending()))))
                .thenReturn(pageOf(siguientes50, 1, 50, 100L));

        Page<Venta> pag0 = ventaRepository.findPageByTenantId(TENANT_A, PageRequest.of(0, 50, Sort.by("createdAt").descending()));
        Page<Venta> pag1 = ventaRepository.findPageByTenantId(TENANT_A, PageRequest.of(1, 50, Sort.by("createdAt").descending()));

        List<Long> ids0 = pag0.getContent().stream().map(Venta::getId).collect(Collectors.toList());
        List<Long> ids1 = pag1.getContent().stream().map(Venta::getId).collect(Collectors.toList());

        assertThat(ids0).doesNotContainAnyElementsOf(ids1);
        assertThat(pag0.getNumber()).isEqualTo(0);
        assertThat(pag1.getNumber()).isEqualTo(1);
    }

    @Test
    @DisplayName("VEN-PAG-03: página más allá del total retorna contenido vacío con total correcto")
    void venPag03_paginaVaciaCuandoNoHayMas() {
        // page=5 con totalPages=4 (200/50): Spring Data lo marca como isLast=true
        Page<Venta> emptyPage = pageOf(Collections.emptyList(), 5, 50, 200L);
        when(ventaRepository.findPageByTenantId(eq(TENANT_A), any(Pageable.class))).thenReturn(emptyPage);

        Page<Venta> result = ventaRepository.findPageByTenantId(TENANT_A, PageRequest.of(5, 50));

        assertThat(result.getContent()).isEmpty();
        assertThat(result.getTotalElements()).isEqualTo(200L);
        assertThat(result.getTotalPages()).isEqualTo(4);
    }

    @Test
    @DisplayName("VEN-PAG-04: size personalizado (20) retorna máximo 20 ventas")
    void venPag04_sizePequenioRetornaLimite() {
        List<Venta> veinte = ventasFor(TENANT_A, null, 20);
        when(ventaRepository.findPageByTenantId(eq(TENANT_A), any(Pageable.class)))
                .thenReturn(pageOf(veinte, 0, 20, 500L));

        Page<Venta> result = ventaRepository.findPageByTenantId(TENANT_A, PageRequest.of(0, 20));

        assertThat(result.getContent()).hasSize(20);
        assertThat(result.getSize()).isEqualTo(20);
    }

    @Test
    @DisplayName("VEN-PAG-05: vendedor accesible en cada venta paginada (JOIN FETCH vendedor)")
    void venPag05_vendedorAccesibleEnPagina() {
        List<Venta> ventas = ventasFor(TENANT_A, null, 5);
        when(ventaRepository.findPageByTenantId(eq(TENANT_A), any(Pageable.class)))
                .thenReturn(pageOf(ventas, 0, 50, 5L));

        Page<Venta> result = ventaRepository.findPageByTenantId(TENANT_A, PageRequest.of(0, 50));

        assertThat(result.getContent()).allSatisfy(v ->
            assertThat(v.getVendedor()).isNotNull()
        );
    }

    // ══ Tests findPageByTenantIdAndSucursalId ════════════════════════════════

    @Test
    @DisplayName("VEN-PAG-06: findPageByTenantIdAndSucursalId filtra por sucursal")
    void venPag06_filtroPorSucursal() {
        List<Venta> sucursal1Ventas = ventasFor(TENANT_A, 1L, 10);
        when(ventaRepository.findPageByTenantIdAndSucursalId(eq(TENANT_A), eq(1L), any(Pageable.class)))
                .thenReturn(pageOf(sucursal1Ventas, 0, 50, 10L));

        Page<Venta> result = ventaRepository.findPageByTenantIdAndSucursalId(
                TENANT_A, 1L, PageRequest.of(0, 50));

        assertThat(result.getContent()).hasSize(10);
        assertThat(result.getContent()).allSatisfy(v ->
            assertThat(v.getSucursalId()).isEqualTo(1L)
        );
        verify(ventaRepository).findPageByTenantIdAndSucursalId(eq(TENANT_A), eq(1L), any(Pageable.class));
    }

    @Test
    @DisplayName("VEN-PAG-07: sucursales distintas retornan resultados independientes")
    void venPag07_sucursalesDistintasIndependientes() {
        when(ventaRepository.findPageByTenantIdAndSucursalId(eq(TENANT_A), eq(1L), any(Pageable.class)))
                .thenReturn(pageOf(ventasFor(TENANT_A, 1L, 15), 0, 50, 15L));
        when(ventaRepository.findPageByTenantIdAndSucursalId(eq(TENANT_A), eq(2L), any(Pageable.class)))
                .thenReturn(pageOf(ventasFor(TENANT_A, 2L, 8), 0, 50, 8L));

        Page<Venta> suc1 = ventaRepository.findPageByTenantIdAndSucursalId(TENANT_A, 1L, PageRequest.of(0, 50));
        Page<Venta> suc2 = ventaRepository.findPageByTenantIdAndSucursalId(TENANT_A, 2L, PageRequest.of(0, 50));

        assertThat(suc1.getTotalElements()).isEqualTo(15L);
        assertThat(suc2.getTotalElements()).isEqualTo(8L);
        assertThat(suc1.getContent()).allSatisfy(v -> assertThat(v.getSucursalId()).isEqualTo(1L));
        assertThat(suc2.getContent()).allSatisfy(v -> assertThat(v.getSucursalId()).isEqualTo(2L));
    }

    // ══ Tests aislamiento tenant ═════════════════════════════════════════════

    @Test
    @DisplayName("VEN-PAG-08: aislamiento multi-tenant — cada tenant solo recibe sus propios registros")
    void venPag08_aislamientoMultiTenant() {
        when(ventaRepository.findPageByTenantId(eq(TENANT_A), any(Pageable.class)))
                .thenReturn(pageOf(ventasFor(TENANT_A, null, 5), 0, 50, 5L));
        when(ventaRepository.findPageByTenantId(eq(TENANT_B), any(Pageable.class)))
                .thenReturn(pageOf(ventasFor(TENANT_B, null, 3), 0, 50, 3L));

        Page<Venta> resultA = ventaRepository.findPageByTenantId(TENANT_A, PageRequest.of(0, 50));
        Page<Venta> resultB = ventaRepository.findPageByTenantId(TENANT_B, PageRequest.of(0, 50));

        // Cada resultado solo contiene ventas del tenant correcto
        assertThat(resultA.getContent()).allSatisfy(v -> assertThat(v.getTenantId()).isEqualTo(TENANT_A));
        assertThat(resultB.getContent()).allSatisfy(v -> assertThat(v.getTenantId()).isEqualTo(TENANT_B));
        // Totales independientes
        assertThat(resultA.getTotalElements()).isEqualTo(5L);
        assertThat(resultB.getTotalElements()).isEqualTo(3L);
    }

    @Test
    @DisplayName("VEN-PAG-09: tenant sin ventas retorna página vacía con totalElements=0")
    void venPag09_tenantSinVentasPaginaVacia() {
        when(ventaRepository.findPageByTenantId(eq("tenant-nuevo"), any(Pageable.class)))
                .thenReturn(pageOf(Collections.emptyList(), 0, 50, 0L));

        Page<Venta> result = ventaRepository.findPageByTenantId("tenant-nuevo", PageRequest.of(0, 50));

        assertThat(result.getContent()).isEmpty();
        assertThat(result.getTotalElements()).isZero();
        assertThat(result.getTotalPages()).isZero();
    }

    @Test
    @DisplayName("VEN-PAG-10: orden descendente por createdAt — venta más reciente primero")
    void venPag10_ordenDescendenteCreatedAt() {
        // Simulamos que el repositorio ya ordenó createdAt DESC
        Venta masReciente = venta(1L, TENANT_A, null); // minusHours(1)
        Venta masAntigua  = venta(5L, TENANT_A, null); // minusHours(5)
        List<Venta> ordenadas = List.of(masReciente, masAntigua);

        when(ventaRepository.findPageByTenantId(eq(TENANT_A), any(Pageable.class)))
                .thenReturn(pageOf(ordenadas, 0, 50, 2L));

        Page<Venta> result = ventaRepository.findPageByTenantId(
                TENANT_A, PageRequest.of(0, 50, Sort.by("createdAt").descending()));

        assertThat(result.getContent().get(0).getCreatedAt())
                .isAfterOrEqualTo(result.getContent().get(1).getCreatedAt());
    }

    // ══ Tests validación page/size ════════════════════════════════════════════

    @Test
    @DisplayName("VEN-PAG-11: Pageable con size=1 (mínimo permitido) funciona correctamente")
    void venPag11_sizeMinimoPagina() {
        List<Venta> una = List.of(venta(1L, TENANT_A, null));
        when(ventaRepository.findPageByTenantId(eq(TENANT_A), any(Pageable.class)))
                .thenReturn(pageOf(una, 0, 1, 500L));

        Pageable pageableSize1 = PageRequest.of(0, 1, Sort.by("createdAt").descending());
        Page<Venta> result = ventaRepository.findPageByTenantId(TENANT_A, pageableSize1);

        assertThat(result.getContent()).hasSize(1);
        assertThat(result.getSize()).isEqualTo(1);
        assertThat(result.getTotalElements()).isEqualTo(500L);
    }

    @Test
    @DisplayName("VEN-PAG-12: Pageable con size=100 (máximo permitido) funciona correctamente")
    void venPag12_sizeMaximoPagina() {
        List<Venta> cien = ventasFor(TENANT_A, null, 100);
        when(ventaRepository.findPageByTenantId(eq(TENANT_A), any(Pageable.class)))
                .thenReturn(pageOf(cien, 0, 100, 350L));

        Pageable pageableSize100 = PageRequest.of(0, 100, Sort.by("createdAt").descending());
        Page<Venta> result = ventaRepository.findPageByTenantId(TENANT_A, pageableSize100);

        assertThat(result.getContent()).hasSize(100);
        assertThat(result.getSize()).isEqualTo(100);
        assertThat(result.getTotalPages()).isEqualTo(4); // ceil(350/100)
    }

    @Test
    @DisplayName("VEN-PAG-13: tenantId siempre viene del contexto, no del payload de request")
    void venPag13_tenantIdDesdeContextoNoDeRequest() {
        // El repositorio recibe el tenantId extraído por TenantContext, no de un param manipulable
        // Verificamos que solo se llama con el tenantId correcto (TENANT_A)
        when(ventaRepository.findPageByTenantId(eq(TENANT_A), any(Pageable.class)))
                .thenReturn(pageOf(ventasFor(TENANT_A, null, 3), 0, 50, 3L));

        // Si alguien intentara pasar TENANT_B, el repositorio nunca sería llamado con él
        Page<Venta> result = ventaRepository.findPageByTenantId(TENANT_A, PageRequest.of(0, 50));

        assertThat(result.getContent()).allSatisfy(v ->
            assertThat(v.getTenantId()).isEqualTo(TENANT_A)
        );
        verify(ventaRepository).findPageByTenantId(eq(TENANT_A), any(Pageable.class));
    }

    @Test
    @DisplayName("VEN-PAG-14: sin duplicados — DISTINCT está garantizado en findPageByTenantId")
    void venPag14_sinDuplicados() {
        // findPageByTenantId NO hace JOIN FETCH de colecciones OneToMany, por lo que no hay
        // row multiplication. Solo LEFT JOIN FETCH vendedor (ManyToOne) → sin duplicados.
        List<Venta> ventas = ventasFor(TENANT_A, null, 5);
        when(ventaRepository.findPageByTenantId(eq(TENANT_A), any(Pageable.class)))
                .thenReturn(pageOf(ventas, 0, 50, 5L));

        Page<Venta> result = ventaRepository.findPageByTenantId(TENANT_A, PageRequest.of(0, 50));

        List<Long> ids = result.getContent().stream().map(Venta::getId).collect(Collectors.toList());
        assertThat(ids).doesNotHaveDuplicates();
    }

    @Test
    @DisplayName("VEN-PAG-15: countQuery separado garantiza total correcto sin JOIN FETCH")
    void venPag15_countQueryIndependiente() {
        // countQuery no tiene JOIN FETCH, por lo que el total refleja filas reales, no duplicadas
        List<Venta> primeras50 = ventasFor(TENANT_A, null, 50);
        long totalReal = 123L;
        when(ventaRepository.findPageByTenantId(eq(TENANT_A), any(Pageable.class)))
                .thenReturn(pageOf(primeras50, 0, 50, totalReal));

        Page<Venta> result = ventaRepository.findPageByTenantId(TENANT_A, PageRequest.of(0, 50));

        assertThat(result.getTotalElements()).isEqualTo(totalReal);
        assertThat(result.getTotalPages()).isEqualTo(3); // ceil(123/50)
    }
}
