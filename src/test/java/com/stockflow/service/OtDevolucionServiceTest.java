package com.stockflow.service;

import com.stockflow.dto.OtConsumoDevolucionDTO;
import com.stockflow.dto.RegistrarDevolucionOtRequestDTO;
import com.stockflow.entity.MovimientoInventario;
import com.stockflow.entity.OrdenTrabajo;
import com.stockflow.entity.OtConsumoDevolucion;
import com.stockflow.entity.OtConsumoLote;
import com.stockflow.entity.OtConsumoMaterial;
import com.stockflow.entity.Producto;
import com.stockflow.entity.Sucursal;
import com.stockflow.entity.Usuario;
import com.stockflow.exception.BadRequestException;
import com.stockflow.exception.ResourceNotFoundException;
import com.stockflow.repository.MovimientoInventarioRepository;
import com.stockflow.repository.OrdenTrabajoRepository;
import com.stockflow.repository.OtConsumoClasificacionRepository;
import com.stockflow.repository.OtConsumoDevolucionLoteRepository;
import com.stockflow.repository.OtConsumoDevolucionRepository;
import com.stockflow.repository.OtConsumoLoteRepository;
import com.stockflow.repository.OtConsumoMaterialRepository;
import com.stockflow.repository.ProductoRepository;
import com.stockflow.repository.ProductoStockSucursalRepository;
import com.stockflow.repository.StockLoteRepository;
import com.stockflow.repository.UsuarioRepository;
import com.stockflow.service.impl.OtDevolucionServiceImpl;
import com.stockflow.util.TenantContext;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.MockedStatic;
import org.mockito.junit.jupiter.MockitoExtension;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mockStatic;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class OtDevolucionServiceTest {

    private static final String TENANT = "tenant-dev";
    private static final Long   OT_ID    = 1L;
    private static final Long   CONSUMO_ID = 10L;
    private static final Long   USER_ID  = 99L;

    @Mock private OrdenTrabajoRepository          otRepository;
    @Mock private OtConsumoMaterialRepository     consumoRepository;
    @Mock private OtConsumoLoteRepository         consumoLoteRepository;
    @Mock private OtConsumoDevolucionRepository   devolucionRepository;
    @Mock private OtConsumoDevolucionLoteRepository devolucionLoteRepository;
    @Mock private OtConsumoClasificacionRepository clasificacionRepository;
    @Mock private ProductoRepository              productoRepository;
    @Mock private ProductoStockSucursalRepository stockSucursalRepository;
    @Mock private StockLoteRepository             stockLoteRepository;
    @Mock private MovimientoInventarioRepository  movimientoRepository;
    @Mock private UsuarioRepository               usuarioRepository;
    @Mock private TenantService                   tenantService;

    @InjectMocks
    private OtDevolucionServiceImpl service;

    private MockedStatic<TenantContext> tenantContextMock;

    @BeforeEach
    void setUp() {
        tenantContextMock = mockStatic(TenantContext.class);
        tenantContextMock.when(TenantContext::getCurrentTenant).thenReturn(TENANT);
        tenantContextMock.when(TenantContext::getCurrentUserId).thenReturn(USER_ID);
        SecurityContextHolder.getContext().setAuthentication(
                new UsernamePasswordAuthenticationToken(
                        "admin@test.com", null,
                        List.of(new SimpleGrantedAuthority("PERM_VER_TODAS_OT"))
                )
        );
    }

    @AfterEach
    void tearDown() {
        tenantContextMock.close();
        SecurityContextHolder.clearContext();
    }

    // ── RD-1: OT no encontrada → ResourceNotFoundException ───────────────────

    @Test
    void registrarDevolucion_otNoEncontrada_lanzaNotFoundException() {
        when(otRepository.findByIdAndTenantIdForUpdate(OT_ID, TENANT)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> service.registrarDevolucion(OT_ID, CONSUMO_ID, buildReq(5, null)))
                .isInstanceOf(ResourceNotFoundException.class)
                .hasMessageContaining("Orden de trabajo no encontrada");
    }

    // ── RD-2: Estado inválido → BadRequestException ───────────────────────────

    @Test
    void registrarDevolucion_estadoInvalido_lanzaBadRequest() {
        when(otRepository.findByIdAndTenantIdForUpdate(OT_ID, TENANT))
                .thenReturn(Optional.of(buildOt("CERRADA")));

        assertThatThrownBy(() -> service.registrarDevolucion(OT_ID, CONSUMO_ID, buildReq(5, null)))
                .isInstanceOf(BadRequestException.class)
                .hasMessageContaining("estado");
    }

    // ── RD-3: Consumo no encontrado → ResourceNotFoundException ──────────────

    @Test
    void registrarDevolucion_consumoNoEncontrado_lanzaNotFoundException() {
        when(otRepository.findByIdAndTenantIdForUpdate(OT_ID, TENANT))
                .thenReturn(Optional.of(buildOt("ABIERTA")));
        when(consumoRepository.findByIdAndTenantIdForUpdate(CONSUMO_ID, TENANT))
                .thenReturn(Optional.empty());

        assertThatThrownBy(() -> service.registrarDevolucion(OT_ID, CONSUMO_ID, buildReq(5, null)))
                .isInstanceOf(ResourceNotFoundException.class)
                .hasMessageContaining("Consumo no encontrado");
    }

    // ── RD-4: Servicio no se puede devolver → BadRequestException ────────────

    @Test
    void registrarDevolucion_servicio_lanzaBadRequest() {
        OrdenTrabajo ot = buildOt("ABIERTA");
        OtConsumoMaterial consumo = buildConsumo(ot, 10);
        Producto servicio = buildProducto("SERVICIO");

        when(otRepository.findByIdAndTenantIdForUpdate(OT_ID, TENANT)).thenReturn(Optional.of(ot));
        when(consumoRepository.findByIdAndTenantIdForUpdate(CONSUMO_ID, TENANT)).thenReturn(Optional.of(consumo));
        when(productoRepository.findByIdForUpdate(consumo.getProductoId())).thenReturn(Optional.of(servicio));

        assertThatThrownBy(() -> service.registrarDevolucion(OT_ID, CONSUMO_ID, buildReq(5, null)))
                .isInstanceOf(BadRequestException.class)
                .hasMessageContaining("No se pueden devolver servicios");
    }

    // ── RD-5: Todo ya devuelto → BadRequestException ──────────────────────────

    @Test
    void registrarDevolucion_todoDevuelto_lanzaBadRequest() {
        OrdenTrabajo ot = buildOt("EN_PROCESO");
        OtConsumoMaterial consumo = buildConsumo(ot, 5); // cantidadBase = 5
        Producto prod = buildProducto("PRODUCTO");

        when(otRepository.findByIdAndTenantIdForUpdate(OT_ID, TENANT)).thenReturn(Optional.of(ot));
        when(consumoRepository.findByIdAndTenantIdForUpdate(CONSUMO_ID, TENANT)).thenReturn(Optional.of(consumo));
        when(productoRepository.findByIdForUpdate(consumo.getProductoId())).thenReturn(Optional.of(prod));
        when(devolucionRepository.sumCantidadDevueltaByConsumoId(CONSUMO_ID, TENANT)).thenReturn(5); // ya devuelto todo

        assertThatThrownBy(() -> service.registrarDevolucion(OT_ID, CONSUMO_ID, buildReq(1, null)))
                .isInstanceOf(BadRequestException.class)
                .hasMessageContaining("ya fue devuelto");
    }

    // ── RD-6: Supera cantidad disponible → BadRequestException ───────────────

    @Test
    void registrarDevolucion_superaDisponible_lanzaBadRequest() {
        OrdenTrabajo ot = buildOt("ABIERTA");
        OtConsumoMaterial consumo = buildConsumo(ot, 5);
        Producto prod = buildProducto("PRODUCTO");

        when(otRepository.findByIdAndTenantIdForUpdate(OT_ID, TENANT)).thenReturn(Optional.of(ot));
        when(consumoRepository.findByIdAndTenantIdForUpdate(CONSUMO_ID, TENANT)).thenReturn(Optional.of(consumo));
        when(productoRepository.findByIdForUpdate(consumo.getProductoId())).thenReturn(Optional.of(prod));
        when(devolucionRepository.sumCantidadDevueltaByConsumoId(CONSUMO_ID, TENANT)).thenReturn(2); // 2 ya devueltos

        // Intenta devolver 4 cuando solo hay 3 disponibles (5 - 2 = 3)
        assertThatThrownBy(() -> service.registrarDevolucion(OT_ID, CONSUMO_ID, buildReq(4, null)))
                .isInstanceOf(BadRequestException.class)
                .hasMessageContaining("supera la disponible");
    }

    // ── RD-7: Devolución exitosa sin lotes → stock restaurado ─────────────────

    @Test
    void registrarDevolucion_sinLotes_restauraStockYGuardaDevolucion() {
        OrdenTrabajo ot = buildOt("ABIERTA");
        OtConsumoMaterial consumo = buildConsumo(ot, 10);
        Producto prod = buildProducto("PRODUCTO");
        prod.setStockActual(0); // stock ya descontado previamente
        Usuario usuario = buildUsuario();

        OtConsumoDevolucion devGuardada = OtConsumoDevolucion.builder()
                .id(100L).tenantId(TENANT).consumoId(CONSUMO_ID).ordenTrabajoId(OT_ID)
                .cantidadBase(5).motivo("prueba").registradoPor(USER_ID)
                .createdAt(LocalDateTime.now()).build();

        when(otRepository.findByIdAndTenantIdForUpdate(OT_ID, TENANT)).thenReturn(Optional.of(ot));
        when(consumoRepository.findByIdAndTenantIdForUpdate(CONSUMO_ID, TENANT)).thenReturn(Optional.of(consumo));
        when(productoRepository.findByIdForUpdate(consumo.getProductoId())).thenReturn(Optional.of(prod));
        when(devolucionRepository.sumCantidadDevueltaByConsumoId(CONSUMO_ID, TENANT)).thenReturn(0);
        when(consumoLoteRepository.findByConsumoIdOrderByCreatedAtAsc(CONSUMO_ID)).thenReturn(List.of());
        when(movimientoRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));
        when(devolucionRepository.save(any())).thenReturn(devGuardada);
        when(usuarioRepository.findById(USER_ID)).thenReturn(Optional.of(usuario));

        OtConsumoDevolucionDTO result = service.registrarDevolucion(OT_ID, CONSUMO_ID, buildReq(5, "prueba"));

        assertThat(result).isNotNull();
        assertThat(result.getCantidadBase()).isEqualTo(5);
        assertThat(result.getMotivo()).isEqualTo("prueba");
        assertThat(prod.getStockActual()).isEqualTo(5); // 0 + 5
        verify(productoRepository).save(prod);
        verify(movimientoRepository).save(any(MovimientoInventario.class));
        verify(devolucionRepository).save(any(OtConsumoDevolucion.class));
    }

    // ── RD-8: Tenant incorrecto en consumo → ResourceNotFoundException ────────

    @Test
    void registrarDevolucion_consumoDeOtroTenant_lanzaNotFoundException() {
        OrdenTrabajo ot = buildOt("ABIERTA");
        // consumo con tenantId diferente
        OtConsumoMaterial consumoOtroTenant = OtConsumoMaterial.builder()
                .id(CONSUMO_ID).tenantId("otro-tenant").ordenTrabajo(ot)
                .productoId(55L).factorPresentacion(1).cantidadPresentacion(5)
                .cantidadBase(5).registradoPor(USER_ID).createdAt(LocalDateTime.now())
                .build();

        when(otRepository.findByIdAndTenantIdForUpdate(OT_ID, TENANT)).thenReturn(Optional.of(ot));
        // findByIdAndTenantIdForUpdate ya filtra por tenantId, así que devuelve empty
        when(consumoRepository.findByIdAndTenantIdForUpdate(CONSUMO_ID, TENANT)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> service.registrarDevolucion(OT_ID, CONSUMO_ID, buildReq(2, null)))
                .isInstanceOf(ResourceNotFoundException.class);
    }

    // ── RD-9: Devolver después de clasificar supera disponible → BadRequest ──

    @Test
    void registrarDevolucion_despuesDeClasificar_superaDisponible_lanzaBadRequest() {
        OrdenTrabajo ot = buildOt("ABIERTA");
        OtConsumoMaterial consumo = buildConsumo(ot, 5); // cantidadBase = 5
        Producto prod = buildProducto("PRODUCTO");

        when(otRepository.findByIdAndTenantIdForUpdate(OT_ID, TENANT)).thenReturn(Optional.of(ot));
        when(consumoRepository.findByIdAndTenantIdForUpdate(CONSUMO_ID, TENANT)).thenReturn(Optional.of(consumo));
        when(productoRepository.findByIdForUpdate(consumo.getProductoId())).thenReturn(Optional.of(prod));
        when(devolucionRepository.sumCantidadDevueltaByConsumoId(CONSUMO_ID, TENANT)).thenReturn(0);
        when(clasificacionRepository.sumCantidadClasificadaByConsumoId(CONSUMO_ID, TENANT)).thenReturn(3);
        // disponible = 5 - 0 - 3 = 2; intento=3 → supera

        assertThatThrownBy(() -> service.registrarDevolucion(OT_ID, CONSUMO_ID, buildReq(3, null)))
                .isInstanceOf(BadRequestException.class)
                .hasMessageContaining("supera la disponible");
    }

    // ── RD-10: Devolución parcial cuando hay clasificaciones previas → éxito ──

    @Test
    void registrarDevolucion_conClasificacionPrevia_cantidadDentroDeDisponible_exito() {
        OrdenTrabajo ot = buildOt("EN_PROCESO");
        OtConsumoMaterial consumo = buildConsumo(ot, 5); // cantidadBase = 5
        Producto prod = buildProducto("PRODUCTO");
        prod.setStockActual(0);
        Usuario usuario = buildUsuario();

        OtConsumoDevolucion devGuardada = OtConsumoDevolucion.builder()
                .id(101L).tenantId(TENANT).consumoId(CONSUMO_ID).ordenTrabajoId(OT_ID)
                .cantidadBase(2).motivo(null).registradoPor(USER_ID)
                .createdAt(LocalDateTime.now()).build();

        when(otRepository.findByIdAndTenantIdForUpdate(OT_ID, TENANT)).thenReturn(Optional.of(ot));
        when(consumoRepository.findByIdAndTenantIdForUpdate(CONSUMO_ID, TENANT)).thenReturn(Optional.of(consumo));
        when(productoRepository.findByIdForUpdate(consumo.getProductoId())).thenReturn(Optional.of(prod));
        when(devolucionRepository.sumCantidadDevueltaByConsumoId(CONSUMO_ID, TENANT)).thenReturn(0);
        when(clasificacionRepository.sumCantidadClasificadaByConsumoId(CONSUMO_ID, TENANT)).thenReturn(3);
        // disponible = 5 - 0 - 3 = 2; intento=2 → exactamente disponible → OK
        when(consumoLoteRepository.findByConsumoIdOrderByCreatedAtAsc(CONSUMO_ID)).thenReturn(List.of());
        when(movimientoRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));
        when(devolucionRepository.save(any())).thenReturn(devGuardada);
        when(usuarioRepository.findById(USER_ID)).thenReturn(Optional.of(usuario));

        OtConsumoDevolucionDTO result = service.registrarDevolucion(OT_ID, CONSUMO_ID, buildReq(2, null));

        assertThat(result).isNotNull();
        assertThat(result.getCantidadBase()).isEqualTo(2);
        assertThat(prod.getStockActual()).isEqualTo(2); // 0 + 2
    }

    // ── RD-11: Devuelta + clasificada = base → disponible=0 → BadRequest ─────

    @Test
    void registrarDevolucion_devueltaMasClasificadaIgualBase_lanzaBadRequest() {
        OrdenTrabajo ot = buildOt("ABIERTA");
        OtConsumoMaterial consumo = buildConsumo(ot, 5); // cantidadBase = 5
        Producto prod = buildProducto("PRODUCTO");

        when(otRepository.findByIdAndTenantIdForUpdate(OT_ID, TENANT)).thenReturn(Optional.of(ot));
        when(consumoRepository.findByIdAndTenantIdForUpdate(CONSUMO_ID, TENANT)).thenReturn(Optional.of(consumo));
        when(productoRepository.findByIdForUpdate(consumo.getProductoId())).thenReturn(Optional.of(prod));
        when(devolucionRepository.sumCantidadDevueltaByConsumoId(CONSUMO_ID, TENANT)).thenReturn(2);
        when(clasificacionRepository.sumCantidadClasificadaByConsumoId(CONSUMO_ID, TENANT)).thenReturn(3);
        // disponible = 5 - 2 - 3 = 0 → BadRequest

        assertThatThrownBy(() -> service.registrarDevolucion(OT_ID, CONSUMO_ID, buildReq(1, null)))
                .isInstanceOf(BadRequestException.class)
                .hasMessageContaining("ya fue devuelto o clasificado");
    }

    // ── RD-12: Sin clasificaciones previas sigue funcionando → compatibilidad ─

    @Test
    void registrarDevolucion_sinClasificacion_funcionaComoAntes() {
        OrdenTrabajo ot = buildOt("ABIERTA");
        OtConsumoMaterial consumo = buildConsumo(ot, 5); // cantidadBase = 5
        Producto prod = buildProducto("PRODUCTO");
        prod.setStockActual(1);
        Usuario usuario = buildUsuario();

        OtConsumoDevolucion devGuardada = OtConsumoDevolucion.builder()
                .id(102L).tenantId(TENANT).consumoId(CONSUMO_ID).ordenTrabajoId(OT_ID)
                .cantidadBase(4).motivo(null).registradoPor(USER_ID)
                .createdAt(LocalDateTime.now()).build();

        when(otRepository.findByIdAndTenantIdForUpdate(OT_ID, TENANT)).thenReturn(Optional.of(ot));
        when(consumoRepository.findByIdAndTenantIdForUpdate(CONSUMO_ID, TENANT)).thenReturn(Optional.of(consumo));
        when(productoRepository.findByIdForUpdate(consumo.getProductoId())).thenReturn(Optional.of(prod));
        when(devolucionRepository.sumCantidadDevueltaByConsumoId(CONSUMO_ID, TENANT)).thenReturn(1);
        when(clasificacionRepository.sumCantidadClasificadaByConsumoId(CONSUMO_ID, TENANT)).thenReturn(0);
        // disponible = 5 - 1 - 0 = 4; intento=4 → exactamente disponible → OK
        when(consumoLoteRepository.findByConsumoIdOrderByCreatedAtAsc(CONSUMO_ID)).thenReturn(List.of());
        when(movimientoRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));
        when(devolucionRepository.save(any())).thenReturn(devGuardada);
        when(usuarioRepository.findById(USER_ID)).thenReturn(Optional.of(usuario));

        OtConsumoDevolucionDTO result = service.registrarDevolucion(OT_ID, CONSUMO_ID, buildReq(4, null));

        assertThat(result).isNotNull();
        assertThat(result.getCantidadBase()).isEqualTo(4);
        assertThat(prod.getStockActual()).isEqualTo(5); // 1 + 4
    }

    // ── Helpers ───────────────────────────────────────────────────────────────

    private RegistrarDevolucionOtRequestDTO buildReq(int cantidad, String motivo) {
        RegistrarDevolucionOtRequestDTO req = new RegistrarDevolucionOtRequestDTO();
        req.setCantidadBase(cantidad);
        req.setMotivo(motivo);
        return req;
    }

    private OrdenTrabajo buildOt(String estado) {
        OrdenTrabajo ot = new OrdenTrabajo();
        ot.setId(OT_ID);
        ot.setTenantId(TENANT);
        ot.setEstado(estado);
        ot.setNumeroOt("OT-001");
        return ot;
    }

    private OtConsumoMaterial buildConsumo(OrdenTrabajo ot, int cantidadBase) {
        return OtConsumoMaterial.builder()
                .id(CONSUMO_ID).tenantId(TENANT).ordenTrabajo(ot)
                .productoId(55L).factorPresentacion(1)
                .cantidadPresentacion(cantidadBase).cantidadBase(cantidadBase)
                .registradoPor(USER_ID).createdAt(LocalDateTime.now())
                .build();
    }

    private Producto buildProducto(String tipo) {
        Producto p = new Producto();
        p.setId(55L);
        p.setTenantId(TENANT);
        p.setNombre("Test");
        p.setTipo(tipo);
        p.setStockActual(10);
        p.setActivo(true);
        return p;
    }

    private Usuario buildUsuario() {
        Usuario u = new Usuario();
        u.setId(USER_ID);
        u.setNombre("Técnico Test");
        u.setEmail("tecnico@test.com");
        return u;
    }
}
