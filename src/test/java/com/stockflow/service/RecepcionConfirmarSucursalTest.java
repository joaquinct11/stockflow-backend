package com.stockflow.service;

import com.stockflow.entity.Recepcion;
import com.stockflow.exception.BadRequestException;
import com.stockflow.exception.ResourceNotFoundException;
import com.stockflow.repository.*;
import com.stockflow.service.impl.RecepcionServiceImpl;
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

import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.doNothing;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * R3 — RecepcionServiceImpl.confirmar() debe rechazar confirmaciones
 * en sucursales inoperativas, sin efectos parciales sobre stock.
 */
@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
@DisplayName("R3 — RecepcionServiceImpl.confirmar(): guard de sucursal operativa")
class RecepcionConfirmarSucursalTest {

    private static final String TENANT     = "vet-fonavi";
    private static final Long   REC_ID     = 1L;
    private static final Long   USER_ID    = 5L;
    private static final Long   SUC_ID     = 10L;

    @Mock private RecepcionRepository              recepcionRepository;
    @Mock private RecepcionDetalleRepository       detalleRepository;
    @Mock private OrdenCompraRepository            ocRepository;
    @Mock private OrdenCompraDetalleRepository     ocDetalleRepository;
    @Mock private ProveedorRepository              proveedorRepository;
    @Mock private ProductoRepository               productoRepository;
    @Mock private UsuarioRepository                usuarioRepository;
    @Mock private MovimientoInventarioRepository   movimientoRepository;
    @Mock private GastoRepository                  gastoRepository;
    @Mock private ProductoStockSucursalRepository  stockSucursalRepository;
    @Mock private SucursalRepository               sucursalRepository;
    @Mock private StockLoteService                 stockLoteService;
    @Mock private SucursalService                  sucursalService;

    @InjectMocks
    private RecepcionServiceImpl recepcionService;

    private Recepcion recepcionBorrador(Long sucursalId) {
        return Recepcion.builder()
                .id(REC_ID)
                .tenantId(TENANT)
                .sucursalId(sucursalId)
                .estado("BORRADOR")
                .build();
    }

    // ── Sucursal bloqueada por plan ─────────────────────────────────────────

    @Nested
    @DisplayName("Sucursal bloqueada por plan → confirmar rechazado")
    class SucursalBloqueadaPorPlan {

        @Test
        @DisplayName("lanza BadRequestException antes de modificar stock")
        void confirmar_sucursalBloqueada_lanzaExcepcion() {
            when(recepcionRepository.findById(REC_ID))
                    .thenReturn(Optional.of(recepcionBorrador(SUC_ID)));
            doThrow(new BadRequestException("suspendida"))
                    .when(sucursalService).validarSucursalOperativa(SUC_ID, TENANT);

            assertThatThrownBy(() -> recepcionService.confirmar(REC_ID, USER_ID, TENANT))
                    .isInstanceOf(BadRequestException.class)
                    .hasMessageContaining("suspendida");
        }

        @Test
        @DisplayName("no guarda movimientos ni gastos cuando la sucursal está bloqueada")
        void confirmar_sucursalBloqueada_sinEfectosParcialesDeStock() {
            when(recepcionRepository.findById(REC_ID))
                    .thenReturn(Optional.of(recepcionBorrador(SUC_ID)));
            doThrow(new BadRequestException("suspendida"))
                    .when(sucursalService).validarSucursalOperativa(SUC_ID, TENANT);

            try {
                recepcionService.confirmar(REC_ID, USER_ID, TENANT);
            } catch (BadRequestException ignored) {}

            verify(movimientoRepository, never()).save(org.mockito.ArgumentMatchers.any());
            verify(gastoRepository,       never()).save(org.mockito.ArgumentMatchers.any());
            verify(stockLoteService, never()).registrarLote(
                    anyString(), anyLong(), anyLong(), anyLong(),
                    anyString(), org.mockito.ArgumentMatchers.any(),
                    org.mockito.ArgumentMatchers.any(Integer.class), anyLong(),
                    org.mockito.ArgumentMatchers.any(),
                    org.mockito.ArgumentMatchers.any());
        }
    }

    // ── Sucursal desactivada manualmente ───────────────────────────────────

    @Nested
    @DisplayName("Sucursal desactivada manualmente → confirmar rechazado")
    class SucursalDesactivada {

        @Test
        @DisplayName("lanza BadRequestException con mensaje de desactivación")
        void confirmar_sucursalDesactivada_lanzaExcepcion() {
            when(recepcionRepository.findById(REC_ID))
                    .thenReturn(Optional.of(recepcionBorrador(SUC_ID)));
            doThrow(new BadRequestException("desactivada"))
                    .when(sucursalService).validarSucursalOperativa(SUC_ID, TENANT);

            assertThatThrownBy(() -> recepcionService.confirmar(REC_ID, USER_ID, TENANT))
                    .isInstanceOf(BadRequestException.class);
        }
    }

    // ── Seguridad de tenant ────────────────────────────────────────────────

    @Nested
    @DisplayName("Recepción de otro tenant → ResourceNotFoundException (sin revelar info)")
    class TenantAjeno {

        @Test
        @DisplayName("recepción de otro tenant lanza ResourceNotFoundException")
        void confirmar_recepcionDeOtroTenant_lanzaResourceNotFound() {
            Recepcion ajena = recepcionBorrador(SUC_ID);
            ajena.setTenantId("otro-tenant");
            when(recepcionRepository.findById(REC_ID)).thenReturn(Optional.of(ajena));

            assertThatThrownBy(() -> recepcionService.confirmar(REC_ID, USER_ID, TENANT))
                    .isInstanceOf(ResourceNotFoundException.class);

            verify(sucursalService, never()).validarSucursalOperativa(anyLong(), anyString());
        }
    }

    // ── Sucursal operativa: confirmación normal ────────────────────────────

    @Nested
    @DisplayName("Sucursal operativa → el guard pasa y la confirmación continúa")
    class SucursalOperativa {

        @Test
        @DisplayName("validarSucursalOperativa es invocado con el sucursalId de la recepción")
        void confirmar_sucursalActiva_guardPasaYContinua() {
            when(recepcionRepository.findById(REC_ID))
                    .thenReturn(Optional.of(recepcionBorrador(SUC_ID)));
            doNothing().when(sucursalService).validarSucursalOperativa(SUC_ID, TENANT);

            // El flujo post-guard fallará (detalles vacíos), pero lo importante
            // es que el guard se invocó y no bloqueó.
            try {
                recepcionService.confirmar(REC_ID, USER_ID, TENANT);
            } catch (Exception ignored) {}

            verify(sucursalService).validarSucursalOperativa(SUC_ID, TENANT);
        }
    }
}
