package com.stockflow.service.impl;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.stockflow.dto.gre.GuiaRemisionResponse;
import com.stockflow.dto.gre.ReconciliarGRERequest;
import com.stockflow.entity.MovimientoInventario;
import com.stockflow.entity.Producto;
import com.stockflow.entity.ProductoPresentacion;
import com.stockflow.entity.Sucursal;
import com.stockflow.entity.Venta;
import com.stockflow.entity.gre.*;
import com.stockflow.exception.BadRequestException;
import com.stockflow.exception.ResourceNotFoundException;
import com.stockflow.repository.*;
import com.stockflow.service.MovimientoInventarioService;
import com.stockflow.service.StockLoteService;
import com.stockflow.util.TenantContext;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

/**
 * Tests de reconciliación F-08.
 *
 * Escenario base: T001-2 quedó en ERROR local pero fue aceptada por Lucode/SUNAT
 * debido al bug de deserialización del campo pdf.a4 en LucodeGREPayload.
 *
 * Tests:
 *  T-REC-1: Reconciliación correcta de una GRE aceptada
 *  T-REC-2: No se repite una SALIDA existente (anti-duplicado)
 *  T-REC-3: No se descuenta inventario si la GRE tiene venta_id (POS ya lo hizo)
 *  T-REC-4: Se respeta el factor de presentación al calcular cantidadBase
 *  T-REC-5: No se reconcilia una GRE que no está en ERROR
 *  T-REC-6: No se reconcilia una GRE de otro tenant (cross-tenant bloqueado)
 *  T-REC-7: Se registra el evento de auditoría RECONCILIACION_F08 con trazabilidad
 *  T-REC-8: La operación es transaccional — si save falla, eventoRepo no se ejecuta
 */
@ExtendWith(MockitoExtension.class)
@DisplayName("GuiaRemisionService — Reconciliación F-08")
class GuiaRemisionReconciliacionTest {

    private static final String TENANT   = "tenant-abc";
    private static final Long   USER_ID  = 42L;

    @Mock GuiaRemisionRepository           guiaRepo;
    @Mock GuiaRemisionEventoRepository     eventoRepo;
    @Mock SucursalRepository               sucursalRepo;
    @Mock ProductoRepository               productoRepo;
    @Mock TenantRepository                 tenantRepo;
    @Mock VentaRepository                  ventaRepo;
    @Mock ProductoPresentacionRepository   presentacionRepo;
    @Mock MovimientoInventarioService      movimientoService;
    @Mock MovimientoInventarioRepository   movimientoRepo;
    @Mock LucodeGREService                 lucodeGREService;
    @Mock GuiaRemisionPdfService           pdfService;
    @Mock StockLoteService                 stockLoteService;
    @Mock UbigeoRepository                 ubigeoRepository;

    private GuiaRemisionServiceImpl service;

    @BeforeEach
    void setUp() {
        TenantContext.setCurrentTenant(TENANT);
        TenantContext.setCurrentUserId(USER_ID);
        service = new GuiaRemisionServiceImpl(
                guiaRepo, eventoRepo, sucursalRepo, productoRepo, tenantRepo,
                ventaRepo, presentacionRepo, movimientoService, movimientoRepo,
                lucodeGREService, pdfService, new ObjectMapper(), stockLoteService,
                ubigeoRepository);
    }

    @AfterEach
    void tearDown() {
        TenantContext.clear();
    }

    // ── Helpers ───────────────────────────────────────────────────────────────

    private GuiaRemision greEnError(Long id) {
        Sucursal sucursal = new Sucursal();
        sucursal.setId(10L);
        sucursal.setNombre("Almacén Central");

        Producto producto = new Producto();
        producto.setId(1L);
        producto.setNombre("Paracetamol 500mg");
        producto.setTenantId(TENANT);
        producto.setStockActual(100);

        GuiaRemisionItem item = GuiaRemisionItem.builder()
                .id(1L)
                .producto(producto)
                .descripcion("Paracetamol 500mg")
                .cantidad(new BigDecimal("3"))
                .tenantId(TENANT)
                .build();

        GuiaRemision guia = GuiaRemision.builder()
                .id(id)
                .tenantId(TENANT)
                .serie("T001")
                .correlativo(2)
                .tipoTraslado(TipoTrasladoGRE.SUCURSALES)
                .motivoTrasladoCodigo("04")
                .modalidadTransporte(ModalidadTransporteGRE.PRIVADO)
                .fechaTraslado(LocalDate.now())
                .sucursalOrigen(sucursal)
                .estadoTributario(EstadoTributarioGRE.ERROR)
                .estadoLogistico(EstadoLogisticoGRE.PENDIENTE)
                .lucodeErrorRaw("{\"error\":\"UnrecognizedPropertyException\"}")
                .items(new java.util.ArrayList<>(List.of(item)))
                .build();

        item.setGuiaRemision(guia);
        return guia;
    }

    private ReconciliarGRERequest requestValido() {
        return new ReconciliarGRERequest(
                "hash-abc123",
                "https://lucode.io/xml/T001-2.xml",
                "https://lucode.io/cdr/T001-2.zip",
                "https://lucode.io/pdf/T001-2.pdf");
    }

    // ── T-REC-1: Reconciliación correcta ─────────────────────────────────────

    @Test
    @DisplayName("T-REC-1: GRE en ERROR → pasa a EMITIDA y registra movimientos de SALIDA")
    void reconciliarCorrectamente() {
        GuiaRemision guia = greEnError(99L);
        when(guiaRepo.findByIdAndTenantIdForUpdate(99L, TENANT)).thenReturn(Optional.of(guia));
        when(movimientoRepo.existsByReferenciaAndTipoAndTenantId("GRE-99", "SALIDA", TENANT)).thenReturn(false);
        when(guiaRepo.save(any())).thenAnswer(inv -> inv.getArgument(0));

        GuiaRemisionResponse resp = service.reconciliarGREAceptada(99L, requestValido());

        assertThat(resp.getEstadoTributario()).isEqualTo(EstadoTributarioGRE.EMITIDA);
        assertThat(resp.getEstadoLogistico()).isEqualTo(EstadoLogisticoGRE.EN_TRANSITO);
        assertThat(resp.getLucodeHash()).isEqualTo("hash-abc123");
        assertThat(resp.getXmlUrl()).isEqualTo("https://lucode.io/xml/T001-2.xml");
        assertThat(resp.getCdrUrl()).isEqualTo("https://lucode.io/cdr/T001-2.zip");

        // Se creó exactamente 1 movimiento de SALIDA (1 ítem, factor=1, cantidad=3)
        ArgumentCaptor<MovimientoInventario> captor = ArgumentCaptor.forClass(MovimientoInventario.class);
        verify(movimientoService, times(1)).crearMovimiento(captor.capture());
        MovimientoInventario mov = captor.getValue();
        assertThat(mov.getTipo()).isEqualTo("SALIDA");
        assertThat(mov.getCantidad()).isEqualTo(3);
        assertThat(mov.getReferencia()).isEqualTo("GRE-99");
        assertThat(mov.getTenantId()).isEqualTo(TENANT);
    }

    // ── T-REC-2: Anti-duplicado ───────────────────────────────────────────────

    @Test
    @DisplayName("T-REC-2: Si ya existe una SALIDA para esta GRE, lanza BadRequestException")
    void noRepiteSalidaExistente() {
        GuiaRemision guia = greEnError(99L);
        when(guiaRepo.findByIdAndTenantIdForUpdate(99L, TENANT)).thenReturn(Optional.of(guia));
        when(movimientoRepo.existsByReferenciaAndTipoAndTenantId("GRE-99", "SALIDA", TENANT)).thenReturn(true);

        assertThatThrownBy(() -> service.reconciliarGREAceptada(99L, requestValido()))
                .isInstanceOf(BadRequestException.class)
                .hasMessageContaining("Ya existen movimientos de SALIDA");

        verify(movimientoService, never()).crearMovimiento(any());
        verify(guiaRepo, never()).save(any());
    }

    // ── T-REC-3: GRE con venta_id no descuenta inventario ────────────────────

    @Test
    @DisplayName("T-REC-3: GRE con venta_id → no se crean movimientos de inventario (POS ya lo hizo)")
    void noDescontaSiTieneVentaId() {
        GuiaRemision guia = greEnError(99L);
        Venta venta = new Venta();
        venta.setId(77L);
        guia.setVenta(venta);

        when(guiaRepo.findByIdAndTenantIdForUpdate(99L, TENANT)).thenReturn(Optional.of(guia));
        when(movimientoRepo.existsByReferenciaAndTipoAndTenantId("GRE-99", "SALIDA", TENANT)).thenReturn(false);
        when(guiaRepo.save(any())).thenAnswer(inv -> inv.getArgument(0));

        GuiaRemisionResponse resp = service.reconciliarGREAceptada(99L, requestValido());

        // La guía igualmente pasa a EMITIDA
        assertThat(resp.getEstadoTributario()).isEqualTo(EstadoTributarioGRE.EMITIDA);
        // Pero no se registra ningún movimiento de inventario
        verify(movimientoService, never()).crearMovimiento(any());
    }

    // ── T-REC-4: Factor de presentación ──────────────────────────────────────

    @Test
    @DisplayName("T-REC-4: cantidadBase = cantidad × factor (presentación caja×12)")
    void respetaFactorPresentacion() {
        GuiaRemision guia = greEnError(99L);
        // Cambiar el item: cantidad=2 con presentacionId=5 (factor=12) → base=24
        GuiaRemisionItem item = guia.getItems().get(0);
        item.setCantidad(new BigDecimal("2"));
        item.setPresentacionId(5L);

        ProductoPresentacion presentacion = new ProductoPresentacion();
        presentacion.setId(5L);
        presentacion.setTenantId(TENANT);
        presentacion.setFactor(12);
        presentacion.setProducto(item.getProducto());

        when(guiaRepo.findByIdAndTenantIdForUpdate(99L, TENANT)).thenReturn(Optional.of(guia));
        when(movimientoRepo.existsByReferenciaAndTipoAndTenantId("GRE-99", "SALIDA", TENANT)).thenReturn(false);
        when(presentacionRepo.findById(5L)).thenReturn(Optional.of(presentacion));
        when(guiaRepo.save(any())).thenAnswer(inv -> inv.getArgument(0));

        service.reconciliarGREAceptada(99L, requestValido());

        ArgumentCaptor<MovimientoInventario> captor = ArgumentCaptor.forClass(MovimientoInventario.class);
        verify(movimientoService).crearMovimiento(captor.capture());
        // 2 cajas × 12 unidades/caja = 24 unidades base
        assertThat(captor.getValue().getCantidad()).isEqualTo(24);
    }

    // ── T-REC-5: Solo ERROR puede reconciliarse ───────────────────────────────

    @Test
    @DisplayName("T-REC-5a: GRE ya EMITIDA → retorna sin modificar (idempotente)")
    void greYaEmitidaRetornaDirecto() {
        GuiaRemision guia = greEnError(99L);
        guia.setEstadoTributario(EstadoTributarioGRE.EMITIDA);
        when(guiaRepo.findByIdAndTenantIdForUpdate(99L, TENANT)).thenReturn(Optional.of(guia));

        GuiaRemisionResponse resp = service.reconciliarGREAceptada(99L, requestValido());

        assertThat(resp.getEstadoTributario()).isEqualTo(EstadoTributarioGRE.EMITIDA);
        verify(guiaRepo, never()).save(any());
        verify(movimientoService, never()).crearMovimiento(any());
    }

    @Test
    @DisplayName("T-REC-5b: GRE en BORRADOR → BadRequestException (no es un error Lucode)")
    void greBorradorLanzaExcepcion() {
        GuiaRemision guia = greEnError(99L);
        guia.setEstadoTributario(EstadoTributarioGRE.BORRADOR);
        when(guiaRepo.findByIdAndTenantIdForUpdate(99L, TENANT)).thenReturn(Optional.of(guia));

        assertThatThrownBy(() -> service.reconciliarGREAceptada(99L, requestValido()))
                .isInstanceOf(BadRequestException.class)
                .hasMessageContaining("estado ERROR");
    }

    // ── T-REC-6: Cross-tenant bloqueado ──────────────────────────────────────

    @Test
    @DisplayName("T-REC-6: GRE no encontrada para este tenant → ResourceNotFoundException")
    void crossTenantBloqueado() {
        when(guiaRepo.findByIdAndTenantIdForUpdate(99L, TENANT)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> service.reconciliarGREAceptada(99L, requestValido()))
                .isInstanceOf(ResourceNotFoundException.class);

        verify(movimientoService, never()).crearMovimiento(any());
        verify(guiaRepo, never()).save(any());
    }

    // ── T-REC-7: Trazabilidad tributaria ─────────────────────────────────────

    @Test
    @DisplayName("T-REC-7: Se registra evento RECONCILIACION_F08 con hash en la nota")
    void registraEventoAuditoriaF08() {
        GuiaRemision guia = greEnError(99L);
        when(guiaRepo.findByIdAndTenantIdForUpdate(99L, TENANT)).thenReturn(Optional.of(guia));
        when(movimientoRepo.existsByReferenciaAndTipoAndTenantId("GRE-99", "SALIDA", TENANT)).thenReturn(false);
        when(guiaRepo.save(any())).thenAnswer(inv -> inv.getArgument(0));

        service.reconciliarGREAceptada(99L, requestValido());

        ArgumentCaptor<GuiaRemisionEvento> captor = ArgumentCaptor.forClass(GuiaRemisionEvento.class);
        verify(eventoRepo).save(captor.capture());
        GuiaRemisionEvento evento = captor.getValue();
        assertThat(evento.getTipoEvento()).isEqualTo(TipoEventoGRE.RECONCILIACION_F08);
        assertThat(evento.getDescripcion()).contains("hash-abc123");
        assertThat(evento.getDescripcion()).contains("pdf.a4");
        assertThat(evento.getTenantId()).isEqualTo(TENANT);
        assertThat(evento.getUsuario()).isEqualTo(USER_ID.toString());
    }

    // ── T-REC-8: Transaccionalidad ────────────────────────────────────────────

    @Test
    @DisplayName("T-REC-8: Si guiaRepo.save lanza excepción, eventoRepo.save no se ejecuta")
    void saveFailurePreventaEventoRegistro() {
        GuiaRemision guia = greEnError(99L);
        when(guiaRepo.findByIdAndTenantIdForUpdate(99L, TENANT)).thenReturn(Optional.of(guia));
        when(movimientoRepo.existsByReferenciaAndTipoAndTenantId("GRE-99", "SALIDA", TENANT)).thenReturn(false);
        when(guiaRepo.save(any())).thenThrow(new RuntimeException("DB error simulado"));

        assertThatThrownBy(() -> service.reconciliarGREAceptada(99L, requestValido()))
                .isInstanceOf(RuntimeException.class)
                .hasMessageContaining("DB error simulado");

        // Con @Transactional real el rollback lo gestiona Spring, pero la secuencia
        // de llamadas garantiza que eventoRepo nunca se llama si save falla antes.
        verify(eventoRepo, never()).save(any());
    }
}
