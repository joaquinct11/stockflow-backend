package com.stockflow.service;

import com.stockflow.dto.AbrirCajaRequestDTO;
import com.stockflow.dto.OrdenCompraItemDTO;
import com.stockflow.dto.OrdenCompraRequestDTO;
import com.stockflow.dto.RecepcionRequestDTO;
import com.stockflow.entity.Venta;
import com.stockflow.exception.BadRequestException;
import com.stockflow.repository.*;
import com.stockflow.service.impl.CajaServiceImpl;
import com.stockflow.service.impl.OrdenCompraServiceImpl;
import com.stockflow.service.impl.RecepcionServiceImpl;
import com.stockflow.service.impl.VentaServiceImpl;
import com.stockflow.util.OcPdfGenerator;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.doThrow;

/**
 * Regresión: los 4 servicios de escritura rechazan operaciones en sucursales inoperativas.
 * Se verifica que la llamada a SucursalService.validarSucursalOperativa se encadena
 * correctamente y la excepción llega al llamador sin ser absorbida.
 */
@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
@DisplayName("Regresión: operaciones rechazadas en sucursales bloqueadas/desactivadas")
class SucursalBloqueadaOperacionesTest {

    private static final String TENANT = "vet-fonavi";
    private static final Long   SUC_ID = 10L;

    // ── VentaServiceImpl ─────────────────────────────────────────────────────

    @Nested
    @DisplayName("VentaServiceImpl.crearVenta — sucursal bloqueada → BadRequestException")
    class VentaBloqueada {

        @Mock private VentaRepository                  ventaRepository;
        @Mock private DetalleVentaRepository           detalleVentaRepository;
        @Mock private ProductoRepository               productoRepository;
        @Mock private UsuarioRepository                usuarioRepository;
        @Mock private MovimientoInventarioService      movimientoInventarioService;
        @Mock private NotaCreditoService               notaCreditoService;
        @Mock private ProductoStockSucursalRepository  stockSucursalRepository;
        @Mock private SucursalRepository               sucursalRepository;
        @Mock private StockLoteService                 stockLoteService;
        @Mock private SucursalService                  sucursalService;

        @InjectMocks
        private VentaServiceImpl ventaService;

        @Test
        @DisplayName("venta con sucursal bloqueada lanza BadRequestException")
        void crearVenta_sucursalBloqueada_lanzaExcepcion() {
            doThrow(new BadRequestException("bloqueada"))
                    .when(sucursalService).validarSucursalOperativa(anyLong(), anyString());

            Venta venta = Venta.builder()
                    .tenantId(TENANT)
                    .sucursalId(SUC_ID)
                    .detalles(List.of())
                    .build();

            assertThatThrownBy(() -> ventaService.crearVenta(venta))
                    .isInstanceOf(BadRequestException.class);
        }

        @Test
        @DisplayName("venta con sucursalId=null pasa sin excepción de validación")
        void crearVenta_sinSucursal_noLanzaExcepcion() {
            Venta venta = Venta.builder()
                    .tenantId(TENANT)
                    .sucursalId(null)
                    .detalles(List.of())
                    .build();

            // sucursalService.validarSucursalOperativa(null, ...) no debe lanzar
            // ventaRepository.save sí lanzará NullPointerException en la entidad, pero
            // la validación de sucursal no debe ser el origen del error.
            try {
                ventaService.crearVenta(venta);
            } catch (BadRequestException e) {
                throw new AssertionError("No debe lanzar BadRequestException por sucursal cuando es null", e);
            } catch (Exception ignored) {
                // Otras excepciones (NPE de Mockito, etc.) son aceptables
            }
        }
    }

    // ── OrdenCompraServiceImpl ───────────────────────────────────────────────

    @Nested
    @DisplayName("OrdenCompraServiceImpl.crearOrdenCompra — sucursal bloqueada → BadRequestException")
    class OrdenCompraBloqueada {

        @Mock private OrdenCompraRepository           ordenCompraRepository;
        @Mock private OrdenCompraDetalleRepository    detalleRepository;
        @Mock private ProveedorRepository             proveedorRepository;
        @Mock private ProductoRepository              productoRepository;
        @Mock private UsuarioRepository               usuarioRepository;
        @Mock private EmailService                    emailService;
        @Mock private com.stockflow.repository.TenantRepository tenantRepository;
        @Mock private OcPdfGenerator                  ocPdfGenerator;
        @Mock private SucursalService                 sucursalService;

        @InjectMocks
        private OrdenCompraServiceImpl ordenCompraService;

        @Test
        @DisplayName("orden de compra con sucursal bloqueada lanza BadRequestException")
        void crearOrdenCompra_sucursalBloqueada_lanzaExcepcion() {
            doThrow(new BadRequestException("bloqueada"))
                    .when(sucursalService).validarSucursalOperativa(anyLong(), anyString());

            OrdenCompraRequestDTO request = OrdenCompraRequestDTO.builder()
                    .proveedorId(1L)
                    .sucursalId(SUC_ID)
                    .items(List.of(OrdenCompraItemDTO.builder()
                            .productoId(1L).cantidadSolicitada(5).build()))
                    .build();

            assertThatThrownBy(() -> ordenCompraService.crearOrdenCompra(request, 1L, TENANT))
                    .isInstanceOf(BadRequestException.class);
        }
    }

    // ── RecepcionServiceImpl ─────────────────────────────────────────────────

    @Nested
    @DisplayName("RecepcionServiceImpl.crearRecepcion — sucursal bloqueada → BadRequestException")
    class RecepcionBloqueada {

        @Mock private RecepcionRepository                  recepcionRepository;
        @Mock private RecepcionDetalleRepository           detalleRepository;
        @Mock private OrdenCompraRepository                ocRepository;
        @Mock private OrdenCompraDetalleRepository         ocDetalleRepository;
        @Mock private ProveedorRepository                  proveedorRepository;
        @Mock private ProductoRepository                   productoRepository;
        @Mock private UsuarioRepository                    usuarioRepository;
        @Mock private MovimientoInventarioRepository       movimientoRepository;
        @Mock private GastoRepository                      gastoRepository;
        @Mock private ProductoStockSucursalRepository      stockSucursalRepository;
        @Mock private SucursalRepository                   sucursalRepository;
        @Mock private StockLoteService                     stockLoteService;
        @Mock private SucursalService                      sucursalService;

        @InjectMocks
        private RecepcionServiceImpl recepcionService;

        @Test
        @DisplayName("recepción con sucursal bloqueada lanza BadRequestException")
        void crearRecepcion_sucursalBloqueada_lanzaExcepcion() {
            doThrow(new BadRequestException("bloqueada"))
                    .when(sucursalService).validarSucursalOperativa(anyLong(), anyString());

            RecepcionRequestDTO request = RecepcionRequestDTO.builder()
                    .proveedorId(1L)
                    .sucursalId(SUC_ID)
                    .build();

            assertThatThrownBy(() -> recepcionService.crearRecepcion(request, 1L, TENANT))
                    .isInstanceOf(BadRequestException.class);
        }
    }

    // ── CajaServiceImpl ──────────────────────────────────────────────────────

    @Nested
    @DisplayName("CajaServiceImpl.abrir — sucursal bloqueada → BadRequestException")
    class CajaBloqueada {

        @Mock private CajaRepository                         cajaRepository;
        @Mock private VentaRepository                        ventaRepository;
        @Mock private RetiroCajaRepository                   retiroCajaRepository;
        @Mock private UsuarioRepository                      usuarioRepository;
        @Mock private com.stockflow.repository.TenantRepository tenantRepository;
        @Mock private EmailService                           emailService;
        @Mock private NotificacionService                    notificacionService;
        @Mock private SucursalService                        sucursalService;

        @InjectMocks
        private CajaServiceImpl cajaService;

        @Test
        @DisplayName("apertura de caja con sucursal bloqueada lanza BadRequestException")
        void abrir_sucursalBloqueada_lanzaExcepcion() {
            doThrow(new BadRequestException("bloqueada"))
                    .when(sucursalService).validarSucursalOperativa(anyLong(), anyString());

            AbrirCajaRequestDTO request = new AbrirCajaRequestDTO();
            request.setSucursalId(SUC_ID);

            assertThatThrownBy(() -> cajaService.abrir(request, 1L, "cajero", TENANT))
                    .isInstanceOf(BadRequestException.class);
        }
    }
}
