package com.stockflow.service;

import com.stockflow.dto.*;
import com.stockflow.entity.*;
import com.stockflow.exception.BadRequestException;
import com.stockflow.exception.ResourceNotFoundException;
import com.stockflow.repository.*;
import com.stockflow.service.impl.CitaServiceImpl;
import com.stockflow.entity.Rol;
import com.stockflow.entity.Sucursal;
import com.stockflow.entity.Usuario;
import com.stockflow.util.TenantContext;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
@DisplayName("CitaServiceImpl — reglas de negocio y tenant isolation")
class CitaServiceImplTest {

    private static final String TENANT_VET  = "vet-test-01";
    private static final Long   CLIENTE_1   = 1L;
    private static final Long   MASCOTA_1   = 10L;
    private static final Long   PROF_1      = 100L;
    private static final Long   CITA_ID     = 999L;

    // Ventana de tiempo en el futuro (evita validación de fecha pasada)
    private static final LocalDateTime INICIO = LocalDateTime.now().plusDays(1).withHour(9).withMinute(0).withSecond(0).withNano(0);
    private static final LocalDateTime FIN    = INICIO.plusHours(1);

    @Mock private CitaRepository          citaRepository;
    @Mock private ClienteRepository       clienteRepository;
    @Mock private MascotaRepository       mascotaRepository;
    @Mock private UsuarioTenantRepository usuarioTenantRepository;
    @Mock private SucursalRepository      sucursalRepository;
    @Mock private TenantService           tenantService;

    @InjectMocks private CitaServiceImpl citaService;

    private Cliente        cliente;
    private Mascota        mascota;
    private UsuarioTenant  profesional;

    @BeforeEach
    void setUp() {
        cliente     = Cliente.builder().id(CLIENTE_1).nombre("Pedro Pérez").tenantId(TENANT_VET).activo(true).build();
        mascota     = Mascota.builder().id(MASCOTA_1).tenantId(TENANT_VET).cliente(cliente).nombre("Toby")
                        .especie("PERRO").activo(true).createdAt(LocalDateTime.now()).build();
        profesional = UsuarioTenant.builder().id(PROF_1).tenantId(TENANT_VET).activo(true).build();

        doNothing().when(tenantService).requireRubro(anyString(), any(String[].class));

        // Default: userId=99L con membresía activa — evita AccessDeniedException en todos los tests
        // que llaman a actualizar()/cambiarEstado() sin configurar un userId específico
        TenantContext.setCurrentUserId(99L);
        when(usuarioTenantRepository.findByUsuarioIdAndTenantIdAndActivoTrue(99L, TENANT_VET))
                .thenReturn(Optional.of(profesional));
    }

    @AfterEach
    void tearDown() {
        TenantContext.clear();
        SecurityContextHolder.clearContext();
    }

    // ── A: crear cita válida ──────────────────────────────────────────────────

    @Test
    @DisplayName("A — crear cita válida → éxito")
    void crearCita_valida_exito() {
        TenantContext.setCurrentTenant(TENANT_VET);
        TenantContext.setCurrentUserId(99L);
        when(clienteRepository.findByIdAndTenantId(CLIENTE_1, TENANT_VET)).thenReturn(Optional.of(cliente));
        when(mascotaRepository.findByIdAndTenantId(MASCOTA_1, TENANT_VET)).thenReturn(Optional.of(mascota));
        when(citaRepository.findSolapamiento(any(), any(), any(), any(), any())).thenReturn(List.of());
        when(usuarioTenantRepository.findByUsuarioIdAndTenantIdAndActivoTrue(99L, TENANT_VET)).thenReturn(Optional.of(profesional));

        Cita saved = buildCita(CITA_ID, EstadoCita.PENDIENTE, INICIO, FIN);
        when(citaRepository.save(any(Cita.class))).thenReturn(saved);
        when(clienteRepository.findById(CLIENTE_1)).thenReturn(Optional.of(cliente));
        when(mascotaRepository.findById(MASCOTA_1)).thenReturn(Optional.of(mascota));

        CrearCitaRequestDTO dto = CrearCitaRequestDTO.builder()
                .clienteId(CLIENTE_1).mascotaId(MASCOTA_1)
                .fechaInicio(INICIO).fechaFin(FIN)
                .tipo(TipoCita.CONSULTA).build();

        CitaDTO result = citaService.crear(dto);

        assertThat(result.getId()).isEqualTo(CITA_ID);
        assertThat(result.getEstado()).isEqualTo(EstadoCita.PENDIENTE);
        verify(citaRepository).save(any(Cita.class));
    }

    // ── B: fechaFin <= fechaInicio debe fallar ────────────────────────────────

    @Test
    @DisplayName("B — fechaFin <= fechaInicio → BadRequestException")
    void crearCita_fechaFinAntes_falla() {
        TenantContext.setCurrentTenant(TENANT_VET);

        CrearCitaRequestDTO dto = CrearCitaRequestDTO.builder()
                .clienteId(CLIENTE_1).mascotaId(MASCOTA_1)
                .fechaInicio(INICIO).fechaFin(INICIO.minusMinutes(1))
                .tipo(TipoCita.CONSULTA).build();

        assertThatThrownBy(() -> citaService.crear(dto))
                .isInstanceOf(BadRequestException.class)
                .hasMessageContaining("fechaFin");
    }

    // ── C: solapamiento activo debe fallar ────────────────────────────────────

    @Test
    @DisplayName("C — solapamiento con cita activa → BadRequestException")
    void crearCita_solapamiento_falla() {
        TenantContext.setCurrentTenant(TENANT_VET);
        when(clienteRepository.findByIdAndTenantId(CLIENTE_1, TENANT_VET)).thenReturn(Optional.of(cliente));
        when(mascotaRepository.findByIdAndTenantId(MASCOTA_1, TENANT_VET)).thenReturn(Optional.of(mascota));
        when(usuarioTenantRepository.findById(PROF_1))
                .thenReturn(Optional.of(profesional));
        when(citaRepository.findSolapamiento(eq(TENANT_VET), eq(PROF_1), any(), any(), eq(-1L)))
                .thenReturn(List.of(buildCita(5L, EstadoCita.CONFIRMADA, INICIO, FIN)));

        CrearCitaRequestDTO dto = CrearCitaRequestDTO.builder()
                .clienteId(CLIENTE_1).mascotaId(MASCOTA_1)
                .usuarioTenantId(PROF_1)
                .fechaInicio(INICIO).fechaFin(FIN)
                .tipo(TipoCita.CONSULTA).build();

        assertThatThrownBy(() -> citaService.crear(dto))
                .isInstanceOf(BadRequestException.class)
                .hasMessageContaining("horario");
    }

    // ── D: cita cancelada no bloquea nueva ────────────────────────────────────

    @Test
    @DisplayName("D — solapamiento con cita CANCELADA → permitido (no bloquea)")
    void crearCita_solapamientoCancelada_permitido() {
        TenantContext.setCurrentTenant(TENANT_VET);
        TenantContext.setCurrentUserId(99L);
        when(clienteRepository.findByIdAndTenantId(CLIENTE_1, TENANT_VET)).thenReturn(Optional.of(cliente));
        when(mascotaRepository.findByIdAndTenantId(MASCOTA_1, TENANT_VET)).thenReturn(Optional.of(mascota));
        when(usuarioTenantRepository.findById(PROF_1)).thenReturn(Optional.of(profesional));
        when(usuarioTenantRepository.findByUsuarioIdAndTenantIdAndActivoTrue(99L, TENANT_VET)).thenReturn(Optional.of(profesional));
        // La query findSolapamiento ya filtra CANCELADA; devuelve lista vacía
        when(citaRepository.findSolapamiento(eq(TENANT_VET), eq(PROF_1), any(), any(), eq(-1L)))
                .thenReturn(List.of());

        Cita saved = buildCita(CITA_ID, EstadoCita.PENDIENTE, INICIO, FIN);
        when(citaRepository.save(any(Cita.class))).thenReturn(saved);
        when(clienteRepository.findById(CLIENTE_1)).thenReturn(Optional.of(cliente));
        when(mascotaRepository.findById(MASCOTA_1)).thenReturn(Optional.of(mascota));

        CrearCitaRequestDTO dto = CrearCitaRequestDTO.builder()
                .clienteId(CLIENTE_1).mascotaId(MASCOTA_1)
                .usuarioTenantId(PROF_1)
                .fechaInicio(INICIO).fechaFin(FIN)
                .tipo(TipoCita.CONSULTA).build();

        CitaDTO result = citaService.crear(dto);
        assertThat(result).isNotNull();
    }

    // ── E: transición válida PENDIENTE → CONFIRMADA ──────────────────────────

    @Test
    @DisplayName("E — PENDIENTE → CONFIRMADA → estado actualizado")
    void cambiarEstado_pendienteAConfirmada_exito() {
        TenantContext.setCurrentTenant(TENANT_VET);
        Cita cita = buildCita(CITA_ID, EstadoCita.PENDIENTE, INICIO, FIN);
        when(citaRepository.findByIdAndTenantId(CITA_ID, TENANT_VET)).thenReturn(Optional.of(cita));
        when(citaRepository.save(any(Cita.class))).thenReturn(cita);
        when(clienteRepository.findById(CLIENTE_1)).thenReturn(Optional.of(cliente));
        when(mascotaRepository.findById(MASCOTA_1)).thenReturn(Optional.of(mascota));

        CitaDTO result = citaService.cambiarEstado(CITA_ID,
                CambiarEstadoCitaRequestDTO.builder().nuevoEstado(EstadoCita.CONFIRMADA).build());

        assertThat(result.getEstado()).isEqualTo(EstadoCita.CONFIRMADA);
    }

    // ── F: transición inválida ATENDIDA → CANCELADA debe fallar ─────────────

    @Test
    @DisplayName("F — ATENDIDA → CANCELADA → BadRequestException (estado final)")
    void cambiarEstado_atendidaACancelada_falla() {
        TenantContext.setCurrentTenant(TENANT_VET);
        Cita cita = buildCita(CITA_ID, EstadoCita.ATENDIDA, INICIO, FIN);
        when(citaRepository.findByIdAndTenantId(CITA_ID, TENANT_VET)).thenReturn(Optional.of(cita));

        assertThatThrownBy(() -> citaService.cambiarEstado(CITA_ID,
                CambiarEstadoCitaRequestDTO.builder().nuevoEstado(EstadoCita.CANCELADA).build()))
                .isInstanceOf(BadRequestException.class)
                .hasMessageContaining("ATENDIDA");
    }

    // ── G: mascota de otro tenant no permitida ────────────────────────────────

    @Test
    @DisplayName("G — mascota de otro tenant → ResourceNotFoundException")
    void crearCita_mascotaOtroTenant_falla() {
        TenantContext.setCurrentTenant(TENANT_VET);
        when(clienteRepository.findByIdAndTenantId(CLIENTE_1, TENANT_VET)).thenReturn(Optional.of(cliente));
        when(mascotaRepository.findByIdAndTenantId(MASCOTA_1, TENANT_VET)).thenReturn(Optional.empty());

        CrearCitaRequestDTO dto = CrearCitaRequestDTO.builder()
                .clienteId(CLIENTE_1).mascotaId(MASCOTA_1)
                .fechaInicio(INICIO).fechaFin(FIN)
                .tipo(TipoCita.CONSULTA).build();

        assertThatThrownBy(() -> citaService.crear(dto))
                .isInstanceOf(ResourceNotFoundException.class)
                .hasMessageContaining(String.valueOf(MASCOTA_1));
    }

    // ── I: crear resuelve creadoPorId cuando existe UsuarioTenant ────────────

    @Test
    @DisplayName("I — crear con usuarioTenant en contexto → creadoPorId asignado correctamente")
    void crear_conUsuarioTenantEnContexto_asignaCreadoPorId() {
        final Long USUARIO_ID = 77L;
        TenantContext.setCurrentTenant(TENANT_VET);
        TenantContext.setCurrentUserId(USUARIO_ID);

        when(clienteRepository.findByIdAndTenantId(CLIENTE_1, TENANT_VET)).thenReturn(Optional.of(cliente));
        when(mascotaRepository.findByIdAndTenantId(MASCOTA_1, TENANT_VET)).thenReturn(Optional.of(mascota));
        when(citaRepository.findSolapamiento(any(), any(), any(), any(), any())).thenReturn(List.of());
        when(usuarioTenantRepository.findByUsuarioIdAndTenantIdAndActivoTrue(USUARIO_ID, TENANT_VET))
                .thenReturn(Optional.of(profesional));

        Cita saved = buildCita(CITA_ID, EstadoCita.PENDIENTE, INICIO, FIN);
        saved.setCreadoPorId(PROF_1);
        when(citaRepository.save(any(Cita.class))).thenAnswer(inv -> {
            Cita c = inv.getArgument(0);
            c.setId(CITA_ID);
            return c;
        });
        when(clienteRepository.findById(CLIENTE_1)).thenReturn(Optional.of(cliente));
        when(mascotaRepository.findById(MASCOTA_1)).thenReturn(Optional.of(mascota));

        CrearCitaRequestDTO dto = CrearCitaRequestDTO.builder()
                .clienteId(CLIENTE_1).mascotaId(MASCOTA_1)
                .fechaInicio(INICIO).fechaFin(FIN)
                .tipo(TipoCita.CONSULTA).build();

        citaService.crear(dto);

        // Verificar que se buscó el UsuarioTenant del usuario autenticado
        verify(usuarioTenantRepository).findByUsuarioIdAndTenantIdAndActivoTrue(USUARIO_ID, TENANT_VET);
        // Verificar que save fue llamado con creadoPorId = PROF_1 (el id del UsuarioTenant)
        verify(citaRepository).save(argThat(c -> PROF_1.equals(c.getCreadoPorId())));
    }

    // ── J: crear sin membresía de tenant → AccessDeniedException (fail-closed) ─

    @Test
    @DisplayName("J — usuario sin membresía en el tenant → AccessDeniedException (no crea la cita)")
    void crear_sinMembresiaEnTenant_falla() {
        final Long USUARIO_ID = 88L;
        TenantContext.setCurrentTenant(TENANT_VET);
        TenantContext.setCurrentUserId(USUARIO_ID);

        when(clienteRepository.findByIdAndTenantId(CLIENTE_1, TENANT_VET)).thenReturn(Optional.of(cliente));
        when(mascotaRepository.findByIdAndTenantId(MASCOTA_1, TENANT_VET)).thenReturn(Optional.of(mascota));
        when(citaRepository.findSolapamiento(any(), any(), any(), any(), any())).thenReturn(List.of());
        // El usuario autenticado no tiene membresía activa en este tenant
        when(usuarioTenantRepository.findByUsuarioIdAndTenantIdAndActivoTrue(USUARIO_ID, TENANT_VET))
                .thenReturn(Optional.empty());

        CrearCitaRequestDTO dto = CrearCitaRequestDTO.builder()
                .clienteId(CLIENTE_1).mascotaId(MASCOTA_1)
                .fechaInicio(INICIO).fechaFin(FIN)
                .tipo(TipoCita.CONSULTA).build();

        assertThatThrownBy(() -> citaService.crear(dto))
                .isInstanceOf(AccessDeniedException.class)
                .hasMessageContaining("membresía");
        // Confirmar que nunca se llegó a persistir nada
        verify(citaRepository, never()).save(any());
    }

    // ── M: crear con membresía desactivada → AccessDeniedException ───────────

    @Test
    @DisplayName("M — usuario con membresía desactivada (activo=false) → AccessDeniedException (no crea la cita)")
    void crear_conMembresiaDesactivada_falla() {
        final Long USUARIO_ID = 55L;
        TenantContext.setCurrentTenant(TENANT_VET);
        TenantContext.setCurrentUserId(USUARIO_ID);

        when(clienteRepository.findByIdAndTenantId(CLIENTE_1, TENANT_VET)).thenReturn(Optional.of(cliente));
        when(mascotaRepository.findByIdAndTenantId(MASCOTA_1, TENANT_VET)).thenReturn(Optional.of(mascota));
        when(citaRepository.findSolapamiento(any(), any(), any(), any(), any())).thenReturn(List.of());
        // La membresía existe pero está desactivada → AndActivoTrue devuelve vacío
        when(usuarioTenantRepository.findByUsuarioIdAndTenantIdAndActivoTrue(USUARIO_ID, TENANT_VET))
                .thenReturn(Optional.empty());

        CrearCitaRequestDTO dto = CrearCitaRequestDTO.builder()
                .clienteId(CLIENTE_1).mascotaId(MASCOTA_1)
                .fechaInicio(INICIO).fechaFin(FIN)
                .tipo(TipoCita.CONSULTA).build();

        assertThatThrownBy(() -> citaService.crear(dto))
                .isInstanceOf(AccessDeniedException.class)
                .hasMessageContaining("activa");
        verify(citaRepository, never()).save(any());
    }

    // ── K: cambiar a CANCELADA con permiso → éxito ───────────────────────────

    @Test
    @DisplayName("K — PENDIENTE → CANCELADA con PERM_CANCELAR_CITA → estado actualizado")
    void cambiarEstado_cancelarConPermiso_exito() {
        TenantContext.setCurrentTenant(TENANT_VET);
        SecurityContextHolder.getContext().setAuthentication(
            new UsernamePasswordAuthenticationToken("user", null,
                List.of(new SimpleGrantedAuthority("PERM_CAMBIAR_ESTADO_CITA"),
                        new SimpleGrantedAuthority("PERM_CANCELAR_CITA")))
        );

        Cita cita = buildCita(CITA_ID, EstadoCita.PENDIENTE, INICIO, FIN);
        when(citaRepository.findByIdAndTenantId(CITA_ID, TENANT_VET)).thenReturn(Optional.of(cita));
        when(citaRepository.save(any(Cita.class))).thenReturn(cita);
        when(clienteRepository.findById(CLIENTE_1)).thenReturn(Optional.of(cliente));
        when(mascotaRepository.findById(MASCOTA_1)).thenReturn(Optional.of(mascota));

        CitaDTO result = citaService.cambiarEstado(CITA_ID,
                CambiarEstadoCitaRequestDTO.builder().nuevoEstado(EstadoCita.CANCELADA).build());

        assertThat(result.getEstado()).isEqualTo(EstadoCita.CANCELADA);
    }

    // ── L: cambiar a CANCELADA sin permiso → AccessDeniedException ──────────

    @Test
    @DisplayName("L — PENDIENTE → CANCELADA sin PERM_CANCELAR_CITA → AccessDeniedException")
    void cambiarEstado_cancelarSinPermiso_falla() {
        TenantContext.setCurrentTenant(TENANT_VET);
        // Solo tiene CAMBIAR_ESTADO_CITA, no CANCELAR_CITA (como VETERINARIO)
        SecurityContextHolder.getContext().setAuthentication(
            new UsernamePasswordAuthenticationToken("user", null,
                List.of(new SimpleGrantedAuthority("PERM_CAMBIAR_ESTADO_CITA")))
        );

        Cita cita = buildCita(CITA_ID, EstadoCita.PENDIENTE, INICIO, FIN);
        when(citaRepository.findByIdAndTenantId(CITA_ID, TENANT_VET)).thenReturn(Optional.of(cita));

        assertThatThrownBy(() -> citaService.cambiarEstado(CITA_ID,
                CambiarEstadoCitaRequestDTO.builder().nuevoEstado(EstadoCita.CANCELADA).build()))
                .isInstanceOf(AccessDeniedException.class)
                .hasMessageContaining("CANCELAR_CITA");
    }

    // ── H: listado sin resultados ────────────────────────────────────────────

    @Test
    @DisplayName("H — listar sin filtros → contenido vacío, totales en cero")
    void listar_sinCitas_devuelveVacio() {
        TenantContext.setCurrentTenant(TENANT_VET);
        Page<Cita> emptyPage = new PageImpl<>(List.of(),
                PageRequest.of(0, 15, Sort.by("fechaInicio")), 0L);
        when(citaRepository.findAll(any(Specification.class), any(Pageable.class)))
                .thenReturn(emptyPage);

        CitaPageResponseDTO result = citaService.listar(
                0, 15, null, null, null, null, null, null, null, "");

        assertThat(result.getContent()).isEmpty();
        assertThat(result.getTotalElements()).isEqualTo(0L);
        assertThat(result.getPage()).isEqualTo(0);
    }

    // ── N: listar sin filtros → devuelve todas las citas del tenant ──────────

    @Test
    @DisplayName("N — GET /citas sin filtros → devuelve resultados paginados")
    void listar_sinFiltros_devuelveResultados() {
        TenantContext.setCurrentTenant(TENANT_VET);
        Cita c1 = buildCita(1L, EstadoCita.PENDIENTE,  INICIO, FIN);
        Cita c2 = buildCita(2L, EstadoCita.CONFIRMADA, INICIO.plusDays(1), FIN.plusDays(1));
        Page<Cita> page = new PageImpl<>(List.of(c1, c2),
                PageRequest.of(0, 15, Sort.by("fechaInicio")), 2L);
        when(citaRepository.findAll(any(Specification.class), any(Pageable.class))).thenReturn(page);
        when(clienteRepository.findById(CLIENTE_1)).thenReturn(Optional.of(cliente));
        when(mascotaRepository.findById(MASCOTA_1)).thenReturn(Optional.of(mascota));

        CitaPageResponseDTO result = citaService.listar(
                0, 15, null, null, null, null, null, null, null, "");

        assertThat(result.getContent()).hasSize(2);
        assertThat(result.getTotalElements()).isEqualTo(2L);
        assertThat(result.getTotalPages()).isEqualTo(1);
    }

    // ── O: listar con rango de fechas ────────────────────────────────────────

    @Test
    @DisplayName("O — listar con fechaDesde y fechaHasta → devuelve citas en rango")
    void listar_conFechas_devuelveCitasEnRango() {
        TenantContext.setCurrentTenant(TENANT_VET);
        Cita c = buildCita(3L, EstadoCita.PENDIENTE, INICIO, FIN);
        Page<Cita> page = new PageImpl<>(List.of(c),
                PageRequest.of(0, 15, Sort.by("fechaInicio")), 1L);
        when(citaRepository.findAll(any(Specification.class), any(Pageable.class))).thenReturn(page);
        when(clienteRepository.findById(CLIENTE_1)).thenReturn(Optional.of(cliente));
        when(mascotaRepository.findById(MASCOTA_1)).thenReturn(Optional.of(mascota));

        CitaPageResponseDTO result = citaService.listar(
                0, 15, null, INICIO.minusHours(1), FIN.plusHours(1),
                null, null, null, null, "");

        assertThat(result.getContent()).hasSize(1);
        assertThat(result.getTotalElements()).isEqualTo(1L);
    }

    // ── P: listar con filtros combinados (estado + cliente) ──────────────────

    @Test
    @DisplayName("P — listar con estado=CONFIRMADA y clienteId → filtros combinados")
    void listar_filtrosCombinados_devuelveFiltrado() {
        TenantContext.setCurrentTenant(TENANT_VET);
        Cita c = buildCita(4L, EstadoCita.CONFIRMADA, INICIO, FIN);
        Page<Cita> page = new PageImpl<>(List.of(c),
                PageRequest.of(0, 15, Sort.by("fechaInicio")), 1L);
        when(citaRepository.findAll(any(Specification.class), any(Pageable.class))).thenReturn(page);
        when(clienteRepository.findById(CLIENTE_1)).thenReturn(Optional.of(cliente));
        when(mascotaRepository.findById(MASCOTA_1)).thenReturn(Optional.of(mascota));

        CitaPageResponseDTO result = citaService.listar(
                0, 15, EstadoCita.CONFIRMADA, null, null,
                CLIENTE_1, null, null, null, "");

        assertThat(result.getContent()).hasSize(1);
        assertThat(result.getContent().get(0).getEstado()).isEqualTo(EstadoCita.CONFIRMADA);
    }

    // ── Q: paginación → totalPages calculado correctamente ───────────────────

    @Test
    @DisplayName("Q — paginación con size=5 y 12 registros → 3 páginas, totalElements=12")
    void listar_paginacion_totalPagesCorrectos() {
        TenantContext.setCurrentTenant(TENANT_VET);
        Cita c = buildCita(5L, EstadoCita.PENDIENTE, INICIO, FIN);
        Page<Cita> page = new PageImpl<>(List.of(c, c, c, c, c),
                PageRequest.of(0, 5, Sort.by("fechaInicio")), 12L);
        when(citaRepository.findAll(any(Specification.class), any(Pageable.class))).thenReturn(page);
        when(clienteRepository.findById(CLIENTE_1)).thenReturn(Optional.of(cliente));
        when(mascotaRepository.findById(MASCOTA_1)).thenReturn(Optional.of(mascota));

        CitaPageResponseDTO result = citaService.listar(
                0, 5, null, null, null, null, null, null, null, "");

        assertThat(result.getTotalElements()).isEqualTo(12L);
        assertThat(result.getTotalPages()).isEqualTo(3);
        assertThat(result.getContent()).hasSize(5);
    }

    // ── R: hora local persiste sin conversión UTC ────────────────────────────
    //
    // El backend usa LocalDateTime + TIMESTAMP WITHOUT TIME ZONE.
    // El frontend debe enviar "YYYY-MM-DDTHH:mm:ss" SIN sufijo Z ni offset.
    // Si el frontend enviara "2026-10-12T14:00:00.000Z" (UTC) y el usuario está en UTC+2,
    // Jackson (time-zone: America/Lima = UTC-5) convertiría: 14:00Z - 5h = 09:00.
    // Esto explica el desfase observado de 7h (UTC+2 del navegador vs UTC-5 del servidor).
    //
    // Este test verifica que una cita creada con hora 16:00 (LocalDateTime) se persiste
    // exactamente a las 16:00, sin ajuste de zona horaria en la capa de servicio.
    @Test
    @DisplayName("R — hora local persiste sin conversión UTC: 16:00 guardado → devuelto como 16:00")
    void crear_horaLocal_persisteSinConversionUTC() {
        TenantContext.setCurrentTenant(TENANT_VET);
        TenantContext.setCurrentUserId(99L);

        // Simular cita a las 16:00 hora local (lo que el frontend envía tras el fix)
        LocalDateTime inicio16h = LocalDateTime.now().plusDays(1).withHour(16).withMinute(0).withSecond(0).withNano(0);
        LocalDateTime fin16h30  = inicio16h.plusMinutes(30);

        when(clienteRepository.findByIdAndTenantId(CLIENTE_1, TENANT_VET)).thenReturn(Optional.of(cliente));
        when(mascotaRepository.findByIdAndTenantId(MASCOTA_1, TENANT_VET)).thenReturn(Optional.of(mascota));
        when(citaRepository.findSolapamiento(any(), any(), any(), any(), any())).thenReturn(List.of());
        when(usuarioTenantRepository.findByUsuarioIdAndTenantIdAndActivoTrue(99L, TENANT_VET))
                .thenReturn(Optional.of(profesional));
        when(clienteRepository.findById(CLIENTE_1)).thenReturn(Optional.of(cliente));
        when(mascotaRepository.findById(MASCOTA_1)).thenReturn(Optional.of(mascota));
        when(citaRepository.save(any(Cita.class))).thenAnswer(inv -> {
            Cita c = inv.getArgument(0);
            c.setId(CITA_ID);
            return c;
        });

        CrearCitaRequestDTO dto = CrearCitaRequestDTO.builder()
                .clienteId(CLIENTE_1).mascotaId(MASCOTA_1)
                .fechaInicio(inicio16h).fechaFin(fin16h30)
                .tipo(TipoCita.CONSULTA).build();

        CitaDTO result = citaService.crear(dto);

        // La hora persiste exactamente como fue enviada — el servicio no ajusta zonas
        assertThat(result.getFechaInicio()).isEqualTo(inicio16h);
        assertThat(result.getFechaFin()).isEqualTo(fin16h30);
        assertThat(result.getFechaInicio().getHour()).isEqualTo(16);
        assertThat(result.getFechaFin().getHour()).isEqualTo(16);
        assertThat(result.getFechaFin().getMinute()).isEqualTo(30);
    }

    // ── S: actualizar sin membresía activa → AccessDeniedException (D2) ────────

    @Test
    @DisplayName("S — actualizar con membresía desactivada → AccessDeniedException")
    void actualizar_sinMembresiaActiva_falla() {
        final Long USUARIO_ID = 88L;
        TenantContext.setCurrentTenant(TENANT_VET);
        TenantContext.setCurrentUserId(USUARIO_ID);
        when(usuarioTenantRepository.findByUsuarioIdAndTenantIdAndActivoTrue(USUARIO_ID, TENANT_VET))
                .thenReturn(Optional.empty());

        ActualizarCitaRequestDTO dto = ActualizarCitaRequestDTO.builder()
                .fechaInicio(INICIO).fechaFin(FIN).tipo(TipoCita.CONSULTA).build();

        assertThatThrownBy(() -> citaService.actualizar(CITA_ID, dto))
                .isInstanceOf(AccessDeniedException.class)
                .hasMessageContaining("membresía");
        verify(citaRepository, never()).save(any());
    }

    // ── T: cambiarEstado sin membresía activa → AccessDeniedException (D2) ───

    @Test
    @DisplayName("T — cambiarEstado con membresía desactivada → AccessDeniedException")
    void cambiarEstado_sinMembresiaActiva_falla() {
        final Long USUARIO_ID = 88L;
        TenantContext.setCurrentTenant(TENANT_VET);
        TenantContext.setCurrentUserId(USUARIO_ID);
        when(usuarioTenantRepository.findByUsuarioIdAndTenantIdAndActivoTrue(USUARIO_ID, TENANT_VET))
                .thenReturn(Optional.empty());

        Cita cita = buildCita(CITA_ID, EstadoCita.PENDIENTE, INICIO, FIN);
        when(citaRepository.findByIdAndTenantId(CITA_ID, TENANT_VET)).thenReturn(Optional.of(cita));

        assertThatThrownBy(() -> citaService.cambiarEstado(CITA_ID,
                CambiarEstadoCitaRequestDTO.builder().nuevoEstado(EstadoCita.CONFIRMADA).build()))
                .isInstanceOf(AccessDeniedException.class)
                .hasMessageContaining("membresía");
        verify(citaRepository, never()).save(any());
    }

    // ── U: crear con profesional inactivo → BadRequestException (D4) ────────

    @Test
    @DisplayName("U — crear con profesional inactivo → BadRequestException")
    void crearCita_profesionalInactivo_falla() {
        TenantContext.setCurrentTenant(TENANT_VET);
        TenantContext.setCurrentUserId(99L);
        UsuarioTenant profInactivo = UsuarioTenant.builder().id(PROF_1).tenantId(TENANT_VET).activo(false).build();
        when(clienteRepository.findByIdAndTenantId(CLIENTE_1, TENANT_VET)).thenReturn(Optional.of(cliente));
        when(mascotaRepository.findByIdAndTenantId(MASCOTA_1, TENANT_VET)).thenReturn(Optional.of(mascota));
        when(usuarioTenantRepository.findById(PROF_1)).thenReturn(Optional.of(profInactivo));

        CrearCitaRequestDTO dto = CrearCitaRequestDTO.builder()
                .clienteId(CLIENTE_1).mascotaId(MASCOTA_1)
                .usuarioTenantId(PROF_1)
                .fechaInicio(INICIO).fechaFin(FIN)
                .tipo(TipoCita.CONSULTA).build();

        assertThatThrownBy(() -> citaService.crear(dto))
                .isInstanceOf(BadRequestException.class)
                .hasMessageContaining("membresía activa");
        verify(citaRepository, never()).save(any());
    }

    // ── V: actualizar con profesional inactivo → BadRequestException (D4) ────

    @Test
    @DisplayName("V — actualizar con profesional inactivo → BadRequestException")
    void actualizarCita_profesionalInactivo_falla() {
        TenantContext.setCurrentTenant(TENANT_VET);
        // userId=99L tiene membresía activa (configurado en @BeforeEach)
        UsuarioTenant profInactivo = UsuarioTenant.builder().id(PROF_1).tenantId(TENANT_VET).activo(false).build();
        when(usuarioTenantRepository.findById(PROF_1)).thenReturn(Optional.of(profInactivo));

        Cita cita = buildCita(CITA_ID, EstadoCita.PENDIENTE, INICIO, FIN);
        when(citaRepository.findByIdAndTenantId(CITA_ID, TENANT_VET)).thenReturn(Optional.of(cita));

        ActualizarCitaRequestDTO dto = ActualizarCitaRequestDTO.builder()
                .usuarioTenantId(PROF_1)
                .fechaInicio(INICIO).fechaFin(FIN)
                .tipo(TipoCita.CONSULTA).build();

        assertThatThrownBy(() -> citaService.actualizar(CITA_ID, dto))
                .isInstanceOf(BadRequestException.class)
                .hasMessageContaining("membresía activa");
        verify(citaRepository, never()).save(any());
    }

    // ── W: VETERINARIO intenta PENDIENTE→CONFIRMADA → AccessDeniedException (D1) ─

    @Test
    @DisplayName("W — VETERINARIO intenta PENDIENTE→CONFIRMADA → AccessDeniedException")
    void cambiarEstado_veterinario_intentaConfirmar_falla() {
        TenantContext.setCurrentTenant(TENANT_VET);
        // userId=99L → membresía activa (profesional, id=100L) — configurado en @BeforeEach
        SecurityContextHolder.getContext().setAuthentication(
            new UsernamePasswordAuthenticationToken("vet", null,
                List.of(new SimpleGrantedAuthority("ROLE_VETERINARIO"),
                        new SimpleGrantedAuthority("PERM_CAMBIAR_ESTADO_CITA")))
        );

        Cita cita = buildCita(CITA_ID, EstadoCita.PENDIENTE, INICIO, FIN);
        cita.setUsuarioTenantId(PROF_1); // cita asignada al mismo VETERINARIO
        when(citaRepository.findByIdAndTenantId(CITA_ID, TENANT_VET)).thenReturn(Optional.of(cita));

        assertThatThrownBy(() -> citaService.cambiarEstado(CITA_ID,
                CambiarEstadoCitaRequestDTO.builder().nuevoEstado(EstadoCita.CONFIRMADA).build()))
                .isInstanceOf(AccessDeniedException.class)
                .hasMessageContaining("VETERINARIO");
    }

    // ── X: VETERINARIO intenta modificar cita de otro → AccessDeniedException (D5) ─

    @Test
    @DisplayName("X — VETERINARIO intenta EN_ATENCION en cita de otro profesional → AccessDeniedException")
    void cambiarEstado_veterinario_citaDeOtroProfesional_falla() {
        TenantContext.setCurrentTenant(TENANT_VET);
        // userId=99L → membresía activa, profesional.id=PROF_1 (100L) — configurado en @BeforeEach
        SecurityContextHolder.getContext().setAuthentication(
            new UsernamePasswordAuthenticationToken("vet", null,
                List.of(new SimpleGrantedAuthority("ROLE_VETERINARIO"),
                        new SimpleGrantedAuthority("PERM_CAMBIAR_ESTADO_CITA")))
        );

        Cita cita = buildCita(CITA_ID, EstadoCita.CONFIRMADA, INICIO, FIN);
        cita.setUsuarioTenantId(200L); // cita asignada a OTRO profesional
        when(citaRepository.findByIdAndTenantId(CITA_ID, TENANT_VET)).thenReturn(Optional.of(cita));

        assertThatThrownBy(() -> citaService.cambiarEstado(CITA_ID,
                CambiarEstadoCitaRequestDTO.builder().nuevoEstado(EstadoCita.EN_ATENCION).build()))
                .isInstanceOf(AccessDeniedException.class)
                .hasMessageContaining("asignadas");
    }

    // ── Y: RECEPCIONISTA intenta CONFIRMADA→EN_ATENCION → AccessDeniedException (D3) ─

    @Test
    @DisplayName("Y — RECEPCIONISTA intenta marcar EN_ATENCION → AccessDeniedException")
    void cambiarEstado_recepcionista_intentaEnAtencion_falla() {
        TenantContext.setCurrentTenant(TENANT_VET);
        // userId=99L → membresía activa, profesional.id=PROF_1 (100L) — configurado en @BeforeEach
        SecurityContextHolder.getContext().setAuthentication(
            new UsernamePasswordAuthenticationToken("recep", null,
                List.of(new SimpleGrantedAuthority("ROLE_RECEPCIONISTA"),
                        new SimpleGrantedAuthority("PERM_CAMBIAR_ESTADO_CITA")))
        );

        Cita cita = buildCita(CITA_ID, EstadoCita.CONFIRMADA, INICIO, FIN);
        cita.setUsuarioTenantId(200L); // cita asignada a otro profesional (no al RECEPCIONISTA)
        when(citaRepository.findByIdAndTenantId(CITA_ID, TENANT_VET)).thenReturn(Optional.of(cita));

        assertThatThrownBy(() -> citaService.cambiarEstado(CITA_ID,
                CambiarEstadoCitaRequestDTO.builder().nuevoEstado(EstadoCita.EN_ATENCION).build()))
                .isInstanceOf(AccessDeniedException.class)
                .hasMessageContaining("profesional asignado");
    }

    // ── Z: VETERINARIO → EN_ATENCION en su propia cita → éxito (D1+D5) ────────

    @Test
    @DisplayName("Z — VETERINARIO marca EN_ATENCION en cita propia → estado actualizado")
    void cambiarEstado_veterinario_suPropiaEnAtencion_exito() {
        TenantContext.setCurrentTenant(TENANT_VET);
        // userId=99L → membresía activa, profesional.id=PROF_1 (100L) — configurado en @BeforeEach
        SecurityContextHolder.getContext().setAuthentication(
            new UsernamePasswordAuthenticationToken("vet", null,
                List.of(new SimpleGrantedAuthority("ROLE_VETERINARIO"),
                        new SimpleGrantedAuthority("PERM_CAMBIAR_ESTADO_CITA")))
        );

        Cita cita = buildCita(CITA_ID, EstadoCita.CONFIRMADA, INICIO, FIN);
        cita.setUsuarioTenantId(PROF_1); // cita asignada al mismo VETERINARIO (id=100L)
        when(citaRepository.findByIdAndTenantId(CITA_ID, TENANT_VET)).thenReturn(Optional.of(cita));
        when(citaRepository.save(any(Cita.class))).thenReturn(cita);
        when(clienteRepository.findById(CLIENTE_1)).thenReturn(Optional.of(cliente));
        when(mascotaRepository.findById(MASCOTA_1)).thenReturn(Optional.of(mascota));

        CitaDTO result = citaService.cambiarEstado(CITA_ID,
                CambiarEstadoCitaRequestDTO.builder().nuevoEstado(EstadoCita.EN_ATENCION).build());

        assertThat(result.getEstado()).isEqualTo(EstadoCita.EN_ATENCION);
    }

    // ── AA: ADMIN puede todas las transiciones → éxito ───────────────────────

    @Test
    @DisplayName("AA — ADMIN marca ATENDIDA en cita de cualquier profesional → éxito")
    void cambiarEstado_admin_puedeTodas_exito() {
        TenantContext.setCurrentTenant(TENANT_VET);
        SecurityContextHolder.getContext().setAuthentication(
            new UsernamePasswordAuthenticationToken("admin", null,
                List.of(new SimpleGrantedAuthority("ROLE_ADMIN"),
                        new SimpleGrantedAuthority("PERM_CAMBIAR_ESTADO_CITA")))
        );

        Cita cita = buildCita(CITA_ID, EstadoCita.EN_ATENCION, INICIO, FIN);
        cita.setUsuarioTenantId(200L); // cita asignada a otro, pero ADMIN puede todo
        when(citaRepository.findByIdAndTenantId(CITA_ID, TENANT_VET)).thenReturn(Optional.of(cita));
        when(citaRepository.save(any(Cita.class))).thenReturn(cita);
        when(clienteRepository.findById(CLIENTE_1)).thenReturn(Optional.of(cliente));
        when(mascotaRepository.findById(MASCOTA_1)).thenReturn(Optional.of(mascota));

        CitaDTO result = citaService.cambiarEstado(CITA_ID,
                CambiarEstadoCitaRequestDTO.builder().nuevoEstado(EstadoCita.ATENDIDA).build());

        assertThat(result.getEstado()).isEqualTo(EstadoCita.ATENDIDA);
    }

    // ── BB: listar — VETERINARIO dispara resolución de scope (D5) ────────────

    @Test
    @DisplayName("BB — listar con ROLE_VETERINARIO → resuelve membresía para scope")
    void listar_veterinario_resuelveMembresiaParaScope() {
        TenantContext.setCurrentTenant(TENANT_VET);
        // userId=99L → membresía activa (profesional, id=100L) — configurado en @BeforeEach
        SecurityContextHolder.getContext().setAuthentication(
            new UsernamePasswordAuthenticationToken("vet", null,
                List.of(new SimpleGrantedAuthority("ROLE_VETERINARIO")))
        );

        Page<Cita> emptyPage = new PageImpl<>(List.of(),
                PageRequest.of(0, 15, Sort.by("fechaInicio")), 0L);
        when(citaRepository.findAll(any(Specification.class), any(Pageable.class)))
                .thenReturn(emptyPage);

        citaService.listar(0, 15, null, null, null, null, null, null, null, "");

        // Verificar que se consultó la membresía para aplicar el scope (no se confía en el param)
        verify(usuarioTenantRepository).findByUsuarioIdAndTenantIdAndActivoTrue(99L, TENANT_VET);
    }

    // ── CC: GROOMER listar → scope por PELUQUERIA ────────────────────────────

    @Test
    @DisplayName("CC — listar con ROLE_GROOMER → resuelve membresía y filtra PELUQUERIA")
    void listar_groomer_resuelveScope() {
        TenantContext.setCurrentTenant(TENANT_VET);
        SecurityContextHolder.getContext().setAuthentication(
            new UsernamePasswordAuthenticationToken("groom", null,
                List.of(new SimpleGrantedAuthority("ROLE_GROOMER")))
        );
        Page<Cita> emptyPage = new PageImpl<>(List.of(),
                PageRequest.of(0, 15, Sort.by("fechaInicio")), 0L);
        when(citaRepository.findAll(any(Specification.class), any(Pageable.class)))
                .thenReturn(emptyPage);

        citaService.listar(0, 15, null, null, null, null, null, null, null, "");

        verify(usuarioTenantRepository).findByUsuarioIdAndTenantIdAndActivoTrue(99L, TENANT_VET);
    }

    // ── DD: VETERINARIO con sucursal en membresía → scope usa sucursal ────────

    @Test
    @DisplayName("DD — listar VETERINARIO con sucursal en membresía → scope con sucursal forzada")
    void listar_veterinario_conSucursalEnMembresia_forzaSucursal() {
        TenantContext.setCurrentTenant(TENANT_VET);
        final Long SUCURSAL_1 = 5L;
        UsuarioTenant vetConSucursal = UsuarioTenant.builder()
                .id(PROF_1).tenantId(TENANT_VET).activo(true).sucursalId(SUCURSAL_1).build();
        when(usuarioTenantRepository.findByUsuarioIdAndTenantIdAndActivoTrue(99L, TENANT_VET))
                .thenReturn(Optional.of(vetConSucursal));
        SecurityContextHolder.getContext().setAuthentication(
            new UsernamePasswordAuthenticationToken("vet", null,
                List.of(new SimpleGrantedAuthority("ROLE_VETERINARIO")))
        );
        Page<Cita> emptyPage = new PageImpl<>(List.of(),
                PageRequest.of(0, 15, Sort.by("fechaInicio")), 0L);
        when(citaRepository.findAll(any(Specification.class), any(Pageable.class)))
                .thenReturn(emptyPage);

        citaService.listar(0, 15, null, null, null, null, null, null, null, "");

        verify(usuarioTenantRepository).findByUsuarioIdAndTenantIdAndActivoTrue(99L, TENANT_VET);
    }

    // ── EE: AUXILIAR_VETERINARIO → sin restricción de scope ──────────────────

    @Test
    @DisplayName("EE — listar AUXILIAR_VETERINARIO → acceso completo sin scope personal")
    void listar_auxiliarVeterinario_accesoCompleto() {
        TenantContext.setCurrentTenant(TENANT_VET);
        SecurityContextHolder.getContext().setAuthentication(
            new UsernamePasswordAuthenticationToken("aux", null,
                List.of(new SimpleGrantedAuthority("ROLE_AUXILIAR_VETERINARIO"),
                        new SimpleGrantedAuthority("PERM_VER_CITAS")))
        );
        Cita c1 = buildCita(1L, EstadoCita.PENDIENTE, INICIO, FIN);
        Page<Cita> page = new PageImpl<>(List.of(c1),
                PageRequest.of(0, 15, Sort.by("fechaInicio")), 1L);
        when(citaRepository.findAll(any(Specification.class), any(Pageable.class))).thenReturn(page);
        when(clienteRepository.findById(CLIENTE_1)).thenReturn(Optional.of(cliente));
        when(mascotaRepository.findById(MASCOTA_1)).thenReturn(Optional.of(mascota));

        CitaPageResponseDTO result = citaService.listar(
                0, 15, null, null, null, null, null, null, null, "");

        assertThat(result.getContent()).hasSize(1);
        // AUXILIAR no llama a resolverMembresiaActual — no mocks de usuarioTenantRepository necesarios
        verify(usuarioTenantRepository, never()).findByUsuarioIdAndTenantIdAndActivoTrue(99L, TENANT_VET);
    }

    // ── FF: sucursal inválida en crear → ResourceNotFoundException ────────────

    @Test
    @DisplayName("FF — crear cita con sucursal inexistente → ResourceNotFoundException")
    void crear_sucursalInexistente_falla() {
        final Long SUCURSAL_INEXISTENTE = 999L;
        TenantContext.setCurrentTenant(TENANT_VET);
        TenantContext.setCurrentUserId(99L);
        when(clienteRepository.findByIdAndTenantId(CLIENTE_1, TENANT_VET)).thenReturn(Optional.of(cliente));
        when(mascotaRepository.findByIdAndTenantId(MASCOTA_1, TENANT_VET)).thenReturn(Optional.of(mascota));
        when(citaRepository.findSolapamiento(any(), any(), any(), any(), any())).thenReturn(List.of());
        when(sucursalRepository.findByIdAndTenantId(SUCURSAL_INEXISTENTE, TENANT_VET))
                .thenReturn(Optional.empty());

        CrearCitaRequestDTO dto = CrearCitaRequestDTO.builder()
                .clienteId(CLIENTE_1).mascotaId(MASCOTA_1)
                .sucursalId(SUCURSAL_INEXISTENTE)
                .fechaInicio(INICIO).fechaFin(FIN)
                .tipo(TipoCita.CONSULTA).build();

        assertThatThrownBy(() -> citaService.crear(dto))
                .isInstanceOf(ResourceNotFoundException.class)
                .hasMessageContaining("Sucursal");
        verify(citaRepository, never()).save(any());
    }

    // ── GG: sucursal inactiva en crear → BadRequestException ────────────────

    @Test
    @DisplayName("GG — crear cita con sucursal inactiva → BadRequestException")
    void crear_sucursalInactiva_falla() {
        final Long SUCURSAL_INACTIVA = 7L;
        TenantContext.setCurrentTenant(TENANT_VET);
        TenantContext.setCurrentUserId(99L);
        Sucursal sucursalInactiva = Sucursal.builder()
                .id(SUCURSAL_INACTIVA).tenantId(TENANT_VET).nombre("Sucursal B")
                .activo(false).bloqueadaPorPlan(false).build();
        when(clienteRepository.findByIdAndTenantId(CLIENTE_1, TENANT_VET)).thenReturn(Optional.of(cliente));
        when(mascotaRepository.findByIdAndTenantId(MASCOTA_1, TENANT_VET)).thenReturn(Optional.of(mascota));
        when(citaRepository.findSolapamiento(any(), any(), any(), any(), any())).thenReturn(List.of());
        when(sucursalRepository.findByIdAndTenantId(SUCURSAL_INACTIVA, TENANT_VET))
                .thenReturn(Optional.of(sucursalInactiva));

        CrearCitaRequestDTO dto = CrearCitaRequestDTO.builder()
                .clienteId(CLIENTE_1).mascotaId(MASCOTA_1)
                .sucursalId(SUCURSAL_INACTIVA)
                .fechaInicio(INICIO).fechaFin(FIN)
                .tipo(TipoCita.CONSULTA).build();

        assertThatThrownBy(() -> citaService.crear(dto))
                .isInstanceOf(BadRequestException.class)
                .hasMessageContaining("activa");
        verify(citaRepository, never()).save(any());
    }

    // ── HH: VETERINARIO intenta editar cita de otro profesional → AccessDeniedException ─

    @Test
    @DisplayName("HH — VETERINARIO intenta editar cita de otro profesional → AccessDeniedException")
    void actualizar_veterinario_citaDeOtroProfesional_falla() {
        TenantContext.setCurrentTenant(TENANT_VET);
        SecurityContextHolder.getContext().setAuthentication(
            new UsernamePasswordAuthenticationToken("vet", null,
                List.of(new SimpleGrantedAuthority("ROLE_VETERINARIO"),
                        new SimpleGrantedAuthority("PERM_EDITAR_CITA")))
        );

        Cita cita = buildCita(CITA_ID, EstadoCita.PENDIENTE, INICIO, FIN);
        cita.setUsuarioTenantId(200L); // cita asignada a otro profesional
        when(citaRepository.findByIdAndTenantId(CITA_ID, TENANT_VET)).thenReturn(Optional.of(cita));

        ActualizarCitaRequestDTO dto = ActualizarCitaRequestDTO.builder()
                .fechaInicio(INICIO).fechaFin(FIN).tipo(TipoCita.CONSULTA).build();

        assertThatThrownBy(() -> citaService.actualizar(CITA_ID, dto))
                .isInstanceOf(AccessDeniedException.class)
                .hasMessageContaining("asignadas");
        verify(citaRepository, never()).save(any());
    }

    // ── II: solapamiento mismo profesional cross-sucursal → BadRequestException ─

    @Test
    @DisplayName("II — mismo profesional bloqueado por solapamiento aunque estén en sucursales distintas")
    void solapamiento_mismoProfesional_crossSucursal_falla() {
        TenantContext.setCurrentTenant(TENANT_VET);
        TenantContext.setCurrentUserId(99L);
        when(clienteRepository.findByIdAndTenantId(CLIENTE_1, TENANT_VET)).thenReturn(Optional.of(cliente));
        when(mascotaRepository.findByIdAndTenantId(MASCOTA_1, TENANT_VET)).thenReturn(Optional.of(mascota));
        when(usuarioTenantRepository.findById(PROF_1)).thenReturn(Optional.of(profesional));
        // findSolapamiento busca por profesionalId + tenantId (sin sucursal) → detecta el conflicto
        Cita citaEnSucursalA = buildCita(5L, EstadoCita.CONFIRMADA, INICIO, FIN);
        citaEnSucursalA.setSucursalId(1L);
        when(citaRepository.findSolapamiento(eq(TENANT_VET), eq(PROF_1), any(), any(), eq(-1L)))
                .thenReturn(List.of(citaEnSucursalA));

        CrearCitaRequestDTO dto = CrearCitaRequestDTO.builder()
                .clienteId(CLIENTE_1).mascotaId(MASCOTA_1)
                .usuarioTenantId(PROF_1)
                .sucursalId(2L) // sucursal diferente
                .fechaInicio(INICIO).fechaFin(FIN)
                .tipo(TipoCita.CONSULTA).build();

        assertThatThrownBy(() -> citaService.crear(dto))
                .isInstanceOf(BadRequestException.class)
                .hasMessageContaining("horario");
    }

    // ── JJ: profesionales distintos en distintas sucursales → no se bloquean ──

    @Test
    @DisplayName("JJ — profesionales distintos en sucursales diferentes no se bloquean")
    void solapamiento_profesionalesDistintos_noBloquea() {
        final Long PROF_2 = 200L;
        TenantContext.setCurrentTenant(TENANT_VET);
        TenantContext.setCurrentUserId(99L);
        UsuarioTenant prof2 = UsuarioTenant.builder().id(PROF_2).tenantId(TENANT_VET).activo(true).build();
        when(clienteRepository.findByIdAndTenantId(CLIENTE_1, TENANT_VET)).thenReturn(Optional.of(cliente));
        when(mascotaRepository.findByIdAndTenantId(MASCOTA_1, TENANT_VET)).thenReturn(Optional.of(mascota));
        when(usuarioTenantRepository.findById(PROF_2)).thenReturn(Optional.of(prof2));
        when(usuarioTenantRepository.findByUsuarioIdAndTenantIdAndActivoTrue(99L, TENANT_VET))
                .thenReturn(Optional.of(profesional));
        // PROF_2 no tiene solapamiento
        when(citaRepository.findSolapamiento(eq(TENANT_VET), eq(PROF_2), any(), any(), eq(-1L)))
                .thenReturn(List.of());

        Cita saved = buildCita(CITA_ID, EstadoCita.PENDIENTE, INICIO, FIN);
        when(citaRepository.save(any(Cita.class))).thenReturn(saved);
        when(clienteRepository.findById(CLIENTE_1)).thenReturn(Optional.of(cliente));
        when(mascotaRepository.findById(MASCOTA_1)).thenReturn(Optional.of(mascota));

        CrearCitaRequestDTO dto = CrearCitaRequestDTO.builder()
                .clienteId(CLIENTE_1).mascotaId(MASCOTA_1)
                .usuarioTenantId(PROF_2)
                .fechaInicio(INICIO).fechaFin(FIN)
                .tipo(TipoCita.CONSULTA).build();

        CitaDTO result = citaService.crear(dto);
        assertThat(result).isNotNull();
    }

    // ── KK: VETERINARIO no puede ver cita de otra sucursal (obtenerPorId) ─────

    @Test
    @DisplayName("KK — VETERINARIO con sucursal X no puede ver cita de sucursal Y")
    void obtenerPorId_veterinario_otraSucursal_falla() {
        TenantContext.setCurrentTenant(TENANT_VET);
        final Long SUCURSAL_VET = 1L;
        final Long SUCURSAL_OTRA = 2L;
        UsuarioTenant vetConSucursal = UsuarioTenant.builder()
                .id(PROF_1).tenantId(TENANT_VET).activo(true).sucursalId(SUCURSAL_VET).build();
        when(usuarioTenantRepository.findByUsuarioIdAndTenantIdAndActivoTrue(99L, TENANT_VET))
                .thenReturn(Optional.of(vetConSucursal));
        SecurityContextHolder.getContext().setAuthentication(
            new UsernamePasswordAuthenticationToken("vet", null,
                List.of(new SimpleGrantedAuthority("ROLE_VETERINARIO")))
        );

        Cita cita = buildCita(CITA_ID, EstadoCita.PENDIENTE, INICIO, FIN);
        cita.setUsuarioTenantId(PROF_1); // cita asignada al mismo VETERINARIO...
        cita.setSucursalId(SUCURSAL_OTRA); // ...pero en otra sucursal
        when(citaRepository.findByIdAndTenantId(CITA_ID, TENANT_VET)).thenReturn(Optional.of(cita));

        assertThatThrownBy(() -> citaService.obtenerPorId(CITA_ID))
                .isInstanceOf(ResourceNotFoundException.class);
    }

    // ── LL: cross-tenant isolation en crear ───────────────────────────────────

    @Test
    @DisplayName("LL — tenant A no puede crear citas con cliente del tenant B")
    void crear_clienteDeOtroTenant_falla() {
        TenantContext.setCurrentTenant(TENANT_VET);
        TenantContext.setCurrentUserId(99L);
        when(clienteRepository.findByIdAndTenantId(CLIENTE_1, TENANT_VET)).thenReturn(Optional.empty());

        CrearCitaRequestDTO dto = CrearCitaRequestDTO.builder()
                .clienteId(CLIENTE_1).mascotaId(MASCOTA_1)
                .fechaInicio(INICIO).fechaFin(FIN)
                .tipo(TipoCita.CONSULTA).build();

        assertThatThrownBy(() -> citaService.crear(dto))
                .isInstanceOf(ResourceNotFoundException.class)
                .hasMessageContaining(String.valueOf(CLIENTE_1));
        verify(citaRepository, never()).save(any());
    }

    // ── MM: cita en sucursal A aparece al filtrar por sucursal A ─────────────

    @Test
    @DisplayName("MM — listar sucursalId=1 → devuelve solo citas de esa sucursal")
    void listar_sucursalFiltrada_devuelveCitasCorrectas() {
        TenantContext.setCurrentTenant(TENANT_VET);
        final Long SUCURSAL_A = 1L;
        Cita c = buildCita(1L, EstadoCita.PENDIENTE, INICIO, FIN);
        c.setSucursalId(SUCURSAL_A);
        Page<Cita> page = new PageImpl<>(List.of(c),
                PageRequest.of(0, 15, Sort.by("fechaInicio")), 1L);
        when(citaRepository.findAll(any(Specification.class), any(Pageable.class))).thenReturn(page);
        when(clienteRepository.findById(CLIENTE_1)).thenReturn(Optional.of(cliente));
        when(mascotaRepository.findById(MASCOTA_1)).thenReturn(Optional.of(mascota));

        CitaPageResponseDTO result = citaService.listar(
                0, 15, null, null, null, null, null, null, SUCURSAL_A, "");

        assertThat(result.getContent()).hasSize(1);
        assertThat(result.getContent().get(0).getSucursalId()).isEqualTo(SUCURSAL_A);
    }

    // ── NN: cita de sucursal B no aparece al filtrar por sucursal A ───────────

    @Test
    @DisplayName("NN — listar sucursalId=1 → no devuelve cita de sucursal 2 (spec filtra por BD)")
    void listar_sucursalB_noAparece_filtroPorSucursalA() {
        TenantContext.setCurrentTenant(TENANT_VET);
        final Long SUCURSAL_A = 1L;
        // El repositorio devuelve vacío porque la BD ya filtra por sucursal_id=1
        Page<Cita> emptyPage = new PageImpl<>(List.of(),
                PageRequest.of(0, 15, Sort.by("fechaInicio")), 0L);
        when(citaRepository.findAll(any(Specification.class), any(Pageable.class))).thenReturn(emptyPage);

        CitaPageResponseDTO result = citaService.listar(
                0, 15, null, null, null, null, null, null, SUCURSAL_A, "");

        assertThat(result.getContent()).isEmpty();
        assertThat(result.getTotalElements()).isEqualTo(0L);
    }

    // ── OO: VETERINARIO no puede ampliar scope enviando sucursalId diferente ─

    @Test
    @DisplayName("OO — VETERINARIO con sucursal=1 pasa sucursalId=2 en URL → backend usa sucursal=1")
    void listar_veterinario_noAmpliaScope_sucursalForzada() {
        TenantContext.setCurrentTenant(TENANT_VET);
        final Long SUCURSAL_VET = 1L;
        UsuarioTenant vetConSucursal = UsuarioTenant.builder()
                .id(PROF_1).tenantId(TENANT_VET).activo(true).sucursalId(SUCURSAL_VET).build();
        when(usuarioTenantRepository.findByUsuarioIdAndTenantIdAndActivoTrue(99L, TENANT_VET))
                .thenReturn(Optional.of(vetConSucursal));
        SecurityContextHolder.getContext().setAuthentication(
            new UsernamePasswordAuthenticationToken("vet", null,
                List.of(new SimpleGrantedAuthority("ROLE_VETERINARIO")))
        );
        Page<Cita> emptyPage = new PageImpl<>(List.of(),
                PageRequest.of(0, 15, Sort.by("fechaInicio")), 0L);
        when(citaRepository.findAll(any(Specification.class), any(Pageable.class))).thenReturn(emptyPage);

        // intenta consultar sucursal=2, pero el backend debe forzar sucursal=1 (la del vet)
        citaService.listar(0, 15, null, null, null, null, null, null, 2L, "");

        // La spec fue llamada con la sucursal del veterinario (1L), no con la del request (2L)
        verify(usuarioTenantRepository).findByUsuarioIdAndTenantIdAndActivoTrue(99L, TENANT_VET);
    }

    // ── PP: listarProfesionalesElegibles devuelve solo activos y del rol correcto

    @Test
    @DisplayName("PP — listarProfesionalesElegibles CONSULTA → devuelve VETERINARIOs activos")
    void listarProfesionalesElegibles_consulta_devuelveVeterinarios() {
        TenantContext.setCurrentTenant(TENANT_VET);
        final Long SUCURSAL_A = 1L;
        Rol rolVet = Rol.builder().id(1L).nombre("VETERINARIO").build();
        UsuarioTenant vet1 = UsuarioTenant.builder().id(10L).tenantId(TENANT_VET).activo(true)
                .sucursalId(SUCURSAL_A).rol(rolVet)
                .usuario(Usuario.builder().id(1L).nombre("Ana García").build())
                .build();
        UsuarioTenant vet2 = UsuarioTenant.builder().id(11L).tenantId(TENANT_VET).activo(true)
                .sucursalId(SUCURSAL_A).rol(rolVet)
                .usuario(Usuario.builder().id(2L).nombre("Luis Soto").build())
                .build();

        when(usuarioTenantRepository.findProfesionalesElegibles(TENANT_VET, "VETERINARIO", SUCURSAL_A))
                .thenReturn(List.of(vet1, vet2));

        List<ProfesionalElegibleDTO> result = citaService.listarProfesionalesElegibles(SUCURSAL_A, TipoCita.CONSULTA);

        assertThat(result).hasSize(2);
        assertThat(result).extracting("rolNombre").containsOnly("VETERINARIO");
        assertThat(result).extracting("usuarioTenantId").containsExactlyInAnyOrder(10L, 11L);
    }

    // ── QQ: vet asignado a sucursal A no aparece en elegibles para sucursal B ─

    @Test
    @DisplayName("QQ — vet de sucursal A no aparece en elegibles para sucursal B")
    void listarProfesionalesElegibles_vetSucursalA_noApareceSucursalB() {
        TenantContext.setCurrentTenant(TENANT_VET);
        final Long SUCURSAL_B = 2L;

        // La query del repositorio ya filtra; devuelve vacío porque el vet es de sucursal A
        when(usuarioTenantRepository.findProfesionalesElegibles(TENANT_VET, "VETERINARIO", SUCURSAL_B))
                .thenReturn(List.of());

        List<ProfesionalElegibleDTO> result = citaService.listarProfesionalesElegibles(SUCURSAL_B, TipoCita.CONSULTA);

        assertThat(result).isEmpty();
    }

    // ── RR: cita CONSULTA con GROOMER → BadRequestException ─────────────────

    @Test
    @DisplayName("RR — cita clínica con profesional GROOMER → BadRequestException")
    void crear_citaClinicaConGroomer_falla() {
        TenantContext.setCurrentTenant(TENANT_VET);
        TenantContext.setCurrentUserId(99L);
        Rol rolGroomer = Rol.builder().id(3L).nombre("GROOMER").build();
        UsuarioTenant groomer = UsuarioTenant.builder()
                .id(PROF_1).tenantId(TENANT_VET).activo(true).rol(rolGroomer).build();

        when(clienteRepository.findByIdAndTenantId(CLIENTE_1, TENANT_VET)).thenReturn(Optional.of(cliente));
        when(mascotaRepository.findByIdAndTenantId(MASCOTA_1, TENANT_VET)).thenReturn(Optional.of(mascota));
        when(usuarioTenantRepository.findById(PROF_1)).thenReturn(Optional.of(groomer));

        CrearCitaRequestDTO dto = CrearCitaRequestDTO.builder()
                .clienteId(CLIENTE_1).mascotaId(MASCOTA_1)
                .usuarioTenantId(PROF_1)
                .fechaInicio(INICIO).fechaFin(FIN)
                .tipo(TipoCita.CONSULTA).build();

        assertThatThrownBy(() -> citaService.crear(dto))
                .isInstanceOf(BadRequestException.class)
                .hasMessageContaining("GROOMER");
        verify(citaRepository, never()).save(any());
    }

    // ── SS: cita PELUQUERIA con VETERINARIO → BadRequestException ────────────

    @Test
    @DisplayName("SS — cita PELUQUERIA con profesional VETERINARIO → BadRequestException")
    void crear_citaPeluqueriaConVeterinario_falla() {
        TenantContext.setCurrentTenant(TENANT_VET);
        TenantContext.setCurrentUserId(99L);
        Rol rolVet = Rol.builder().id(1L).nombre("VETERINARIO").build();
        UsuarioTenant vet = UsuarioTenant.builder()
                .id(PROF_1).tenantId(TENANT_VET).activo(true).rol(rolVet).build();

        when(clienteRepository.findByIdAndTenantId(CLIENTE_1, TENANT_VET)).thenReturn(Optional.of(cliente));
        when(mascotaRepository.findByIdAndTenantId(MASCOTA_1, TENANT_VET)).thenReturn(Optional.of(mascota));
        when(usuarioTenantRepository.findById(PROF_1)).thenReturn(Optional.of(vet));

        CrearCitaRequestDTO dto = CrearCitaRequestDTO.builder()
                .clienteId(CLIENTE_1).mascotaId(MASCOTA_1)
                .usuarioTenantId(PROF_1)
                .fechaInicio(INICIO).fechaFin(FIN)
                .tipo(TipoCita.PELUQUERIA).build();

        assertThatThrownBy(() -> citaService.crear(dto))
                .isInstanceOf(BadRequestException.class)
                .hasMessageContaining("PELUQUERIA");
        verify(citaRepository, never()).save(any());
    }

    // ── TT: citas con usuarioTenantId=null no rompen la carga de la agenda ───

    @Test
    @DisplayName("TT — citas con usuarioTenantId=null se cargan correctamente sin NPE")
    void listar_citasSinProfesionalAsignado_cargaCorrectamente() {
        TenantContext.setCurrentTenant(TENANT_VET);
        Cita citaSinProf = buildCita(1L, EstadoCita.PENDIENTE, INICIO, FIN);
        citaSinProf.setUsuarioTenantId(null); // sin profesional asignado

        Page<Cita> page = new PageImpl<>(List.of(citaSinProf),
                PageRequest.of(0, 15, Sort.by("fechaInicio")), 1L);
        when(citaRepository.findAll(any(Specification.class), any(Pageable.class))).thenReturn(page);
        when(clienteRepository.findById(CLIENTE_1)).thenReturn(Optional.of(cliente));
        when(mascotaRepository.findById(MASCOTA_1)).thenReturn(Optional.of(mascota));

        CitaPageResponseDTO result = citaService.listar(
                0, 15, null, null, null, null, null, null, null, "");

        assertThat(result.getContent()).hasSize(1);
        assertThat(result.getContent().get(0).getUsuarioTenantId()).isNull();
        assertThat(result.getContent().get(0).getProfesionalNombre()).isNull();
    }

    // ── helpers ───────────────────────────────────────────────────────────────

    private Cita buildCita(Long id, EstadoCita estado, LocalDateTime inicio, LocalDateTime fin) {
        return Cita.builder()
                .id(id)
                .tenantId(TENANT_VET)
                .clienteId(CLIENTE_1)
                .mascotaId(MASCOTA_1)
                .fechaInicio(inicio)
                .fechaFin(fin)
                .tipo(TipoCita.CONSULTA)
                .estado(estado)
                .createdAt(LocalDateTime.now())
                .updatedAt(LocalDateTime.now())
                .build();
    }
}
