package com.stockflow.controller;

import com.stockflow.dto.MovimientoInventarioDTO;
import com.stockflow.entity.StockLote;
import com.stockflow.exception.BadRequestException;
import com.stockflow.exception.ResourceNotFoundException;
import com.stockflow.mapper.MovimientoInventarioMapper;
import com.stockflow.repository.*;
import com.stockflow.service.MovimientoInventarioService;
import com.stockflow.service.ProductoService;
import com.stockflow.service.StockLoteService;
import com.stockflow.service.SucursalService;
import com.stockflow.service.UsuarioService;
import com.stockflow.util.TenantContext;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;

import java.util.Map;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.doNothing;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * Tests de validación de sucursal operativa en MovimientoInventarioController.
 * Cubre:
 *   - POST /movimientos-inventario (R1)
 *   - POST /movimientos-inventario/merma (R2)
 */
@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
@DisplayName("MovimientoInventarioController — guard de sucursal operativa")
class MovimientoInventarioControllerSucursalTest {

    private static final String TENANT = "vet-fonavi";
    private static final Long   SUC_ID = 10L;
    private static final Long   MOV_ID = 99L;

    // ── Todas las dependencias del controller ────────────────────────────────
    @Mock private MovimientoInventarioService                movimientoService;
    @Mock private MovimientoInventarioRepository             movimientoRepository;
    @Mock private ProductoService                            productoService;
    @Mock private UsuarioService                             usuarioService;
    @Mock private MovimientoInventarioMapper                 movimientoMapper;
    @Mock private com.stockflow.repository.ProductoVarianteRepository productoVarianteRepository;
    @Mock private com.stockflow.repository.ProductoVarianteStockSucursalRepository varianteStockSucursalRepository;
    @Mock private ProductoStockSucursalRepository            stockSucursalRepository;
    @Mock private SucursalRepository                         sucursalRepository;
    @Mock private StockLoteService                           stockLoteService;
    @Mock private DetalleVentaRepository                     detalleVentaRepository;
    @Mock private StockLoteRepository                        stockLoteRepository;
    @Mock private ProveedorRepository                        proveedorRepository;
    @Mock private ProductoRepository                         productoRepository;
    @Mock private UsuarioTenantRepository                    usuarioTenantRepository;
    @Mock private SucursalService                            sucursalService;

    @InjectMocks
    private MovimientoInventarioController controller;

    @BeforeEach
    void setTenantContext() {
        TenantContext.setCurrentTenant(TENANT);
    }

    @AfterEach
    void clearTenantContext() {
        TenantContext.clear();
    }

    // ── R1: POST /movimientos-inventario ────────────────────────────────────

    @Nested
    @DisplayName("R1 — POST /movimientos-inventario: movimientos manuales de inventario")
    class MovimientoManual {

        private MovimientoInventarioDTO dto(Long sucursalId) {
            return MovimientoInventarioDTO.builder()
                    .productoId(1L)
                    .usuarioId(1L)
                    .tipo("ENTRADA")
                    .cantidad(5)
                    .sucursalId(sucursalId)
                    .build();
        }

        @Test
        @DisplayName("sucursal bloqueada por plan → BadRequestException (rechazado)")
        void sucursalBloqueada_rechaza() {
            doThrow(new BadRequestException("bloqueada"))
                    .when(sucursalService).validarSucursalOperativa(anyLong(), anyString());

            assertThatThrownBy(() -> controller.crear(dto(SUC_ID)))
                    .isInstanceOf(BadRequestException.class)
                    .hasMessageContaining("bloqueada");
        }

        @Test
        @DisplayName("sucursal desactivada manualmente → BadRequestException (rechazado)")
        void sucursalDesactivada_rechaza() {
            doThrow(new BadRequestException("desactivada"))
                    .when(sucursalService).validarSucursalOperativa(anyLong(), anyString());

            assertThatThrownBy(() -> controller.crear(dto(SUC_ID)))
                    .isInstanceOf(BadRequestException.class);
        }

        @Test
        @DisplayName("sucursal de otro tenant → ResourceNotFoundException (rechazado)")
        void sucursalOtroTenant_rechaza() {
            doThrow(new ResourceNotFoundException("Sucursal no encontrada"))
                    .when(sucursalService).validarSucursalOperativa(anyLong(), anyString());

            assertThatThrownBy(() -> controller.crear(dto(SUC_ID)))
                    .isInstanceOf(ResourceNotFoundException.class);
        }

        @Test
        @DisplayName("tenantId proviene de TenantContext, no del body — validar que se pasa el correcto")
        void tenantIdVieneDeLContexto_noDelBody() {
            doNothing().when(sucursalService).validarSucursalOperativa(anyLong(), anyString());

            // Ignorar las excepciones posteriores al guard (producto no mockeado, etc.)
            try { controller.crear(dto(SUC_ID)); } catch (Exception ignored) {}

            // Verificar que el tenantId pasado a validarSucursalOperativa es el del contexto
            verify(sucursalService).validarSucursalOperativa(SUC_ID, TENANT);
        }

        @Test
        @DisplayName("sucursalId=null → no lanza excepción de sucursal (operaciones sin sucursal permitidas)")
        void sucursalIdNulo_noBloqueaPorSucursal() {
            doNothing().when(sucursalService).validarSucursalOperativa(anyLong(), anyString());

            // Ignorar las excepciones posteriores al guard
            try { controller.crear(dto(null)); } catch (BadRequestException e) {
                throw new AssertionError("No debe lanzar BadRequestException por sucursal null", e);
            } catch (Exception ignored) {}

            verify(sucursalService).validarSucursalOperativa(null, TENANT);
        }
    }

    // ── R2: POST /movimientos-inventario/merma ──────────────────────────────

    @Nested
    @DisplayName("R2 — POST /movimientos-inventario/merma: baja de lotes vencidos")
    class Merma {

        private StockLote loteActivo(Long sucursalId) {
            return StockLote.builder()
                    .tenantId(TENANT)
                    .productoId(1L)
                    .sucursalId(sucursalId)
                    .stockActual(10)
                    .build();
        }

        private Map<String, Object> body() {
            return Map.of("movimientoOrigenId", MOV_ID, "cantidad", 3);
        }

        @Test
        @DisplayName("merma con sucursal bloqueada → BadRequestException (rechazado)")
        void merma_sucursalBloqueada_rechaza() {
            when(stockLoteRepository.findByMovimientoId(MOV_ID))
                    .thenReturn(Optional.of(loteActivo(SUC_ID)));
            doThrow(new BadRequestException("bloqueada"))
                    .when(sucursalService).validarSucursalOperativa(SUC_ID, TENANT);

            assertThatThrownBy(() -> controller.darDeBajaLote(body()))
                    .isInstanceOf(BadRequestException.class)
                    .hasMessageContaining("bloqueada");
        }

        @Test
        @DisplayName("merma con sucursal activa → pasa el guard (comportamiento normal continúa)")
        void merma_sucursalActiva_guardPasa() {
            when(stockLoteRepository.findByMovimientoId(MOV_ID))
                    .thenReturn(Optional.of(loteActivo(SUC_ID)));
            doNothing().when(sucursalService).validarSucursalOperativa(SUC_ID, TENANT);

            // Ignorar excepciones posteriores al guard
            try { controller.darDeBajaLote(body()); } catch (Exception ignored) {}

            verify(sucursalService).validarSucursalOperativa(SUC_ID, TENANT);
        }

        @Test
        @DisplayName("merma con lote sin sucursalId (null) → guard pasa sin excepción")
        void merma_lotesSinSucursal_guardPasa() {
            when(stockLoteRepository.findByMovimientoId(MOV_ID))
                    .thenReturn(Optional.of(loteActivo(null)));
            doNothing().when(sucursalService).validarSucursalOperativa(null, TENANT);

            try { controller.darDeBajaLote(body()); } catch (BadRequestException e) {
                throw new AssertionError("No debe lanzar BadRequestException por sucursal null en merma", e);
            } catch (Exception ignored) {}
        }
    }
}
