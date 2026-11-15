package com.stockflow.service;

import com.stockflow.entity.Sucursal;
import com.stockflow.exception.BadRequestException;
import com.stockflow.exception.ResourceNotFoundException;
import com.stockflow.repository.*;
import com.stockflow.service.impl.SucursalServiceImpl;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;

import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
@DisplayName("SucursalService.validarSucursalOperativa — guarda operaciones en sucursales inoperativas")
class SucursalValidarOperativaTest {

    private static final String TENANT = "vet-fonavi";
    private static final Long   SUC_ID = 10L;

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

    // ── Helper ──────────────────────────────────────────────────────────────

    private Sucursal sucursalCon(boolean activo, boolean bloqueadaPorPlan) {
        return Sucursal.builder()
                .id(SUC_ID)
                .tenantId(TENANT)
                .nombre("Sucursal Test")
                .activo(activo)
                .bloqueadaPorPlan(bloqueadaPorPlan)
                .build();
    }

    // ── Tests ────────────────────────────────────────────────────────────────

    @Nested
    @DisplayName("sucursalId null → no-op (compatibilidad con operaciones sin sucursal)")
    class SucursalIdNulo {

        @Test
        @DisplayName("null no debe lanzar ninguna excepción")
        void conNullNoLanzaExcepcion() {
            assertThatCode(() -> sucursalService.validarSucursalOperativa(null, TENANT))
                    .doesNotThrowAnyException();
        }
    }

    @Nested
    @DisplayName("sucursal inexistente o de otro tenant → ResourceNotFoundException")
    class SucursalInexistente {

        @Test
        @DisplayName("sucursal no encontrada lanza ResourceNotFoundException")
        void sucursalNoEncontrada_lanzaResourceNotFound() {
            when(sucursalRepository.findById(SUC_ID)).thenReturn(Optional.empty());

            assertThatThrownBy(() -> sucursalService.validarSucursalOperativa(SUC_ID, TENANT))
                    .isInstanceOf(ResourceNotFoundException.class);
        }

        @Test
        @DisplayName("sucursal de otro tenant lanza ResourceNotFoundException")
        void sucursalDeOtroTenant_lanzaResourceNotFound() {
            Sucursal ajena = sucursalCon(true, false);
            ajena.setTenantId("otro-tenant");
            when(sucursalRepository.findById(SUC_ID)).thenReturn(Optional.of(ajena));

            assertThatThrownBy(() -> sucursalService.validarSucursalOperativa(SUC_ID, TENANT))
                    .isInstanceOf(ResourceNotFoundException.class);
        }
    }

    @Nested
    @DisplayName("sucursal inoperativa → BadRequestException con mensaje descriptivo")
    class SucursalInoperativa {

        @Test
        @DisplayName("activo=false, bloqueadaPorPlan=true → mensaje de bloqueo por plan")
        void bloqueadaPorPlan_lanzaBadRequest_conMensajePlan() {
            when(sucursalRepository.findById(SUC_ID))
                    .thenReturn(Optional.of(sucursalCon(false, true)));

            assertThatThrownBy(() -> sucursalService.validarSucursalOperativa(SUC_ID, TENANT))
                    .isInstanceOf(BadRequestException.class)
                    .hasMessageContaining("bloqueada");
        }

        @Test
        @DisplayName("activo=false, bloqueadaPorPlan=false → mensaje de desactivación manual")
        void desactivadaManualmente_lanzaBadRequest_conMensajeDesactivada() {
            when(sucursalRepository.findById(SUC_ID))
                    .thenReturn(Optional.of(sucursalCon(false, false)));

            assertThatThrownBy(() -> sucursalService.validarSucursalOperativa(SUC_ID, TENANT))
                    .isInstanceOf(BadRequestException.class)
                    .hasMessageContaining("desactivada");
        }
    }

    @Nested
    @DisplayName("sucursal activa → pasa sin excepción")
    class SucursalOperativa {

        @Test
        @DisplayName("activo=true no lanza ninguna excepción")
        void sucursalActiva_noLanzaExcepcion() {
            when(sucursalRepository.findById(SUC_ID))
                    .thenReturn(Optional.of(sucursalCon(true, false)));

            assertThatCode(() -> sucursalService.validarSucursalOperativa(SUC_ID, TENANT))
                    .doesNotThrowAnyException();
        }
    }
}
