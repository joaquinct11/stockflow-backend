package com.stockflow.controller;

import com.stockflow.mapper.DetalleVentaMapper;
import com.stockflow.mapper.VentaMapper;
import com.stockflow.repository.*;
import com.stockflow.service.*;
import com.stockflow.util.TenantContext;
import org.junit.jupiter.api.*;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

import java.util.List;
import java.util.Optional;

import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Verifica que /ventas/buscar nunca sea capturado por el handler /{id:\\d+},
 * evitando MethodArgumentTypeMismatchException ("For input string: 'buscar'").
 */
@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
@DisplayName("VentaController — routing /buscar vs /{id:\\d+}")
class VentaControllerBuscarRoutingTest {

    private static final String TENANT = "tenant-routing-test";

    @Mock private VentaService                              ventaService;
    @Mock private ProductoService                           productoService;
    @Mock private UsuarioService                            usuarioService;
    @Mock private VentaMapper                               ventaMapper;
    @Mock private DetalleVentaMapper                        detalleVentaMapper;
    @Mock private MovimientoInventarioService               movimientoService;
    @Mock private ComprobanteService                        comprobanteService;
    @Mock private NotaCreditoService                        notaCreditoService;
    @Mock private NotificacionService                       notificacionService;
    @Mock private ProductoVarianteRepository                productoVarianteRepository;
    @Mock private ProductoVarianteStockSucursalRepository   varianteStockSucursalRepository;
    @Mock private ProductoStockSucursalRepository           stockSucursalRepository;
    @Mock private SucursalRepository                        sucursalRepository;
    @Mock private VentaRepository                           ventaRepository;
    @Mock private DetalleVentaRepository                    detalleVentaRepository;
    @Mock private DevolucionRepository                      devolucionRepository;
    @Mock private StockLoteService                          stockLoteService;
    @Mock private ProductoPresentacionRepository            presentacionRepository;

    @InjectMocks private VentaController controller;

    private MockMvc mockMvc;

    @BeforeEach
    void setUp() {
        mockMvc = MockMvcBuilders.standaloneSetup(controller).build();
        TenantContext.setCurrentTenant(TENANT);
    }

    @AfterEach
    void tearDown() {
        TenantContext.clear();
    }

    // ── PROBLEMA PRINCIPAL ──────────────────────────────────────────────────

    @Test
    @DisplayName("r01: GET /ventas/buscar?q=208 → buscarVentas (HTTP 200, no MethodArgumentTypeMismatch)")
    void r01_buscar_con_q_numerico_no_dispara_obtenerPorId() throws Exception {
        when(ventaRepository.buscarVentas(eq(TENANT), anyString())).thenReturn(List.of());

        mockMvc.perform(get("/ventas/buscar").param("q", "208"))
                .andExpect(status().isOk());
    }

    @Test
    @DisplayName("r02: GET /ventas/buscar?q=NombreCliente → buscarVentas (HTTP 200)")
    void r02_buscar_con_q_texto_devuelve_ok() throws Exception {
        when(ventaRepository.buscarVentas(eq(TENANT), eq("Juan"))).thenReturn(List.of());

        mockMvc.perform(get("/ventas/buscar").param("q", "Juan"))
                .andExpect(status().isOk());
    }

    @Test
    @DisplayName("r03: GET /ventas/buscar?q=12345678 (documento) → buscarVentas (HTTP 200)")
    void r03_buscar_con_q_documento_devuelve_ok() throws Exception {
        when(ventaRepository.buscarVentas(eq(TENANT), anyString())).thenReturn(List.of());

        mockMvc.perform(get("/ventas/buscar").param("q", "12345678"))
                .andExpect(status().isOk());
    }

    // ── RUTAS EXISTENTES QUE DEBEN SEGUIR FUNCIONANDO ───────────────────────

    @Test
    @DisplayName("r04: GET /ventas/208 → obtenerPorId (404 cuando no existe, no type error)")
    void r04_get_por_id_numerico_resuelve_obtenerPorId() throws Exception {
        when(ventaService.obtenerVentaPorId(208L)).thenReturn(Optional.empty());

        mockMvc.perform(get("/ventas/208"))
                .andExpect(status().isNotFound());
    }

    @Test
    @DisplayName("r05: GET /ventas/208/estado-devolucion → estadoDevolucion (HTTP 200)")
    void r05_estado_devolucion_por_id_numerico() throws Exception {
        when(detalleVentaRepository.findByVentaId(208L)).thenReturn(List.of());
        when(devolucionRepository.sumarDevueltoPorProducto(208L, TENANT)).thenReturn(List.of());

        mockMvc.perform(get("/ventas/208/estado-devolucion"))
                .andExpect(status().isOk());
    }

    // ── VALIDACIÓN DE PARÁMETRO OBLIGATORIO ──────────────────────────────────

    @Test
    @DisplayName("r06: GET /ventas/buscar sin ?q → 400 (parámetro requerido ausente, no type mismatch)")
    void r06_buscar_sin_q_es_bad_request_no_type_mismatch() throws Exception {
        mockMvc.perform(get("/ventas/buscar"))
                .andExpect(status().isBadRequest());
    }
}
