package com.stockflow.service;

import com.stockflow.entity.Cliente;
import com.stockflow.repository.ClienteRepository;
import com.stockflow.repository.EquipoClienteRepository;
import com.stockflow.repository.MascotaRepository;
import com.stockflow.service.impl.ClienteServiceImpl;
import com.stockflow.util.TenantContext;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;

import java.util.Collections;
import java.util.List;
import java.util.stream.IntStream;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * F-07 — Verifica que ClienteServiceImpl aplica límites de paginación en todas
 * las consultas de listado y búsqueda para evitar egress ilimitado.
 */
@ExtendWith(MockitoExtension.class)
@DisplayName("F-07 — ClienteServiceImpl: límites de paginación")
class ClienteF07PaginacionTest {

    private static final String TENANT = "tenant-abc";

    @Mock private ClienteRepository       clienteRepository;
    @Mock private MascotaRepository       mascotaRepository;
    @Mock private EquipoClienteRepository equipoClienteRepository;

    @InjectMocks private ClienteServiceImpl service;

    @BeforeEach
    void setTenant() {
        TenantContext.setCurrentTenant(TENANT);
    }

    @AfterEach
    void clearTenant() {
        TenantContext.clear();
    }

    // ── 1. obtenerClientesPorTenant ──────────────────────────────────────────

    @Nested
    @DisplayName("1 — obtenerClientesPorTenant: límite MAX_LISTA_CLIENTES")
    class ObtenerPorTenant {

        @Test
        @DisplayName("pasa Pageable con size=MAX_LISTA_CLIENTES al repository")
        void pasaPageableConLimite() {
            when(clienteRepository.findByTenantId(anyString(), any(Pageable.class)))
                    .thenReturn(List.of());

            service.obtenerClientesPorTenant(TENANT);

            ArgumentCaptor<Pageable> cap = ArgumentCaptor.forClass(Pageable.class);
            verify(clienteRepository).findByTenantId(eq(TENANT), cap.capture());
            assertThat(cap.getValue().getPageSize())
                    .isEqualTo(ClienteServiceImpl.MAX_LISTA_CLIENTES);
            assertThat(cap.getValue().getPageNumber()).isZero();
        }

        @Test
        @DisplayName("devuelve la lista que retorna el repository (sin modificar)")
        void devuelveLista() {
            List<Cliente> mockLista = List.of(cliente(1L), cliente(2L));
            when(clienteRepository.findByTenantId(anyString(), any(Pageable.class)))
                    .thenReturn(mockLista);

            List<Cliente> result = service.obtenerClientesPorTenant(TENANT);

            assertThat(result).hasSize(2);
        }

        @Test
        @DisplayName("límite es 1000 — no se pide más de 1000 clientes")
        void limiteEs1000() {
            assertThat(ClienteServiceImpl.MAX_LISTA_CLIENTES).isEqualTo(1000);
        }
    }

    // ── 2. obtenerClientesActivosPorTenant ───────────────────────────────────

    @Nested
    @DisplayName("2 — obtenerClientesActivosPorTenant: límite MAX_ACTIVOS_CLIENTES")
    class ObtenerActivosPorTenant {

        @Test
        @DisplayName("pasa Pageable con size=MAX_ACTIVOS_CLIENTES al repository")
        void pasaPageableConLimite() {
            when(clienteRepository.findByTenantIdAndActivoTrue(anyString(), any(Pageable.class)))
                    .thenReturn(List.of());

            service.obtenerClientesActivosPorTenant(TENANT);

            ArgumentCaptor<Pageable> cap = ArgumentCaptor.forClass(Pageable.class);
            verify(clienteRepository).findByTenantIdAndActivoTrue(eq(TENANT), cap.capture());
            assertThat(cap.getValue().getPageSize())
                    .isEqualTo(ClienteServiceImpl.MAX_ACTIVOS_CLIENTES);
            assertThat(cap.getValue().getPageNumber()).isZero();
        }

        @Test
        @DisplayName("límite es 500 — adecuado para selectores del frontend")
        void limiteEs500() {
            assertThat(ClienteServiceImpl.MAX_ACTIVOS_CLIENTES).isEqualTo(500);
        }
    }

    // ── 3. buscarClientesPorNombre ───────────────────────────────────────────

    @Nested
    @DisplayName("3 — buscarClientesPorNombre: límite MAX_BUSQUEDA_NOMBRE")
    class BuscarPorNombre {

        @Test
        @DisplayName("pasa Pageable con size=MAX_BUSQUEDA_NOMBRE al repository")
        void pasaPageableConLimite() {
            when(clienteRepository.findByNombreContainingIgnoreCaseAndTenantId(
                    anyString(), anyString(), any(Pageable.class)))
                    .thenReturn(List.of());

            service.buscarClientesPorNombre("Juan", TENANT);

            ArgumentCaptor<Pageable> cap = ArgumentCaptor.forClass(Pageable.class);
            verify(clienteRepository).findByNombreContainingIgnoreCaseAndTenantId(
                    eq("Juan"), eq(TENANT), cap.capture());
            assertThat(cap.getValue().getPageSize())
                    .isEqualTo(ClienteServiceImpl.MAX_BUSQUEDA_NOMBRE);
        }

        @Test
        @DisplayName("límite es 20 — adecuado para autocompletado")
        void limiteEs20() {
            assertThat(ClienteServiceImpl.MAX_BUSQUEDA_NOMBRE).isEqualTo(20);
        }

        @Test
        @DisplayName("búsqueda case-insensitive pasa el término sin modificar al repository")
        void pasaTerminoSinModificar() {
            when(clienteRepository.findByNombreContainingIgnoreCaseAndTenantId(
                    anyString(), anyString(), any(Pageable.class)))
                    .thenReturn(List.of());

            service.buscarClientesPorNombre("GARCIA", TENANT);

            verify(clienteRepository).findByNombreContainingIgnoreCaseAndTenantId(
                    eq("GARCIA"), eq(TENANT), any(Pageable.class));
        }
    }

    // ── 4. buscarClientesPorDocumento ────────────────────────────────────────

    @Nested
    @DisplayName("4 — buscarClientesPorDocumento: límite MAX_BUSQUEDA_DOC")
    class BuscarPorDocumento {

        @Test
        @DisplayName("pasa Pageable con size=MAX_BUSQUEDA_DOC al repository")
        void pasaPageableConLimite() {
            when(clienteRepository.findByNumeroDocumentoAndTenantId(
                    anyString(), anyString(), any(Pageable.class)))
                    .thenReturn(List.of());

            service.buscarClientesPorDocumento("12345678", TENANT);

            ArgumentCaptor<Pageable> cap = ArgumentCaptor.forClass(Pageable.class);
            verify(clienteRepository).findByNumeroDocumentoAndTenantId(
                    eq("12345678"), eq(TENANT), cap.capture());
            assertThat(cap.getValue().getPageSize())
                    .isEqualTo(ClienteServiceImpl.MAX_BUSQUEDA_DOC);
        }

        @Test
        @DisplayName("límite es 10 — un número de documento no debería tener más de 1 resultado")
        void limiteEs10() {
            assertThat(ClienteServiceImpl.MAX_BUSQUEDA_DOC).isEqualTo(10);
        }
    }

    // ── 5. aislamiento multi-tenant ──────────────────────────────────────────

    @Nested
    @DisplayName("5 — aislamiento multi-tenant: el tenantId siempre viene del contexto")
    class MultiTenant {

        @Test
        @DisplayName("obtenerClientesPorTenant usa exactamente el tenantId pasado")
        void usaTenantIdPasado() {
            when(clienteRepository.findByTenantId(anyString(), any(Pageable.class)))
                    .thenReturn(Collections.emptyList());

            service.obtenerClientesPorTenant("otro-tenant");

            verify(clienteRepository).findByTenantId(eq("otro-tenant"), any(Pageable.class));
        }

        @Test
        @DisplayName("obtenerClientesActivosPorTenant usa exactamente el tenantId pasado")
        void activosUsaTenantIdPasado() {
            when(clienteRepository.findByTenantIdAndActivoTrue(anyString(), any(Pageable.class)))
                    .thenReturn(Collections.emptyList());

            service.obtenerClientesActivosPorTenant("otro-tenant");

            verify(clienteRepository).findByTenantIdAndActivoTrue(
                    eq("otro-tenant"), any(Pageable.class));
        }

        @Test
        @DisplayName("buscarClientesPorNombre usa el tenantId pasado, no el del contexto")
        void busquedaUsaTenantIdPasado() {
            when(clienteRepository.findByNombreContainingIgnoreCaseAndTenantId(
                    anyString(), anyString(), any(Pageable.class)))
                    .thenReturn(Collections.emptyList());

            service.buscarClientesPorNombre("Perez", "tenant-x");

            verify(clienteRepository).findByNombreContainingIgnoreCaseAndTenantId(
                    eq("Perez"), eq("tenant-x"), any(Pageable.class));
        }
    }

    // ── 6. coherencia entre constantes ──────────────────────────────────────

    @Nested
    @DisplayName("6 — Coherencia entre constantes de límite")
    class CoherenciaConstantes {

        @Test
        @DisplayName("MAX_LISTA >= MAX_ACTIVOS > MAX_BUSQUEDA_NOMBRE >= MAX_BUSQUEDA_DOC")
        void constantesEnOrdenDecreciente() {
            assertThat(ClienteServiceImpl.MAX_LISTA_CLIENTES)
                    .isGreaterThanOrEqualTo(ClienteServiceImpl.MAX_ACTIVOS_CLIENTES);
            assertThat(ClienteServiceImpl.MAX_ACTIVOS_CLIENTES)
                    .isGreaterThan(ClienteServiceImpl.MAX_BUSQUEDA_NOMBRE);
            assertThat(ClienteServiceImpl.MAX_BUSQUEDA_NOMBRE)
                    .isGreaterThanOrEqualTo(ClienteServiceImpl.MAX_BUSQUEDA_DOC);
        }
    }

    // ── 7. comportamiento en el límite (riesgo de clientes omitidos) ─────────

    @Nested
    @DisplayName("7 — Comportamiento en límite: clientes omitidos cuando se supera el tope")
    class LimiteBoundary {

        @Test
        @DisplayName("listado recibe exactamente MAX_LISTA_CLIENTES clientes → los devuelve todos sin corte adicional")
        void listadoEnLimiteExacto() {
            List<Cliente> mockLista = clientesEnLimite(ClienteServiceImpl.MAX_LISTA_CLIENTES);
            when(clienteRepository.findByTenantId(anyString(), any(Pageable.class)))
                    .thenReturn(mockLista);

            List<Cliente> result = service.obtenerClientesPorTenant(TENANT);

            assertThat(result).hasSize(ClienteServiceImpl.MAX_LISTA_CLIENTES);
        }

        @Test
        @DisplayName("listado con tenant de MAX_LISTA+1 clientes: el Pageable limita al tamaño correcto")
        void listadoDocumentaRiesgoOmision() {
            // El repository recibe PageRequest(0, 1000) y devuelve como máximo 1000 filas.
            // Un tenant con 1001+ clientes solo verá los primeros 1000.
            // Este test documenta que el comportamiento es intencional (límite de Egress).
            ArgumentCaptor<Pageable> cap = ArgumentCaptor.forClass(Pageable.class);
            when(clienteRepository.findByTenantId(anyString(), any(Pageable.class)))
                    .thenReturn(List.of());

            service.obtenerClientesPorTenant(TENANT);

            verify(clienteRepository).findByTenantId(anyString(), cap.capture());
            assertThat(cap.getValue().getPageSize())
                    .as("El Pageable tiene el límite configurado; el repository corta ahí")
                    .isEqualTo(ClienteServiceImpl.MAX_LISTA_CLIENTES);
        }

        @Test
        @DisplayName("selector de activos: Pageable limita a MAX_ACTIVOS_CLIENTES — riesgo con >500 activos")
        void selectorDocumentaRiesgoActivos() {
            // Consumidores como CitaFormModal, EquiposPage, MascotasPage, OrdenesTrabajoPage
            // y VentasList cargan getActivos() al montar. Con más de 500 activos los restantes
            // no aparecen en los dropdowns. Límite aceptable para la mayoría de tenants SMB.
            ArgumentCaptor<Pageable> cap = ArgumentCaptor.forClass(Pageable.class);
            when(clienteRepository.findByTenantIdAndActivoTrue(anyString(), any(Pageable.class)))
                    .thenReturn(List.of());

            service.obtenerClientesActivosPorTenant(TENANT);

            verify(clienteRepository).findByTenantIdAndActivoTrue(anyString(), cap.capture());
            assertThat(cap.getValue().getPageSize())
                    .isEqualTo(ClienteServiceImpl.MAX_ACTIVOS_CLIENTES);
        }
    }

    // ── helpers ──────────────────────────────────────────────────────────────

    private List<Cliente> clientesEnLimite(int cantidad) {
        return java.util.stream.IntStream.rangeClosed(1, cantidad)
                .mapToObj(i -> cliente((long) i))
                .toList();
    }

    private Cliente cliente(Long id) {
        Cliente c = new Cliente();
        c.setId(id);
        c.setNombre("Cliente " + id);
        c.setTenantId(TENANT);
        c.setActivo(true);
        return c;
    }
}
