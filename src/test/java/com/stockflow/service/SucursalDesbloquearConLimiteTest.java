package com.stockflow.service;

import com.stockflow.entity.Sucursal;
import com.stockflow.repository.*;
import com.stockflow.service.impl.SucursalServiceImpl;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
@DisplayName("SucursalService.desbloquearSucursalesConLimite — respeta el plan vigente")
class SucursalDesbloquearConLimiteTest {

    private static final String TENANT = "vet-fonavi";

    @Mock private SucursalRepository                       sucursalRepository;
    @Mock private ProductoStockSucursalRepository          stockSucursalRepository;
    @Mock private ProductoVarianteStockSucursalRepository  varianteStockSucursalRepository;
    @Mock private PlanLimitService                         planLimitService;
    @Mock private VentaRepository                          ventaRepository;
    @Mock private MovimientoInventarioRepository           movimientoRepository;
    @Mock private GastoRepository                          gastoRepository;
    @Mock private CajaRepository                           cajaRepository;
    @Mock private DevolucionRepository                     devolucionRepository;
    @Mock private NotaCreditoRepository                    notaCreditoRepository;
    @Mock private OrdenCompraRepository                    ordenCompraRepository;
    @Mock private RecepcionRepository                      recepcionRepository;
    @Mock private ComprobanteRepository                    comprobanteRepository;
    @Mock private UsuarioTenantRepository                  usuarioTenantRepository;
    @Mock private CertificadoRepository                    certificadoRepository;

    @InjectMocks
    private SucursalServiceImpl sucursalService;

    private Sucursal bloqueada(Long id) {
        return Sucursal.builder().id(id).tenantId(TENANT).activo(false).bloqueadaPorPlan(true).build();
    }

    @Nested
    @DisplayName("Sin espacios disponibles → no se reactiva ninguna")
    class SinEspacios {

        @Test
        @DisplayName("límite ya alcanzado → devuelve 0 y no guarda")
        void limiteAlcanzado_noDesbloquea() {
            when(sucursalRepository.countByTenantIdAndActivoTrue(TENANT)).thenReturn(5L);
            when(sucursalRepository.findByTenantIdAndBloqueadaPorPlanTrue(TENANT))
                    .thenReturn(List.of(bloqueada(10L)));

            int resultado = sucursalService.desbloquearSucursalesConLimite(TENANT, 5);

            assertThat(resultado).isZero();
            verify(sucursalRepository, never()).saveAll(anyList());
        }
    }

    @Nested
    @DisplayName("Hay espacios → reactiva respetando el límite")
    class ConEspacios {

        @Test
        @DisplayName("una bloqueada, un espacio → se reactiva")
        void unaBloqueada_unEspacio_seDesbloquea() {
            when(sucursalRepository.countByTenantIdAndActivoTrue(TENANT)).thenReturn(4L);
            when(sucursalRepository.findByTenantIdAndBloqueadaPorPlanTrue(TENANT))
                    .thenReturn(List.of(bloqueada(10L)));

            int resultado = sucursalService.desbloquearSucursalesConLimite(TENANT, 5);

            assertThat(resultado).isEqualTo(1);
        }

        @Test
        @DisplayName("tres bloqueadas, un espacio → solo se reactiva la de id menor (más antigua)")
        void tresBloqueadas_unEspacio_reactivaLaMasAntigua() {
            when(sucursalRepository.countByTenantIdAndActivoTrue(TENANT)).thenReturn(4L);
            when(sucursalRepository.findByTenantIdAndBloqueadaPorPlanTrue(TENANT))
                    .thenReturn(List.of(bloqueada(30L), bloqueada(10L), bloqueada(20L)));  // desordenadas

            @SuppressWarnings("unchecked")
            ArgumentCaptor<List<Sucursal>> captor = ArgumentCaptor.forClass(List.class);
            when(sucursalRepository.saveAll(captor.capture())).thenReturn(List.of());

            int resultado = sucursalService.desbloquearSucursalesConLimite(TENANT, 5);

            assertThat(resultado).isEqualTo(1);
            List<Sucursal> guardadas = captor.getValue();
            assertThat(guardadas).hasSize(1);
            assertThat(guardadas.get(0).getId()).isEqualTo(10L);  // id menor primero
        }

        @Test
        @DisplayName("dos bloqueadas, dos espacios → se reactivan ambas")
        void dosBloqueadas_dosEspacios_seReactivanAmbas() {
            when(sucursalRepository.countByTenantIdAndActivoTrue(TENANT)).thenReturn(3L);
            when(sucursalRepository.findByTenantIdAndBloqueadaPorPlanTrue(TENANT))
                    .thenReturn(List.of(bloqueada(10L), bloqueada(20L)));

            int resultado = sucursalService.desbloquearSucursalesConLimite(TENANT, 5);

            assertThat(resultado).isEqualTo(2);
        }

        @Test
        @DisplayName("sin bloqueadas → devuelve 0")
        void sinBloqueadas_devuelveCero() {
            when(sucursalRepository.countByTenantIdAndActivoTrue(TENANT)).thenReturn(1L);
            when(sucursalRepository.findByTenantIdAndBloqueadaPorPlanTrue(TENANT))
                    .thenReturn(List.of());

            int resultado = sucursalService.desbloquearSucursalesConLimite(TENANT, 5);

            assertThat(resultado).isZero();
            verify(sucursalRepository, never()).saveAll(anyList());
        }

        @Test
        @DisplayName("BÁSICO con una activa y una bloqueada, límite=1 → no desbloquea (no hay espacio)")
        void basicoLimite1_noDesbloquea() {
            when(sucursalRepository.countByTenantIdAndActivoTrue(TENANT)).thenReturn(1L);
            when(sucursalRepository.findByTenantIdAndBloqueadaPorPlanTrue(TENANT))
                    .thenReturn(List.of(bloqueada(10L)));

            int resultado = sucursalService.desbloquearSucursalesConLimite(TENANT, 1);

            assertThat(resultado).isZero();
            verify(sucursalRepository, never()).saveAll(anyList());
        }
    }
}
