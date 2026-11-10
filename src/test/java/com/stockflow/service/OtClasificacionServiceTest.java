package com.stockflow.service;

import com.stockflow.dto.OtConsumoClasificacionDTO;
import com.stockflow.dto.RegistrarClasificacionOtRequestDTO;
import com.stockflow.entity.OrdenTrabajo;
import com.stockflow.entity.OtConsumoClasificacion;
import com.stockflow.entity.OtConsumoMaterial;
import com.stockflow.entity.Producto;
import com.stockflow.entity.Usuario;
import com.stockflow.exception.BadRequestException;
import com.stockflow.exception.ResourceNotFoundException;
import com.stockflow.repository.OrdenTrabajoRepository;
import com.stockflow.repository.OtConsumoClasificacionRepository;
import com.stockflow.repository.OtConsumoDevolucionRepository;
import com.stockflow.repository.OtConsumoMaterialRepository;
import com.stockflow.repository.ProductoRepository;
import com.stockflow.repository.UsuarioRepository;
import com.stockflow.service.impl.OtClasificacionServiceImpl;
import com.stockflow.util.TenantContext;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.MockedStatic;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.LocalDateTime;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mockStatic;
import java.util.List;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class OtClasificacionServiceTest {

    private static final String TENANT     = "tenant-cl";
    private static final Long   OT_ID      = 1L;
    private static final Long   CONSUMO_ID = 10L;
    private static final Long   USER_ID    = 99L;

    @Mock private OrdenTrabajoRepository          otRepository;
    @Mock private OtConsumoMaterialRepository     consumoRepository;
    @Mock private OtConsumoDevolucionRepository   devolucionRepository;
    @Mock private OtConsumoClasificacionRepository clasificacionRepository;
    @Mock private ProductoRepository              productoRepository;
    @Mock private UsuarioRepository               usuarioRepository;
    @Mock private TenantService                   tenantService;

    @InjectMocks
    private OtClasificacionServiceImpl service;

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

    // ── CL-1: OT no encontrada → ResourceNotFoundException ───────────────────

    @Test
    void registrarClasificacion_otNoEncontrada_lanzaNotFoundException() {
        when(otRepository.findByIdAndTenantIdForUpdate(OT_ID, TENANT)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> service.registrarClasificacion(OT_ID, CONSUMO_ID, buildReq("INSTALADO", 3)))
                .isInstanceOf(ResourceNotFoundException.class)
                .hasMessageContaining("Orden de trabajo no encontrada");
    }

    // ── CL-2: Estado inválido → BadRequestException ───────────────────────────

    @Test
    void registrarClasificacion_estadoInvalido_lanzaBadRequest() {
        when(otRepository.findByIdAndTenantIdForUpdate(OT_ID, TENANT))
                .thenReturn(Optional.of(buildOt("CERRADA")));

        assertThatThrownBy(() -> service.registrarClasificacion(OT_ID, CONSUMO_ID, buildReq("INSTALADO", 3)))
                .isInstanceOf(BadRequestException.class)
                .hasMessageContaining("estado");
    }

    // ── CL-3: Servicio no se puede clasificar → BadRequestException ───────────

    @Test
    void registrarClasificacion_servicio_lanzaBadRequest() {
        OrdenTrabajo ot = buildOt("ABIERTA");
        OtConsumoMaterial consumo = buildConsumo(ot, 10);
        Producto servicio = buildProducto("SERVICIO");

        when(otRepository.findByIdAndTenantIdForUpdate(OT_ID, TENANT)).thenReturn(Optional.of(ot));
        when(consumoRepository.findByIdAndTenantIdForUpdate(CONSUMO_ID, TENANT)).thenReturn(Optional.of(consumo));
        when(productoRepository.findById(consumo.getProductoId()))
                .thenReturn(Optional.of(servicio));

        assertThatThrownBy(() -> service.registrarClasificacion(OT_ID, CONSUMO_ID, buildReq("INSTALADO", 3)))
                .isInstanceOf(BadRequestException.class)
                .hasMessageContaining("No se pueden clasificar servicios");
    }

    // ── CL-4: Supera cantidad disponible → BadRequestException ───────────────

    @Test
    void registrarClasificacion_superaDisponible_lanzaBadRequest() {
        OrdenTrabajo ot = buildOt("EN_PROCESO");
        OtConsumoMaterial consumo = buildConsumo(ot, 5);
        Producto prod = buildProducto("PRODUCTO");

        when(otRepository.findByIdAndTenantIdForUpdate(OT_ID, TENANT)).thenReturn(Optional.of(ot));
        when(consumoRepository.findByIdAndTenantIdForUpdate(CONSUMO_ID, TENANT)).thenReturn(Optional.of(consumo));
        when(productoRepository.findById(consumo.getProductoId())).thenReturn(Optional.of(prod));
        when(devolucionRepository.sumCantidadDevueltaByConsumoId(CONSUMO_ID, TENANT)).thenReturn(2);
        when(clasificacionRepository.sumCantidadClasificadaByConsumoId(CONSUMO_ID, TENANT)).thenReturn(2);
        // disponible = 5 - 2 - 2 = 1, intenta clasificar 2

        assertThatThrownBy(() -> service.registrarClasificacion(OT_ID, CONSUMO_ID, buildReq("DAÑADO", 2)))
                .isInstanceOf(BadRequestException.class)
                .hasMessageContaining("supera la disponible");
    }

    // ── CL-5: Todo clasificado y devuelto → BadRequestException ──────────────

    @Test
    void registrarClasificacion_todoClasificadoDevuelto_lanzaBadRequest() {
        OrdenTrabajo ot = buildOt("ABIERTA");
        OtConsumoMaterial consumo = buildConsumo(ot, 5);
        Producto prod = buildProducto("PRODUCTO");

        when(otRepository.findByIdAndTenantIdForUpdate(OT_ID, TENANT)).thenReturn(Optional.of(ot));
        when(consumoRepository.findByIdAndTenantIdForUpdate(CONSUMO_ID, TENANT)).thenReturn(Optional.of(consumo));
        when(productoRepository.findById(consumo.getProductoId())).thenReturn(Optional.of(prod));
        when(devolucionRepository.sumCantidadDevueltaByConsumoId(CONSUMO_ID, TENANT)).thenReturn(3);
        when(clasificacionRepository.sumCantidadClasificadaByConsumoId(CONSUMO_ID, TENANT)).thenReturn(2);
        // disponible = 5 - 3 - 2 = 0

        assertThatThrownBy(() -> service.registrarClasificacion(OT_ID, CONSUMO_ID, buildReq("PERDIDO", 1)))
                .isInstanceOf(BadRequestException.class)
                .hasMessageContaining("ya fue clasificado o devuelto");
    }

    // ── CL-6: Clasificación exitosa → DTO correcto ────────────────────────────

    @Test
    void registrarClasificacion_exitosa_devuelveDTO() {
        OrdenTrabajo ot = buildOt("ABIERTA");
        OtConsumoMaterial consumo = buildConsumo(ot, 10);
        Producto prod = buildProducto("PRODUCTO");
        Usuario usuario = buildUsuario();

        OtConsumoClasificacion clGuardada = OtConsumoClasificacion.builder()
                .id(200L).tenantId(TENANT).consumoId(CONSUMO_ID).ordenTrabajoId(OT_ID)
                .clasificacion("RECUPERABLE").cantidad(3).notas("ok")
                .registradoPor(USER_ID).createdAt(LocalDateTime.now()).build();

        when(otRepository.findByIdAndTenantIdForUpdate(OT_ID, TENANT)).thenReturn(Optional.of(ot));
        when(consumoRepository.findByIdAndTenantIdForUpdate(CONSUMO_ID, TENANT)).thenReturn(Optional.of(consumo));
        when(productoRepository.findById(consumo.getProductoId())).thenReturn(Optional.of(prod));
        when(devolucionRepository.sumCantidadDevueltaByConsumoId(CONSUMO_ID, TENANT)).thenReturn(0);
        when(clasificacionRepository.sumCantidadClasificadaByConsumoId(CONSUMO_ID, TENANT)).thenReturn(0);
        when(clasificacionRepository.save(any())).thenReturn(clGuardada);
        when(usuarioRepository.findById(USER_ID)).thenReturn(Optional.of(usuario));

        OtConsumoClasificacionDTO result = service.registrarClasificacion(OT_ID, CONSUMO_ID,
                buildReq("RECUPERABLE", 3));

        assertThat(result).isNotNull();
        assertThat(result.getClasificacion()).isEqualTo("RECUPERABLE");
        assertThat(result.getCantidad()).isEqualTo(3);
        verify(clasificacionRepository).save(any(OtConsumoClasificacion.class));
    }

    // ── CL-7: Clasificaciones válidas aceptadas ───────────────────────────────

    @Test
    void registrarClasificacion_todasLasClasificacionesValidas() {
        String[] validos = {"INSTALADO", "RECUPERABLE", "DAÑADO", "PERDIDO"};
        OrdenTrabajo ot = buildOt("ABIERTA");
        Producto prod = buildProducto("PRODUCTO");
        Usuario usuario = buildUsuario();

        for (String cl : validos) {
            OtConsumoMaterial consumo = buildConsumo(ot, 10);

            OtConsumoClasificacion saved = OtConsumoClasificacion.builder()
                    .id(1L).tenantId(TENANT).consumoId(CONSUMO_ID).ordenTrabajoId(OT_ID)
                    .clasificacion(cl).cantidad(1).registradoPor(USER_ID)
                    .createdAt(LocalDateTime.now()).build();

            when(otRepository.findByIdAndTenantIdForUpdate(OT_ID, TENANT)).thenReturn(Optional.of(ot));
            when(consumoRepository.findByIdAndTenantIdForUpdate(CONSUMO_ID, TENANT)).thenReturn(Optional.of(consumo));
            when(productoRepository.findById(consumo.getProductoId())).thenReturn(Optional.of(prod));
            when(devolucionRepository.sumCantidadDevueltaByConsumoId(CONSUMO_ID, TENANT)).thenReturn(0);
            when(clasificacionRepository.sumCantidadClasificadaByConsumoId(CONSUMO_ID, TENANT)).thenReturn(0);
            when(clasificacionRepository.save(any())).thenReturn(saved);
            when(usuarioRepository.findById(USER_ID)).thenReturn(Optional.of(usuario));

            OtConsumoClasificacionDTO result = service.registrarClasificacion(OT_ID, CONSUMO_ID,
                    buildReq(cl, 1));
            assertThat(result.getClasificacion()).isEqualTo(cl);
        }
    }

    // ── Helpers ───────────────────────────────────────────────────────────────

    private RegistrarClasificacionOtRequestDTO buildReq(String clasificacion, int cantidad) {
        RegistrarClasificacionOtRequestDTO req = new RegistrarClasificacionOtRequestDTO();
        req.setClasificacion(clasificacion);
        req.setCantidad(cantidad);
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
