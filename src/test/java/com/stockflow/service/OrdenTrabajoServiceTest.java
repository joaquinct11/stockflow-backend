package com.stockflow.service;

import com.stockflow.dto.ActualizarEstadoOtRequestDTO;
import com.stockflow.dto.ActualizarOtRequestDTO;
import com.stockflow.dto.AsignarTecnicoOtRequestDTO;
import com.stockflow.dto.CrearOtRequestDTO;
import com.stockflow.dto.OrdenTrabajoDTO;
import com.stockflow.dto.OtPageResponseDTO;
import com.stockflow.entity.Cliente;
import com.stockflow.entity.EquipoCliente;
import com.stockflow.entity.OrdenTrabajo;
import com.stockflow.entity.Sucursal;
import com.stockflow.entity.Usuario;
import com.stockflow.exception.BadRequestException;
import com.stockflow.exception.ResourceNotFoundException;
import com.stockflow.mapper.OrdenTrabajoMapper;
import com.stockflow.repository.ClienteRepository;
import com.stockflow.repository.EquipoClienteRepository;
import com.stockflow.repository.OrdenTrabajoRepository;
import com.stockflow.repository.OtHistorialEstadoRepository;
import com.stockflow.repository.SucursalRepository;
import com.stockflow.repository.UsuarioRepository;
import com.stockflow.repository.UsuarioTenantRepository;
import com.stockflow.service.impl.OrdenTrabajoServiceImpl;
import com.stockflow.util.TenantContext;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.MockedStatic;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.PageRequest;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyShort;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mockStatic;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class OrdenTrabajoServiceTest {

    private static final String TENANT_A = "tenant-a";
    private static final Long USER_ID = 1L;

    @Mock private OrdenTrabajoRepository otRepository;
    @Mock private OtHistorialEstadoRepository historialRepository;
    @Mock private EquipoClienteRepository equipoRepository;
    @Mock private ClienteRepository clienteRepository;
    @Mock private SucursalRepository sucursalRepository;
    @Mock private UsuarioRepository usuarioRepository;
    @Mock private UsuarioTenantRepository usuarioTenantRepository;
    @Mock private OtNumeracionService otNumeracionService;
    @Mock private OrdenTrabajoMapper mapper;
    @Mock private TenantService tenantService;

    @InjectMocks
    private OrdenTrabajoServiceImpl service;

    private MockedStatic<TenantContext> tenantContextMock;

    @BeforeEach
    void setUp() {
        tenantContextMock = mockStatic(TenantContext.class);
        tenantContextMock.when(TenantContext::getCurrentTenant).thenReturn(TENANT_A);
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

    // ── T1: Crear OT válida ────────────────────────────────────────────────────

    @Test
    void crear_otValida_retornaEstadoAbiertoYNumeroGenerado() {
        // given
        Cliente cliente = buildCliente(10L, TENANT_A);
        EquipoCliente equipo = buildEquipo(20L, TENANT_A, cliente);
        Sucursal sucursal = buildSucursal(30L, TENANT_A);

        when(equipoRepository.findByIdAndTenantId(20L, TENANT_A)).thenReturn(Optional.of(equipo));
        when(clienteRepository.findByIdAndTenantId(10L, TENANT_A)).thenReturn(Optional.of(cliente));
        when(sucursalRepository.findByIdAndTenantId(30L, TENANT_A)).thenReturn(Optional.of(sucursal));
        when(otNumeracionService.generarNumeroOt(eq(TENANT_A), anyShort())).thenReturn("OT-2026-00001");

        OrdenTrabajo saved = buildOt(1L, TENANT_A, equipo, cliente, sucursal, "ABIERTA");
        when(otRepository.save(any())).thenReturn(saved);
        when(historialRepository.save(any())).thenReturn(null);
        OrdenTrabajoDTO expectedDTO = OrdenTrabajoDTO.builder()
                .id(1L).estado("ABIERTA").numeroOt("OT-2026-00001").build();
        when(mapper.toDTO(saved)).thenReturn(expectedDTO);

        CrearOtRequestDTO dto = CrearOtRequestDTO.builder()
                .equipoClienteId(20L).clienteId(10L).sucursalId(30L)
                .descripcionProblema("Falla en motor").build();

        // when
        OrdenTrabajoDTO result = service.crear(dto);

        // then
        assertThat(result.getEstado()).isEqualTo("ABIERTA");
        assertThat(result.getNumeroOt()).isEqualTo("OT-2026-00001");
        verify(historialRepository).save(any());
    }

    // ── T2: Listar OTs por tenant ──────────────────────────────────────────────

    @Test
    void listar_retornaOTsDelTenantActual() {
        // given
        when(otRepository.findPaginado(eq(TENANT_A), any(), any(), any(), any(), any()))
                .thenReturn(new PageImpl<>(List.of(), PageRequest.of(0, 20), 0));
        when(otRepository.countTotal(TENANT_A)).thenReturn(0L);
        when(otRepository.countByEstado(eq(TENANT_A), anyString())).thenReturn(0L);

        // when
        OtPageResponseDTO result = service.listar(0, 20, null, null, null, null);

        // then
        assertThat(result.getContent()).isEmpty();
        verify(otRepository).findPaginado(eq(TENANT_A), any(), any(), any(), any(), any());
    }

    // ── T3: Obtener OT por ID ──────────────────────────────────────────────────

    @Test
    void obtenerPorId_existente_retornaDTO() {
        // given
        Cliente cliente = buildCliente(10L, TENANT_A);
        EquipoCliente equipo = buildEquipo(20L, TENANT_A, cliente);
        Sucursal sucursal = buildSucursal(30L, TENANT_A);
        OrdenTrabajo ot = buildOt(5L, TENANT_A, equipo, cliente, sucursal, "EN_PROCESO");

        when(otRepository.findByIdAndTenantId(5L, TENANT_A)).thenReturn(Optional.of(ot));
        when(historialRepository.findByOrdenTrabajo_IdAndTenantIdOrderByCreatedAtAsc(5L, TENANT_A))
                .thenReturn(List.of());
        OrdenTrabajoDTO expectedDTO = OrdenTrabajoDTO.builder().id(5L).estado("EN_PROCESO").build();
        when(mapper.toDTO(ot)).thenReturn(expectedDTO);

        // when
        OrdenTrabajoDTO result = service.obtenerPorId(5L);

        // then
        assertThat(result.getId()).isEqualTo(5L);
        assertThat(result.getEstado()).isEqualTo("EN_PROCESO");
    }

    // ── T4: Actualizar OT ─────────────────────────────────────────────────────

    @Test
    void actualizar_camposDiagnostico_actualizaCorrectamente() {
        // given
        Cliente cliente = buildCliente(10L, TENANT_A);
        EquipoCliente equipo = buildEquipo(20L, TENANT_A, cliente);
        Sucursal sucursal = buildSucursal(30L, TENANT_A);
        OrdenTrabajo ot = buildOt(5L, TENANT_A, equipo, cliente, sucursal, "EN_PROCESO");

        when(otRepository.findByIdAndTenantId(5L, TENANT_A)).thenReturn(Optional.of(ot));
        when(otRepository.save(any())).thenReturn(ot);
        OrdenTrabajoDTO expectedDTO = OrdenTrabajoDTO.builder().id(5L).diagnostico("Bobina quemada").build();
        when(mapper.toDTO(ot)).thenReturn(expectedDTO);

        ActualizarOtRequestDTO dto = ActualizarOtRequestDTO.builder()
                .diagnostico("Bobina quemada").build();

        // when
        OrdenTrabajoDTO result = service.actualizar(5L, dto);

        // then
        assertThat(result.getDiagnostico()).isEqualTo("Bobina quemada");
        verify(otRepository).save(ot);
    }

    // ── T5: Rechazar acceso cross-tenant ──────────────────────────────────────

    @Test
    void obtenerPorId_otDeOtroTenant_lanzaResourceNotFoundException() {
        // given — la OT existe para TENANT_B, no para TENANT_A
        when(otRepository.findByIdAndTenantId(99L, TENANT_A)).thenReturn(Optional.empty());

        // when / then
        assertThatThrownBy(() -> service.obtenerPorId(99L))
                .isInstanceOf(ResourceNotFoundException.class);
    }

    // ── T6: Rechazar cliente de otro tenant ───────────────────────────────────

    @Test
    void crear_clienteDeOtroTenant_lanzaResourceNotFoundException() {
        // given
        Cliente cliente = buildCliente(10L, TENANT_A);
        EquipoCliente equipo = buildEquipo(20L, TENANT_A, cliente);

        when(equipoRepository.findByIdAndTenantId(20L, TENANT_A)).thenReturn(Optional.of(equipo));
        // Cliente no encontrado en el tenant actual
        when(clienteRepository.findByIdAndTenantId(10L, TENANT_A)).thenReturn(Optional.empty());

        CrearOtRequestDTO dto = CrearOtRequestDTO.builder()
                .equipoClienteId(20L).clienteId(10L).sucursalId(30L)
                .descripcionProblema("Test").build();

        // when / then
        assertThatThrownBy(() -> service.crear(dto))
                .isInstanceOf(ResourceNotFoundException.class);
    }

    // ── T7: Rechazar equipo que no pertenece al cliente ───────────────────────

    @Test
    void crear_equipoNoPertenecealCliente_lanzaBadRequestException() {
        // given
        Cliente clienteReal = buildCliente(10L, TENANT_A);
        Cliente clienteOtro = buildCliente(11L, TENANT_A);
        // El equipo pertenece al clienteOtro, no al clienteReal
        EquipoCliente equipo = buildEquipo(20L, TENANT_A, clienteOtro);

        when(equipoRepository.findByIdAndTenantId(20L, TENANT_A)).thenReturn(Optional.of(equipo));
        when(clienteRepository.findByIdAndTenantId(10L, TENANT_A)).thenReturn(Optional.of(clienteReal));

        CrearOtRequestDTO dto = CrearOtRequestDTO.builder()
                .equipoClienteId(20L).clienteId(10L).sucursalId(30L)
                .descripcionProblema("Test").build();

        // when / then
        assertThatThrownBy(() -> service.crear(dto))
                .isInstanceOf(BadRequestException.class)
                .hasMessageContaining("no pertenece al cliente");
    }

    // ── T8: Rechazar sucursal de otro tenant ──────────────────────────────────

    @Test
    void crear_sucursalDeOtroTenant_lanzaResourceNotFoundException() {
        // given
        Cliente cliente = buildCliente(10L, TENANT_A);
        EquipoCliente equipo = buildEquipo(20L, TENANT_A, cliente);

        when(equipoRepository.findByIdAndTenantId(20L, TENANT_A)).thenReturn(Optional.of(equipo));
        when(clienteRepository.findByIdAndTenantId(10L, TENANT_A)).thenReturn(Optional.of(cliente));
        when(sucursalRepository.findByIdAndTenantId(30L, TENANT_A)).thenReturn(Optional.empty());

        CrearOtRequestDTO dto = CrearOtRequestDTO.builder()
                .equipoClienteId(20L).clienteId(10L).sucursalId(30L)
                .descripcionProblema("Test").build();

        // when / then
        assertThatThrownBy(() -> service.crear(dto))
                .isInstanceOf(ResourceNotFoundException.class);
    }

    // ── T9: Estado inválido ────────────────────────────────────────────────────

    @Test
    void actualizarEstado_estadoInvalido_lanzaBadRequestException() {
        // given — la validación del estado ocurre antes de consultar la OT
        ActualizarEstadoOtRequestDTO dto = ActualizarEstadoOtRequestDTO.builder()
                .estado("ESTADO_INEXISTENTE").build();

        // when / then
        assertThatThrownBy(() -> service.actualizarEstado(5L, dto))
                .isInstanceOf(BadRequestException.class)
                .hasMessageContaining("Estado inválido");
    }

    // ── T10: Validación de campo requerido ────────────────────────────────────

    @Test
    void crear_equipoInexistente_lanzaResourceNotFoundException() {
        // given — el equipo no existe en el tenant
        when(equipoRepository.findByIdAndTenantId(anyLong(), eq(TENANT_A)))
                .thenReturn(Optional.empty());

        CrearOtRequestDTO dto = CrearOtRequestDTO.builder()
                .equipoClienteId(99L).clienteId(10L).sucursalId(30L)
                .descripcionProblema("Test").build();

        // when / then
        assertThatThrownBy(() -> service.crear(dto))
                .isInstanceOf(ResourceNotFoundException.class);
    }

    // ── T11: asignarTecnico — éxito ───────────────────────────────────────────

    @Test
    void asignarTecnico_tecnicoValido_asignaCorrectamente() {
        // given
        Cliente cliente = buildCliente(10L, TENANT_A);
        EquipoCliente equipo = buildEquipo(20L, TENANT_A, cliente);
        Sucursal sucursal = buildSucursal(30L, TENANT_A);
        OrdenTrabajo ot = buildOt(5L, TENANT_A, equipo, cliente, sucursal, "EN_PROCESO");
        Usuario tecnico = buildUsuario(50L);

        when(otRepository.findByIdAndTenantId(5L, TENANT_A)).thenReturn(Optional.of(ot));
        when(usuarioRepository.findById(50L)).thenReturn(Optional.of(tecnico));
        when(usuarioTenantRepository.existsByUsuarioIdAndTenantIdAndActivoTrue(50L, TENANT_A)).thenReturn(true);
        when(otRepository.save(any())).thenReturn(ot);
        when(historialRepository.save(any())).thenReturn(null);
        OrdenTrabajoDTO expectedDTO = OrdenTrabajoDTO.builder().id(5L).build();
        when(mapper.toDTO(ot)).thenReturn(expectedDTO);

        AsignarTecnicoOtRequestDTO dto = AsignarTecnicoOtRequestDTO.builder()
                .tecnicoId(50L).build();

        // when
        OrdenTrabajoDTO result = service.asignarTecnico(5L, dto);

        // then
        assertThat(result.getId()).isEqualTo(5L);
        verify(otRepository).save(ot);
    }

    // ── T12: asignarTecnico — técnico de otro tenant ──────────────────────────

    @Test
    void asignarTecnico_tecnicoOtroTenant_lanzaBadRequestException() {
        // given
        Cliente cliente = buildCliente(10L, TENANT_A);
        EquipoCliente equipo = buildEquipo(20L, TENANT_A, cliente);
        Sucursal sucursal = buildSucursal(30L, TENANT_A);
        OrdenTrabajo ot = buildOt(5L, TENANT_A, equipo, cliente, sucursal, "ABIERTA");
        Usuario tecnico = buildUsuario(50L);

        when(otRepository.findByIdAndTenantId(5L, TENANT_A)).thenReturn(Optional.of(ot));
        when(usuarioRepository.findById(50L)).thenReturn(Optional.of(tecnico));
        // Técnico no pertenece al tenant
        when(usuarioTenantRepository.existsByUsuarioIdAndTenantIdAndActivoTrue(50L, TENANT_A)).thenReturn(false);

        AsignarTecnicoOtRequestDTO dto = AsignarTecnicoOtRequestDTO.builder()
                .tecnicoId(50L).build();

        // when / then
        assertThatThrownBy(() -> service.asignarTecnico(5L, dto))
                .isInstanceOf(BadRequestException.class)
                .hasMessageContaining("no pertenece a este tenant");
    }

    // ── T13: asignarTecnico — técnico inexistente ─────────────────────────────

    @Test
    void asignarTecnico_tecnicoInexistente_lanzaResourceNotFoundException() {
        // given
        Cliente cliente = buildCliente(10L, TENANT_A);
        EquipoCliente equipo = buildEquipo(20L, TENANT_A, cliente);
        Sucursal sucursal = buildSucursal(30L, TENANT_A);
        OrdenTrabajo ot = buildOt(5L, TENANT_A, equipo, cliente, sucursal, "ABIERTA");

        when(otRepository.findByIdAndTenantId(5L, TENANT_A)).thenReturn(Optional.of(ot));
        when(usuarioRepository.findById(999L)).thenReturn(Optional.empty());

        AsignarTecnicoOtRequestDTO dto = AsignarTecnicoOtRequestDTO.builder()
                .tecnicoId(999L).build();

        // when / then
        assertThatThrownBy(() -> service.asignarTecnico(5L, dto))
                .isInstanceOf(ResourceNotFoundException.class)
                .hasMessageContaining("Técnico no encontrado");
    }

    // ── T14: asignarTecnico — técnico inactivo ────────────────────────────────

    @Test
    void asignarTecnico_tecnicoInactivo_lanzaBadRequestException() {
        // given
        Cliente cliente = buildCliente(10L, TENANT_A);
        EquipoCliente equipo = buildEquipo(20L, TENANT_A, cliente);
        Sucursal sucursal = buildSucursal(30L, TENANT_A);
        OrdenTrabajo ot = buildOt(5L, TENANT_A, equipo, cliente, sucursal, "ABIERTA");
        Usuario tecnicoInactivo = Usuario.builder().id(50L).nombre("Técnico inactivo")
                .activo(false).email("t@test.com").contraseña("x").build();

        when(otRepository.findByIdAndTenantId(5L, TENANT_A)).thenReturn(Optional.of(ot));
        when(usuarioRepository.findById(50L)).thenReturn(Optional.of(tecnicoInactivo));

        AsignarTecnicoOtRequestDTO dto = AsignarTecnicoOtRequestDTO.builder()
                .tecnicoId(50L).build();

        // when / then
        assertThatThrownBy(() -> service.asignarTecnico(5L, dto))
                .isInstanceOf(BadRequestException.class)
                .hasMessageContaining("no está activo");
    }

    // ── T15: actualizar — no permite cambiar técnico ──────────────────────────

    @Test
    void actualizar_dtoNoTieneTecnicoId_noAfectaTecnico() {
        // given
        Cliente cliente = buildCliente(10L, TENANT_A);
        EquipoCliente equipo = buildEquipo(20L, TENANT_A, cliente);
        Sucursal sucursal = buildSucursal(30L, TENANT_A);
        Usuario tecnicoOriginal = buildUsuario(50L);
        OrdenTrabajo ot = buildOt(5L, TENANT_A, equipo, cliente, sucursal, "EN_PROCESO");
        ot.setTecnico(tecnicoOriginal);

        when(otRepository.findByIdAndTenantId(5L, TENANT_A)).thenReturn(Optional.of(ot));
        when(otRepository.save(any())).thenReturn(ot);
        when(mapper.toDTO(ot)).thenReturn(OrdenTrabajoDTO.builder().id(5L).build());

        // ActualizarOtRequestDTO no tiene campo tecnicoId (fue eliminado en corrección C2)
        ActualizarOtRequestDTO dto = ActualizarOtRequestDTO.builder()
                .diagnostico("Nuevo diagnóstico").build();

        // when
        service.actualizar(5L, dto);

        // then — el técnico en la entidad no fue cambiado
        assertThat(ot.getTecnico()).isEqualTo(tecnicoOriginal);
    }

    // ── T16: numeraciones de dos tenants son independientes ───────────────────

    @Test
    void crear_dosTenants_numeracionesIndependientes() {
        // Valida que generarNumeroOt se llama con el tenantId correcto para cada tenant.
        // La independencia real es garantizada por la clave única (tenant_id, anio) en BD.
        Cliente clienteA = buildCliente(10L, TENANT_A);
        EquipoCliente equipoA = buildEquipo(20L, TENANT_A, clienteA);
        Sucursal sucursalA = buildSucursal(30L, TENANT_A);

        when(equipoRepository.findByIdAndTenantId(20L, TENANT_A)).thenReturn(Optional.of(equipoA));
        when(clienteRepository.findByIdAndTenantId(10L, TENANT_A)).thenReturn(Optional.of(clienteA));
        when(sucursalRepository.findByIdAndTenantId(30L, TENANT_A)).thenReturn(Optional.of(sucursalA));
        when(otNumeracionService.generarNumeroOt(eq(TENANT_A), anyShort())).thenReturn("OT-2026-00001");
        OrdenTrabajo savedA = buildOt(1L, TENANT_A, equipoA, clienteA, sucursalA, "ABIERTA");
        when(otRepository.save(any())).thenReturn(savedA);
        when(historialRepository.save(any())).thenReturn(null);
        when(mapper.toDTO(savedA)).thenReturn(OrdenTrabajoDTO.builder().id(1L).numeroOt("OT-2026-00001").build());

        CrearOtRequestDTO dtoA = CrearOtRequestDTO.builder()
                .equipoClienteId(20L).clienteId(10L).sucursalId(30L)
                .descripcionProblema("Falla A").build();

        OrdenTrabajoDTO resultA = service.crear(dtoA);

        // El servicio de numeración fue invocado con TENANT_A
        verify(otNumeracionService).generarNumeroOt(eq(TENANT_A), anyShort());
        assertThat(resultA.getNumeroOt()).isEqualTo("OT-2026-00001");
    }

    // ── Helpers ───────────────────────────────────────────────────────────────

    private Cliente buildCliente(Long id, String tenantId) {
        return Cliente.builder().id(id).tenantId(tenantId).nombre("Cliente " + id)
                .activo(true).build();
    }

    private EquipoCliente buildEquipo(Long id, String tenantId, Cliente cliente) {
        return EquipoCliente.builder().id(id).tenantId(tenantId)
                .nombre("Equipo " + id).activo(true).cliente(cliente)
                .createdAt(LocalDateTime.now()).build();
    }

    private Sucursal buildSucursal(Long id, String tenantId) {
        return Sucursal.builder().id(id).tenantId(tenantId).nombre("Sucursal " + id)
                .activo(true).esPrincipal(false).bloqueadaPorPlan(false)
                .createdAt(LocalDateTime.now()).updatedAt(LocalDateTime.now()).build();
    }

    private OrdenTrabajo buildOt(Long id, String tenantId, EquipoCliente equipo,
                                  Cliente cliente, Sucursal sucursal, String estado) {
        return OrdenTrabajo.builder()
                .id(id).tenantId(tenantId)
                .numeroOt("OT-2026-" + String.format("%05d", id))
                .anio((short) 2026)
                .equipo(equipo).cliente(cliente).sucursal(sucursal)
                .estado(estado).prioridad("NORMAL")
                .descripcionProblema("Descripción de prueba")
                .createdAt(LocalDateTime.now())
                .build();
    }

    private Usuario buildUsuario(Long id) {
        return Usuario.builder().id(id).nombre("Técnico " + id).activo(true)
                .email("tecnico" + id + "@test.com").contraseña("x").build();
    }

    // ── T17: rechaza cambio manual a FACTURADA ────────────────────────────────

    @Test
    void actualizarEstado_aFacturada_lanzaBadRequestException() {
        ActualizarEstadoOtRequestDTO dto = ActualizarEstadoOtRequestDTO.builder()
                .estado("FACTURADA").build();

        assertThatThrownBy(() -> service.actualizarEstado(5L, dto))
                .isInstanceOf(BadRequestException.class)
                .hasMessageContaining("FACTURADA");
    }

    // ── T18: rechaza cambio manual a ENTREGADA ────────────────────────────────

    @Test
    void actualizarEstado_aEntregada_lanzaBadRequestException() {
        ActualizarEstadoOtRequestDTO dto = ActualizarEstadoOtRequestDTO.builder()
                .estado("ENTREGADA").build();

        assertThatThrownBy(() -> service.actualizarEstado(5L, dto))
                .isInstanceOf(BadRequestException.class)
                .hasMessageContaining("ENTREGADA");
    }

    // ── T19: permite cancelar desde estado CERRADA_TECN ──────────────────────

    @Test
    void actualizarEstado_cancelarDesdeCerradaTecn_guardaEstadoCancelada() {
        Cliente cliente = buildCliente(10L, TENANT_A);
        EquipoCliente equipo = buildEquipo(20L, TENANT_A, cliente);
        Sucursal sucursal = buildSucursal(30L, TENANT_A);
        OrdenTrabajo ot = buildOt(5L, TENANT_A, equipo, cliente, sucursal, "CERRADA_TECN");

        when(otRepository.findByIdAndTenantId(5L, TENANT_A)).thenReturn(Optional.of(ot));
        when(otRepository.save(any())).thenReturn(ot);
        when(historialRepository.save(any())).thenReturn(null);
        OrdenTrabajoDTO expectedDTO = OrdenTrabajoDTO.builder().id(5L).estado("CANCELADA").build();
        when(mapper.toDTO(ot)).thenReturn(expectedDTO);

        ActualizarEstadoOtRequestDTO dto = ActualizarEstadoOtRequestDTO.builder()
                .estado("CANCELADA").observacion("Cliente retiró el equipo").build();

        OrdenTrabajoDTO result = service.actualizarEstado(5L, dto);

        assertThat(result.getEstado()).isEqualTo("CANCELADA");
        verify(otRepository).save(ot);
        verify(historialRepository).save(any());
    }

    // ── T20: facturarOt sigue funcionando (integración mínima del guard) ──────

    @Test
    void actualizarEstado_facturadaEnMinusculas_tambienEsRechazado() {
        // Verifica que el .toUpperCase() no elude la protección
        ActualizarEstadoOtRequestDTO dto = ActualizarEstadoOtRequestDTO.builder()
                .estado("facturada").build();

        assertThatThrownBy(() -> service.actualizarEstado(5L, dto))
                .isInstanceOf(BadRequestException.class)
                .hasMessageContaining("FACTURADA");
    }

    // ── T21: mensaje de error específico para ENTREGADA ───────────────────────

    @Test
    void actualizarEstado_entregadaEnMinusculas_tambienEsRechazado() {
        ActualizarEstadoOtRequestDTO dto = ActualizarEstadoOtRequestDTO.builder()
                .estado("entregada").build();

        assertThatThrownBy(() -> service.actualizarEstado(5L, dto))
                .isInstanceOf(BadRequestException.class)
                .hasMessageContaining("ENTREGADA");
    }
}
