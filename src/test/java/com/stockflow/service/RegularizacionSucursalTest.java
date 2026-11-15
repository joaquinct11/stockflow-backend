package com.stockflow.service;

import com.stockflow.dto.SucursalDTO;
import com.stockflow.entity.Sucursal;
import com.stockflow.exception.BadRequestException;
import com.stockflow.repository.*;
import com.stockflow.service.impl.SucursalServiceImpl;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;

import java.util.Optional;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
@DisplayName("SucursalServiceImpl — regularizarDatosSucursalPrincipal")
class RegularizacionSucursalTest {

    private static final String TENANT_A = "tenant-hist-001";
    private static final String TENANT_B = "tenant-hist-002";
    private static final Long   SUCURSAL_ID = 42L;

    @Mock private SucursalRepository                      sucursalRepository;
    @Mock private ProductoStockSucursalRepository         stockSucursalRepository;
    @Mock private ProductoVarianteStockSucursalRepository varianteStockSucursalRepository;
    @Mock private PlanLimitService                        planLimitService;
    @Mock private VentaRepository                         ventaRepository;
    @Mock private MovimientoInventarioRepository          movimientoRepository;
    @Mock private GastoRepository                         gastoRepository;
    @Mock private CajaRepository                          cajaRepository;
    @Mock private DevolucionRepository                    devolucionRepository;
    @Mock private NotaCreditoRepository                   notaCreditoRepository;
    @Mock private OrdenCompraRepository                   ordenCompraRepository;
    @Mock private RecepcionRepository                     recepcionRepository;
    @Mock private ComprobanteRepository                   comprobanteRepository;
    @Mock private UsuarioTenantRepository                 usuarioTenantRepository;
    @Mock private CertificadoRepository                   certificadoRepository;
    @Mock private StockLoteRepository                      stockLoteRepository;

    @InjectMocks private SucursalServiceImpl sucursalService;

    private Sucursal sucursalPrincipal;

    @BeforeEach
    void setUp() {
        sucursalPrincipal = Sucursal.builder()
                .id(SUCURSAL_ID)
                .tenantId(TENANT_A)
                .nombre("Sucursal Principal")
                .esPrincipal(true)
                .activo(true)
                .bloqueadaPorPlan(false)
                .build();
    }

    // ── N: tenant sin sucursal principal → excepción ─────────────────────────

    @Test
    @DisplayName("N — tenant sin sucursal principal → BadRequestException")
    void sinSucursalPrincipal_lanzaBadRequest() {
        when(sucursalRepository.findByTenantIdAndEsPrincipalTrue(TENANT_A))
                .thenReturn(Optional.empty());

        assertThatThrownBy(() -> sucursalService.regularizarDatosSucursalPrincipal(TENANT_A))
                .isInstanceOf(BadRequestException.class)
                .hasMessageContaining("No existe sucursal principal");

        verifyNoInteractions(ventaRepository, movimientoRepository, stockSucursalRepository);
    }

    // ── P: tenant con sucursal creada por SQL → backfill completo ────────────

    @Test
    @DisplayName("P — sucursal principal existe (creada por SQL) → ejecuta todos los backfills")
    void sucursalExiste_ejecutaTodosLosBackfills() {
        when(sucursalRepository.findByTenantIdAndEsPrincipalTrue(TENANT_A))
                .thenReturn(Optional.of(sucursalPrincipal));

        SucursalDTO resultado = sucursalService.regularizarDatosSucursalPrincipal(TENANT_A);

        assertThat(resultado).isNotNull();
        assertThat(resultado.getId()).isEqualTo(SUCURSAL_ID);
        assertThat(resultado.getEsPrincipal()).isTrue();

        // Los 10 backfills de registros históricos deben haberse ejecutado
        verify(ventaRepository).asignarSucursalDondeEsNulo(SUCURSAL_ID, TENANT_A);
        verify(movimientoRepository).asignarSucursalDondeEsNulo(SUCURSAL_ID, TENANT_A);
        verify(gastoRepository).asignarSucursalDondeEsNulo(SUCURSAL_ID, TENANT_A);
        verify(cajaRepository).asignarSucursalDondeEsNulo(SUCURSAL_ID, TENANT_A);
        verify(devolucionRepository).asignarSucursalDondeEsNulo(SUCURSAL_ID, TENANT_A);
        verify(notaCreditoRepository).asignarSucursalDondeEsNulo(SUCURSAL_ID, TENANT_A);
        verify(ordenCompraRepository).asignarSucursalDondeEsNulo(SUCURSAL_ID, TENANT_A);
        verify(recepcionRepository).asignarSucursalDondeEsNulo(SUCURSAL_ID, TENANT_A);
        verify(comprobanteRepository).asignarSucursalDondeEsNulo(SUCURSAL_ID, TENANT_A);
        verify(certificadoRepository).asignarSucursalDondeEsNulo(SUCURSAL_ID, TENANT_A);

        // Usuarios (excluye ADMINs internamente)
        verify(usuarioTenantRepository).asignarSucursalDondeEsNuloByTenant(SUCURSAL_ID, TENANT_A);

        // Lotes históricos sin sucursal asignada
        verify(stockLoteRepository).asignarSucursalDondeEsNulo(SUCURSAL_ID, TENANT_A);

        // Stock de productos y variantes
        verify(stockSucursalRepository).inicializarStockParaSucursal(SUCURSAL_ID, TENANT_A);
        verify(varianteStockSucursalRepository).inicializarStockPrincipalParaSucursal(SUCURSAL_ID, TENANT_A);
    }

    // ── P: NO crea nueva sucursal (a diferencia de inicializarPrincipal) ──────

    @Test
    @DisplayName("P — no llama a sucursalRepository.save() — no crea sucursal nueva")
    void noCreaSucursalNueva() {
        when(sucursalRepository.findByTenantIdAndEsPrincipalTrue(TENANT_A))
                .thenReturn(Optional.of(sucursalPrincipal));

        sucursalService.regularizarDatosSucursalPrincipal(TENANT_A);

        verify(sucursalRepository, never()).save(any());
        verify(sucursalRepository, never()).saveAll(any());
    }

    // ── P: idempotencia — segunda ejecución no duplica datos ─────────────────

    @Test
    @DisplayName("P — ejecución repetida → backfill llamado N veces sin error (idempotente en BD)")
    void idempotente_dosEjecuciones() {
        when(sucursalRepository.findByTenantIdAndEsPrincipalTrue(TENANT_A))
                .thenReturn(Optional.of(sucursalPrincipal));

        sucursalService.regularizarDatosSucursalPrincipal(TENANT_A);
        sucursalService.regularizarDatosSucursalPrincipal(TENANT_A);

        // Los queries en BD son idempotentes (WHERE IS NULL / ON CONFLICT DO NOTHING),
        // el service no debe fallar en la segunda ejecución
        verify(ventaRepository, times(2)).asignarSucursalDondeEsNulo(SUCURSAL_ID, TENANT_A);
        verify(stockLoteRepository, times(2)).asignarSucursalDondeEsNulo(SUCURSAL_ID, TENANT_A);
        verify(stockSucursalRepository, times(2)).inicializarStockParaSucursal(SUCURSAL_ID, TENANT_A);
    }

    // ── P: aislamiento entre tenants ─────────────────────────────────────────

    @Test
    @DisplayName("P — aislamiento: backfill de TENANT_B no afecta TENANT_A")
    void aislamientoEntreTenantsDistintos() {
        Sucursal sucursalB = Sucursal.builder()
                .id(99L)
                .tenantId(TENANT_B)
                .nombre("Sucursal Principal")
                .esPrincipal(true)
                .activo(true)
                .bloqueadaPorPlan(false)
                .build();

        when(sucursalRepository.findByTenantIdAndEsPrincipalTrue(TENANT_A))
                .thenReturn(Optional.of(sucursalPrincipal));
        when(sucursalRepository.findByTenantIdAndEsPrincipalTrue(TENANT_B))
                .thenReturn(Optional.of(sucursalB));

        sucursalService.regularizarDatosSucursalPrincipal(TENANT_A);
        sucursalService.regularizarDatosSucursalPrincipal(TENANT_B);

        // Cada backfill usa su propio tenantId — nunca se cruzan
        verify(ventaRepository).asignarSucursalDondeEsNulo(SUCURSAL_ID, TENANT_A);
        verify(ventaRepository).asignarSucursalDondeEsNulo(99L, TENANT_B);
        verify(ventaRepository, never()).asignarSucursalDondeEsNulo(SUCURSAL_ID, TENANT_B);
        verify(ventaRepository, never()).asignarSucursalDondeEsNulo(99L, TENANT_A);

        verify(stockLoteRepository).asignarSucursalDondeEsNulo(SUCURSAL_ID, TENANT_A);
        verify(stockLoteRepository).asignarSucursalDondeEsNulo(99L, TENANT_B);
        verify(stockLoteRepository, never()).asignarSucursalDondeEsNulo(SUCURSAL_ID, TENANT_B);
        verify(stockLoteRepository, never()).asignarSucursalDondeEsNulo(99L, TENANT_A);
    }
}
