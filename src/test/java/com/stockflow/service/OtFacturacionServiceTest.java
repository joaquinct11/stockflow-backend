package com.stockflow.service;

import com.stockflow.dto.FacturarOtRequestDTO;
import com.stockflow.dto.OrdenTrabajoDTO;
import com.stockflow.entity.Caja;
import com.stockflow.entity.Cliente;
import com.stockflow.entity.OrdenTrabajo;
import com.stockflow.entity.OtConsumoMaterial;
import com.stockflow.entity.OtHistorialEstado;
import com.stockflow.entity.Producto;
import com.stockflow.entity.Sucursal;
import com.stockflow.entity.Usuario;
import com.stockflow.entity.Venta;
import com.stockflow.exception.BadRequestException;
import com.stockflow.exception.ResourceNotFoundException;
import com.stockflow.mapper.OrdenTrabajoMapper;
import com.stockflow.repository.CajaRepository;
import com.stockflow.repository.ClienteRepository;
import com.stockflow.repository.EquipoClienteRepository;
import com.stockflow.repository.OrdenTrabajoRepository;
import com.stockflow.repository.OtConsumoClasificacionRepository;
import com.stockflow.repository.OtConsumoDevolucionRepository;
import com.stockflow.repository.OtConsumoMaterialRepository;
import com.stockflow.repository.OtHistorialEstadoRepository;
import com.stockflow.repository.ProductoRepository;
import com.stockflow.repository.SucursalRepository;
import com.stockflow.repository.UsuarioRepository;
import com.stockflow.repository.UsuarioTenantRepository;
import com.stockflow.service.impl.OrdenTrabajoServiceImpl;
import com.stockflow.util.TenantContext;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.MockedStatic;
import org.mockito.junit.jupiter.MockitoExtension;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.Collections;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mockStatic;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class OtFacturacionServiceTest {

    private static final String TENANT    = "tenant-test";
    private static final Long   OT_ID     = 1L;
    private static final Long   USER_ID   = 99L;
    private static final Long   VENDEDOR_ID = 55L;
    private static final Long   PROD_ID   = 10L;
    private static final Long   CONSUMO_ID = 20L;
    private static final Long   VENTA_ID  = 500L;
    private static final Long   PROD_SERV_ID   = 11L;
    private static final Long   CONSUMO_SERV_ID = 21L;

    @Mock private OrdenTrabajoRepository       otRepository;
    @Mock private OtHistorialEstadoRepository  historialRepository;
    @Mock private EquipoClienteRepository      equipoRepository;
    @Mock private ClienteRepository            clienteRepository;
    @Mock private SucursalRepository           sucursalRepository;
    @Mock private UsuarioRepository            usuarioRepository;
    @Mock private OtNumeracionService          otNumeracionService;
    @Mock private UsuarioTenantRepository      usuarioTenantRepository;
    @Mock private OtConsumoMaterialRepository  consumoRepository;
    @Mock private OtConsumoDevolucionRepository devolucionRepository;
    @Mock private OtConsumoClasificacionRepository clasificacionRepository;
    @Mock private ProductoRepository           productoRepository;
    @Mock private OrdenTrabajoMapper           mapper;
    @Mock private TenantService                tenantService;
    @Mock private VentaService                 ventaService;
    @Mock private CajaRepository               cajaRepository;

    @InjectMocks
    private OrdenTrabajoServiceImpl service;

    private MockedStatic<TenantContext> tenantContextMock;

    @BeforeEach
    void setUp() {
        tenantContextMock = mockStatic(TenantContext.class);
        tenantContextMock.when(TenantContext::getCurrentTenant).thenReturn(TENANT);
        tenantContextMock.when(TenantContext::getCurrentUserId).thenReturn(USER_ID);
    }

    @AfterEach
    void tearDown() {
        tenantContextMock.close();
    }

    // ── Helpers ──────────────────────────────────────────────────────────────

    private OrdenTrabajo otCerradaTecn() {
        Sucursal sucursal = new Sucursal();
        sucursal.setId(7L);
        Cliente cliente = new Cliente();
        cliente.setId(3L);

        return OrdenTrabajo.builder()
                .id(OT_ID)
                .tenantId(TENANT)
                .estado("CERRADA_TECN")
                .ventaId(null)
                .sucursal(sucursal)
                .cliente(cliente)
                .build();
    }

    private OtConsumoMaterial consumoFisico() {
        return OtConsumoMaterial.builder()
                .id(CONSUMO_ID)
                .tenantId(TENANT)
                .productoId(PROD_ID)
                .cantidadBase(2)
                .precioUnitario(new BigDecimal("50.00"))
                .factorPresentacion(1)
                .cantidadPresentacion(2)
                .createdAt(LocalDateTime.now())
                .build();
    }

    private Producto producto(String tipo) {
        Producto p = new Producto();
        p.setId(PROD_ID);
        p.setTipo(tipo);
        p.setNombre("Producto test");
        return p;
    }

    private Usuario vendedor() {
        Usuario u = new Usuario();
        u.setId(VENDEDOR_ID);
        u.setActivo(true);
        return u;
    }

    private OtConsumoMaterial consumoServicio() {
        return OtConsumoMaterial.builder()
                .id(CONSUMO_SERV_ID)
                .tenantId(TENANT)
                .productoId(PROD_SERV_ID)
                .cantidadBase(1)
                .precioUnitario(new BigDecimal("80.00"))
                .factorPresentacion(1)
                .cantidadPresentacion(1)
                .createdAt(LocalDateTime.now())
                .build();
    }

    private Venta ventaCreada() {
        return Venta.builder()
                .id(VENTA_ID)
                .total(new BigDecimal("120.00"))
                .estado("COMPLETADA")
                .tenantId(TENANT)
                .build();
    }

    private FacturarOtRequestDTO req() {
        return FacturarOtRequestDTO.builder()
                .vendedorId(VENDEDOR_ID)
                .metodoPago("EFECTIVO")
                .montoManoObra(new BigDecimal("20.00"))
                .build();
    }

    private void stubBase(OrdenTrabajo ot) {
        when(otRepository.findByIdAndTenantIdForUpdate(OT_ID, TENANT)).thenReturn(Optional.of(ot));
        when(usuarioRepository.findById(VENDEDOR_ID)).thenReturn(Optional.of(vendedor()));
        when(usuarioTenantRepository.existsByUsuarioIdAndTenantIdAndActivoTrue(VENDEDOR_ID, TENANT)).thenReturn(true);
        when(consumoRepository.findByOrdenTrabajo_IdAndTenantIdOrderByCreatedAtAsc(OT_ID, TENANT))
                .thenReturn(List.of(consumoFisico()));
        when(productoRepository.findAllById(any())).thenReturn(List.of(producto("PRODUCTO")));
        when(ventaService.crearVenta(any())).thenReturn(ventaCreada());
        when(otRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));
        when(historialRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));
        when(historialRepository.findByOrdenTrabajo_IdAndTenantIdOrderByCreatedAtAsc(OT_ID, TENANT))
                .thenReturn(Collections.emptyList());
        when(mapper.toDTO(any(OrdenTrabajo.class))).thenReturn(new OrdenTrabajoDTO());
    }

    // ── Tests ────────────────────────────────────────────────────────────────

    /** FT1: factura correctamente una OT en estado CERRADA_TECN */
    @Test
    void ft1_facturaOtCerradaTecn() {
        OrdenTrabajo ot = otCerradaTecn();
        stubBase(ot);

        OrdenTrabajoDTO result = service.facturarOt(OT_ID, req());

        assertThat(result).isNotNull();
        verify(ventaService).crearVenta(any());
        ArgumentCaptor<OrdenTrabajo> cap = ArgumentCaptor.forClass(OrdenTrabajo.class);
        verify(otRepository).save(cap.capture());
        assertThat(cap.getValue().getEstado()).isEqualTo("FACTURADA");
        assertThat(cap.getValue().getVentaId()).isEqualTo(VENTA_ID);
    }

    /** FT2: rechaza si estado != CERRADA_TECN */
    @Test
    void ft2_rechazaEstadoDistintoDeCerradaTecn() {
        OrdenTrabajo ot = otCerradaTecn();
        ot.setEstado("EN_PROCESO");
        when(otRepository.findByIdAndTenantIdForUpdate(OT_ID, TENANT)).thenReturn(Optional.of(ot));

        assertThatThrownBy(() -> service.facturarOt(OT_ID, req()))
                .isInstanceOf(BadRequestException.class)
                .hasMessageContaining("CERRADA_TECN");
    }

    /** FT3: rechaza si ventaId != null (ya facturada) */
    @Test
    void ft3_rechazaOtYaFacturada() {
        OrdenTrabajo ot = otCerradaTecn();
        ot.setVentaId(777L);
        when(otRepository.findByIdAndTenantIdForUpdate(OT_ID, TENANT)).thenReturn(Optional.of(ot));
        Venta ventaActiva = Venta.builder().id(777L).tenantId(TENANT).estado("COMPLETADA").build();
        when(ventaService.getVentaById(777L, TENANT)).thenReturn(ventaActiva);

        assertThatThrownBy(() -> service.facturarOt(OT_ID, req()))
                .isInstanceOf(BadRequestException.class)
                .hasMessageContaining("ya fue facturada");
    }

    /** FT4: productoRepository.save() NO se llama (sin descuento de inventario) */
    @Test
    void ft4_noDescuentaInventario() {
        OrdenTrabajo ot = otCerradaTecn();
        stubBase(ot);

        service.facturarOt(OT_ID, req());

        verify(productoRepository, never()).save(any());
    }

    /** FT5: total = sum(consumos) + montoManoObra */
    @Test
    void ft5_totalCorrecto() {
        OrdenTrabajo ot = otCerradaTecn();
        stubBase(ot);
        // consumo: 2 unidades × S/50 = S/100; manoObra = S/20 → total = S/120
        ArgumentCaptor<Venta> capVenta = ArgumentCaptor.forClass(Venta.class);

        service.facturarOt(OT_ID, req());

        verify(ventaService).crearVenta(capVenta.capture());
        assertThat(capVenta.getValue().getTotal()).isEqualByComparingTo("120.00");
    }

    /** FT6: OT queda en estado FACTURADA */
    @Test
    void ft6_otQuedaFacturada() {
        OrdenTrabajo ot = otCerradaTecn();
        stubBase(ot);
        ArgumentCaptor<OrdenTrabajo> cap = ArgumentCaptor.forClass(OrdenTrabajo.class);

        service.facturarOt(OT_ID, req());

        verify(otRepository).save(cap.capture());
        assertThat(cap.getValue().getEstado()).isEqualTo("FACTURADA");
    }

    /** FT7: OT.ventaId apunta a la Venta creada */
    @Test
    void ft7_otVentaIdVinculado() {
        OrdenTrabajo ot = otCerradaTecn();
        stubBase(ot);
        ArgumentCaptor<OrdenTrabajo> cap = ArgumentCaptor.forClass(OrdenTrabajo.class);

        service.facturarOt(OT_ID, req());

        verify(otRepository).save(cap.capture());
        assertThat(cap.getValue().getVentaId()).isEqualTo(VENTA_ID);
    }

    /** FT8: consumos tipo SERVICIO se incluyen en DetalleVenta (no afectan inventario) */
    @Test
    void ft8_consumoServicioIncluidoEnVenta() {
        OrdenTrabajo ot = otCerradaTecn();
        when(otRepository.findByIdAndTenantIdForUpdate(OT_ID, TENANT)).thenReturn(Optional.of(ot));
        when(usuarioRepository.findById(VENDEDOR_ID)).thenReturn(Optional.of(vendedor()));
        when(usuarioTenantRepository.existsByUsuarioIdAndTenantIdAndActivoTrue(VENDEDOR_ID, TENANT)).thenReturn(true);
        when(consumoRepository.findByOrdenTrabajo_IdAndTenantIdOrderByCreatedAtAsc(OT_ID, TENANT))
                .thenReturn(List.of(consumoFisico()));
        // El producto es SERVICIO
        when(productoRepository.findAllById(any())).thenReturn(List.of(producto("SERVICIO")));
        when(ventaService.crearVenta(any())).thenReturn(ventaCreada());
        when(otRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));
        when(historialRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));
        when(historialRepository.findByOrdenTrabajo_IdAndTenantIdOrderByCreatedAtAsc(OT_ID, TENANT))
                .thenReturn(Collections.emptyList());
        when(mapper.toDTO(any(OrdenTrabajo.class))).thenReturn(new OrdenTrabajoDTO());

        ArgumentCaptor<Venta> capVenta = ArgumentCaptor.forClass(Venta.class);
        service.facturarOt(OT_ID, req());

        verify(ventaService).crearVenta(capVenta.capture());
        // El SERVICIO tiene precio 50 × 2 = 100; manoObra 20 → total 120
        assertThat(capVenta.getValue().getTotal()).isEqualByComparingTo("120.00");
    }

    /** FT9: montoManoObra null se trata como cero */
    @Test
    void ft9_montoManoObraNullEsCero() {
        OrdenTrabajo ot = otCerradaTecn();
        stubBase(ot);
        FacturarOtRequestDTO reqSinManoObra = FacturarOtRequestDTO.builder()
                .vendedorId(VENDEDOR_ID)
                .metodoPago("EFECTIVO")
                .montoManoObra(null)
                .build();
        ArgumentCaptor<Venta> capVenta = ArgumentCaptor.forClass(Venta.class);

        service.facturarOt(OT_ID, reqSinManoObra);

        verify(ventaService).crearVenta(capVenta.capture());
        // consumo: 100; manoObra: 0 → total 100
        assertThat(capVenta.getValue().getTotal()).isEqualByComparingTo("100.00");
        ArgumentCaptor<OrdenTrabajo> capOt = ArgumentCaptor.forClass(OrdenTrabajo.class);
        verify(otRepository).save(capOt.capture());
        assertThat(capOt.getValue().getMontoManoObra()).isEqualByComparingTo(BigDecimal.ZERO);
    }

    /** FT10: rechaza vendedorId inexistente */
    @Test
    void ft10_rechazaVendedorInexistente() {
        OrdenTrabajo ot = otCerradaTecn();
        when(otRepository.findByIdAndTenantIdForUpdate(OT_ID, TENANT)).thenReturn(Optional.of(ot));
        when(usuarioRepository.findById(VENDEDOR_ID)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> service.facturarOt(OT_ID, req()))
                .isInstanceOf(ResourceNotFoundException.class)
                .hasMessageContaining("Vendedor no encontrado");
    }

    /**
     * FT11: idempotencia — simula que la OT ya tiene ventaId (resultado del lock pesimista
     * en la primera transacción concurrente). La segunda solicitud debe ser rechazada.
     * Nota: la protección de concurrencia real viene del bloqueo pesimista a nivel de DB;
     * este test verifica la guarda de idempotencia que opera sobre el resultado del lock.
     */
    @Test
    void ft11_idempotenciaDosFacturacionesConcurrentes() {
        OrdenTrabajo ot = otCerradaTecn();
        ot.setVentaId(VENTA_ID); // simula que la primera TX ya completó
        when(otRepository.findByIdAndTenantIdForUpdate(OT_ID, TENANT)).thenReturn(Optional.of(ot));
        when(ventaService.getVentaById(VENTA_ID, TENANT)).thenReturn(ventaCreada());

        assertThatThrownBy(() -> service.facturarOt(OT_ID, req()))
                .isInstanceOf(BadRequestException.class)
                .hasMessageContaining("ya fue facturada");

        verify(ventaService, never()).crearVenta(any());
    }

    /**
     * FT12: si falla otRepository.save() (actualización de OT), la excepción se propaga.
     * La reversión de la venta ya creada es responsabilidad de @Transactional (Spring):
     * al lanzarse una RuntimeException, la TX completa se revierte incluyendo crearVenta().
     */
    @Test
    void ft12_siGuardaOtFallaExcepcionPropaga() {
        OrdenTrabajo ot = otCerradaTecn();
        when(otRepository.findByIdAndTenantIdForUpdate(OT_ID, TENANT)).thenReturn(Optional.of(ot));
        when(usuarioRepository.findById(VENDEDOR_ID)).thenReturn(Optional.of(vendedor()));
        when(usuarioTenantRepository.existsByUsuarioIdAndTenantIdAndActivoTrue(VENDEDOR_ID, TENANT)).thenReturn(true);
        when(consumoRepository.findByOrdenTrabajo_IdAndTenantIdOrderByCreatedAtAsc(OT_ID, TENANT))
                .thenReturn(Collections.emptyList());
        when(ventaService.crearVenta(any())).thenReturn(ventaCreada());
        when(otRepository.save(any())).thenThrow(new RuntimeException("DB error"));

        assertThatThrownBy(() -> service.facturarOt(OT_ID, req()))
                .isInstanceOf(RuntimeException.class)
                .hasMessageContaining("DB error");
    }

    // ── Nuevos tests: materiales + servicios ─────────────────────────────────

    /** FT13: OT con solo materiales — total correcto */
    @Test
    void ft13_soloMateriales_totalCorrecto() {
        OrdenTrabajo ot = otCerradaTecn();
        stubBase(ot); // stubBase usa producto("PRODUTO") — tipo físico
        ArgumentCaptor<Venta> cap = ArgumentCaptor.forClass(Venta.class);

        service.facturarOt(OT_ID, req());

        verify(ventaService).crearVenta(cap.capture());
        // consumo físico: 2 × 50 = 100; manoObra 20 → total 120
        assertThat(cap.getValue().getTotal()).isEqualByComparingTo("120.00");
        // 1 DetalleVenta del material
        assertThat(cap.getValue().getDetalles()).hasSize(1);
    }

    /** FT14: OT con solo servicios — total correcto */
    @Test
    void ft14_soloServicios_totalCorrecto() {
        OrdenTrabajo ot = otCerradaTecn();

        Producto prodServ = new Producto();
        prodServ.setId(PROD_SERV_ID);
        prodServ.setTipo("SERVICIO");
        prodServ.setNombre("Servicio test");

        when(otRepository.findByIdAndTenantIdForUpdate(OT_ID, TENANT)).thenReturn(Optional.of(ot));
        when(usuarioRepository.findById(VENDEDOR_ID)).thenReturn(Optional.of(vendedor()));
        when(usuarioTenantRepository.existsByUsuarioIdAndTenantIdAndActivoTrue(VENDEDOR_ID, TENANT)).thenReturn(true);
        when(consumoRepository.findByOrdenTrabajo_IdAndTenantIdOrderByCreatedAtAsc(OT_ID, TENANT))
                .thenReturn(List.of(consumoServicio()));
        when(productoRepository.findAllById(any())).thenReturn(List.of(prodServ));
        when(ventaService.crearVenta(any())).thenReturn(ventaCreada());
        when(otRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));
        when(historialRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));
        when(historialRepository.findByOrdenTrabajo_IdAndTenantIdOrderByCreatedAtAsc(OT_ID, TENANT))
                .thenReturn(Collections.emptyList());
        when(mapper.toDTO(any(OrdenTrabajo.class))).thenReturn(new OrdenTrabajoDTO());

        ArgumentCaptor<Venta> cap = ArgumentCaptor.forClass(Venta.class);
        service.facturarOt(OT_ID, req());

        verify(ventaService).crearVenta(cap.capture());
        // consumo servicio: 1 × 80 = 80; manoObra 20 → total 100
        assertThat(cap.getValue().getTotal()).isEqualByComparingTo("100.00");
    }

    /** FT15: OT mixta (material + servicio) — total = mat + serv + manoObra */
    @Test
    void ft15_mixtoMateriakesYServicios_totalCorrecto() {
        OrdenTrabajo ot = otCerradaTecn();

        Producto prodFisico = producto("PRODUTO");
        Producto prodServ   = new Producto();
        prodServ.setId(PROD_SERV_ID);
        prodServ.setTipo("SERVICIO");
        prodServ.setNombre("Servicio test");

        when(otRepository.findByIdAndTenantIdForUpdate(OT_ID, TENANT)).thenReturn(Optional.of(ot));
        when(usuarioRepository.findById(VENDEDOR_ID)).thenReturn(Optional.of(vendedor()));
        when(usuarioTenantRepository.existsByUsuarioIdAndTenantIdAndActivoTrue(VENDEDOR_ID, TENANT)).thenReturn(true);
        when(consumoRepository.findByOrdenTrabajo_IdAndTenantIdOrderByCreatedAtAsc(OT_ID, TENANT))
                .thenReturn(List.of(consumoFisico(), consumoServicio()));
        when(productoRepository.findAllById(any())).thenReturn(List.of(prodFisico, prodServ));
        when(ventaService.crearVenta(any())).thenReturn(ventaCreada());
        when(otRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));
        when(historialRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));
        when(historialRepository.findByOrdenTrabajo_IdAndTenantIdOrderByCreatedAtAsc(OT_ID, TENANT))
                .thenReturn(Collections.emptyList());
        when(mapper.toDTO(any(OrdenTrabajo.class))).thenReturn(new OrdenTrabajoDTO());

        ArgumentCaptor<Venta> cap = ArgumentCaptor.forClass(Venta.class);
        service.facturarOt(OT_ID, req());

        verify(ventaService).crearVenta(cap.capture());
        // físico: 2×50=100; servicio: 1×80=80; manoObra: 20 → total 200
        assertThat(cap.getValue().getTotal()).isEqualByComparingTo("200.00");
        assertThat(cap.getValue().getDetalles()).hasSize(2);
    }

    /** FT16: servicio genera DetalleVenta (incluido en la venta) */
    @Test
    void ft16_servicioGeneraDetalleVenta() {
        OrdenTrabajo ot = otCerradaTecn();

        Producto prodServ = new Producto();
        prodServ.setId(PROD_SERV_ID);
        prodServ.setTipo("SERVICIO");
        prodServ.setNombre("Diagnóstico");

        when(otRepository.findByIdAndTenantIdForUpdate(OT_ID, TENANT)).thenReturn(Optional.of(ot));
        when(usuarioRepository.findById(VENDEDOR_ID)).thenReturn(Optional.of(vendedor()));
        when(usuarioTenantRepository.existsByUsuarioIdAndTenantIdAndActivoTrue(VENDEDOR_ID, TENANT)).thenReturn(true);
        when(consumoRepository.findByOrdenTrabajo_IdAndTenantIdOrderByCreatedAtAsc(OT_ID, TENANT))
                .thenReturn(List.of(consumoServicio()));
        when(productoRepository.findAllById(any())).thenReturn(List.of(prodServ));
        when(ventaService.crearVenta(any())).thenReturn(ventaCreada());
        when(otRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));
        when(historialRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));
        when(historialRepository.findByOrdenTrabajo_IdAndTenantIdOrderByCreatedAtAsc(OT_ID, TENANT))
                .thenReturn(Collections.emptyList());
        when(mapper.toDTO(any(OrdenTrabajo.class))).thenReturn(new OrdenTrabajoDTO());

        ArgumentCaptor<Venta> cap = ArgumentCaptor.forClass(Venta.class);
        service.facturarOt(OT_ID, req());

        verify(ventaService).crearVenta(cap.capture());
        assertThat(cap.getValue().getDetalles()).hasSize(1);
        assertThat(cap.getValue().getDetalles().get(0).getProducto().getTipo()).isEqualTo("SERVICIO");
    }

    /** FT17: material genera DetalleVenta */
    @Test
    void ft17_materialGeneraDetalleVenta() {
        OrdenTrabajo ot = otCerradaTecn();
        stubBase(ot);
        ArgumentCaptor<Venta> cap = ArgumentCaptor.forClass(Venta.class);

        service.facturarOt(OT_ID, req());

        verify(ventaService).crearVenta(cap.capture());
        assertThat(cap.getValue().getDetalles()).hasSize(1);
        assertThat(cap.getValue().getDetalles().get(0).getProducto().getTipo()).isNotEqualTo("SERVICIO");
    }

    /** FT18: servicio no descuenta inventario (productoRepository.save() nunca llamado) */
    @Test
    void ft18_servicioNoDescuentaInventario() {
        OrdenTrabajo ot = otCerradaTecn();

        Producto prodServ = new Producto();
        prodServ.setId(PROD_SERV_ID);
        prodServ.setTipo("SERVICIO");
        prodServ.setNombre("Servicio test");

        when(otRepository.findByIdAndTenantIdForUpdate(OT_ID, TENANT)).thenReturn(Optional.of(ot));
        when(usuarioRepository.findById(VENDEDOR_ID)).thenReturn(Optional.of(vendedor()));
        when(usuarioTenantRepository.existsByUsuarioIdAndTenantIdAndActivoTrue(VENDEDOR_ID, TENANT)).thenReturn(true);
        when(consumoRepository.findByOrdenTrabajo_IdAndTenantIdOrderByCreatedAtAsc(OT_ID, TENANT))
                .thenReturn(List.of(consumoServicio()));
        when(productoRepository.findAllById(any())).thenReturn(List.of(prodServ));
        when(ventaService.crearVenta(any())).thenReturn(ventaCreada());
        when(otRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));
        when(historialRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));
        when(historialRepository.findByOrdenTrabajo_IdAndTenantIdOrderByCreatedAtAsc(OT_ID, TENANT))
                .thenReturn(Collections.emptyList());
        when(mapper.toDTO(any(OrdenTrabajo.class))).thenReturn(new OrdenTrabajoDTO());

        service.facturarOt(OT_ID, req());

        verify(productoRepository, never()).save(any());
    }

    /** FT19: material no genera segundo descuento de inventario */
    @Test
    void ft19_materialNoDescuentaInventarioSegundaVez() {
        OrdenTrabajo ot = otCerradaTecn();
        stubBase(ot);

        service.facturarOt(OT_ID, req());

        verify(productoRepository, never()).save(any());
    }

    /** FT20: OT no puede facturarse dos veces — idempotencia por ventaId */
    @Test
    void ft20_noPermiteDobleFacturacion() {
        OrdenTrabajo ot = otCerradaTecn();
        ot.setVentaId(VENTA_ID);
        when(otRepository.findByIdAndTenantIdForUpdate(OT_ID, TENANT)).thenReturn(Optional.of(ot));
        when(ventaService.getVentaById(VENTA_ID, TENANT)).thenReturn(ventaCreada());

        assertThatThrownBy(() -> service.facturarOt(OT_ID, req()))
                .isInstanceOf(BadRequestException.class)
                .hasMessageContaining("ya fue facturada");
        verify(ventaService, never()).crearVenta(any());
    }

    /** FT21: fallo al crear la venta propaga excepción y no cambia estado OT */
    @Test
    void ft21_falloVentaNoCambiaEstadoOt() {
        OrdenTrabajo ot = otCerradaTecn();
        when(otRepository.findByIdAndTenantIdForUpdate(OT_ID, TENANT)).thenReturn(Optional.of(ot));
        when(usuarioRepository.findById(VENDEDOR_ID)).thenReturn(Optional.of(vendedor()));
        when(usuarioTenantRepository.existsByUsuarioIdAndTenantIdAndActivoTrue(VENDEDOR_ID, TENANT)).thenReturn(true);
        when(consumoRepository.findByOrdenTrabajo_IdAndTenantIdOrderByCreatedAtAsc(OT_ID, TENANT))
                .thenReturn(Collections.emptyList());
        when(ventaService.crearVenta(any())).thenThrow(new RuntimeException("venta error"));

        assertThatThrownBy(() -> service.facturarOt(OT_ID, req()))
                .isInstanceOf(RuntimeException.class)
                .hasMessageContaining("venta error");

        // OT nunca se guardó con el nuevo estado
        verify(otRepository, never()).save(any());
        assertThat(ot.getEstado()).isEqualTo("CERRADA_TECN");
    }

    /** FT22: OT mixta sin mano de obra — total = mat + serv */
    @Test
    void ft22_mixtoSinManoObra_totalMatServ() {
        OrdenTrabajo ot = otCerradaTecn();

        Producto prodFisico = producto("PRODUTO");
        Producto prodServ   = new Producto();
        prodServ.setId(PROD_SERV_ID);
        prodServ.setTipo("SERVICIO");
        prodServ.setNombre("Servicio test");

        FacturarOtRequestDTO reqSinMano = FacturarOtRequestDTO.builder()
                .vendedorId(VENDEDOR_ID)
                .metodoPago("EFECTIVO")
                .montoManoObra(null)
                .build();

        when(otRepository.findByIdAndTenantIdForUpdate(OT_ID, TENANT)).thenReturn(Optional.of(ot));
        when(usuarioRepository.findById(VENDEDOR_ID)).thenReturn(Optional.of(vendedor()));
        when(usuarioTenantRepository.existsByUsuarioIdAndTenantIdAndActivoTrue(VENDEDOR_ID, TENANT)).thenReturn(true);
        when(consumoRepository.findByOrdenTrabajo_IdAndTenantIdOrderByCreatedAtAsc(OT_ID, TENANT))
                .thenReturn(List.of(consumoFisico(), consumoServicio()));
        when(productoRepository.findAllById(any())).thenReturn(List.of(prodFisico, prodServ));
        when(ventaService.crearVenta(any())).thenReturn(ventaCreada());
        when(otRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));
        when(historialRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));
        when(historialRepository.findByOrdenTrabajo_IdAndTenantIdOrderByCreatedAtAsc(OT_ID, TENANT))
                .thenReturn(Collections.emptyList());
        when(mapper.toDTO(any(OrdenTrabajo.class))).thenReturn(new OrdenTrabajoDTO());

        ArgumentCaptor<Venta> cap = ArgumentCaptor.forClass(Venta.class);
        service.facturarOt(OT_ID, reqSinMano);

        verify(ventaService).crearVenta(cap.capture());
        // físico: 2×50=100; servicio: 1×80=80; manoObra: 0 → total 180
        assertThat(cap.getValue().getTotal()).isEqualByComparingTo("180.00");
    }
}
