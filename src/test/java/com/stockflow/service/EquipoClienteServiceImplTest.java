package com.stockflow.service;

import com.stockflow.util.TenantContext;
import com.stockflow.dto.ActualizarEquipoRequestDTO;
import com.stockflow.dto.CrearEquipoRequestDTO;
import com.stockflow.dto.EquipoClienteDTO;
import com.stockflow.dto.EquipoPageResponseDTO;
import com.stockflow.dto.EquipoResumenDTO;
import com.stockflow.dto.EquipoStatsDTO;
import com.stockflow.entity.Cliente;
import com.stockflow.entity.EquipoCliente;
import com.stockflow.exception.ConflictException;
import com.stockflow.exception.ResourceNotFoundException;
import com.stockflow.mapper.EquipoClienteMapper;
import com.stockflow.repository.ClienteRepository;
import com.stockflow.repository.EquipoClienteRepository;
import com.stockflow.service.impl.EquipoClienteServiceImpl;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
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

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.ArgumentMatchers.isNull;
import static org.mockito.Mockito.doNothing;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class EquipoClienteServiceImplTest {

    private static final String TENANT_SERV = "serv-ab12";
    private static final String TENANT_OTRO = "otro-cd34";
    private static final Long CLIENTE_1     = 1L;
    private static final Long EQUIPO_10     = 10L;

    @Mock private EquipoClienteRepository equipoRepository;
    @Mock private ClienteRepository clienteRepository;
    @Mock private EquipoClienteMapper mapper;
    @Mock private TenantService tenantService;

    @InjectMocks private EquipoClienteServiceImpl service;

    private Cliente cliente;
    private EquipoCliente equipo;
    private EquipoClienteDTO equipoDTO;

    @BeforeEach
    void setUp() {
        cliente = Cliente.builder()
                .id(CLIENTE_1)
                .tenantId(TENANT_SERV)
                .nombre("Empresa ABC")
                .build();

        equipo = EquipoCliente.builder()
                .id(EQUIPO_10)
                .tenantId(TENANT_SERV)
                .cliente(cliente)
                .nombre("Aire acondicionado")
                .tipo("CLIMATIZACION")
                .marca("Carrier")
                .modelo("X200")
                .numeroSerie("SN-001")
                .activo(true)
                .createdAt(LocalDateTime.now())
                .build();

        equipoDTO = EquipoClienteDTO.builder()
                .id(EQUIPO_10)
                .tenantId(TENANT_SERV)
                .clienteId(CLIENTE_1)
                .clienteNombre("Empresa ABC")
                .nombre("Aire acondicionado")
                .activo(true)
                .build();

        doNothing().when(tenantService).requireRubro(anyString(), anyString());
        when(mapper.toDTO(any(EquipoCliente.class))).thenReturn(equipoDTO);
    }

    @AfterEach
    void tearDown() {
        TenantContext.clear();
    }

    // ── getStats ─────────────────────────────────────────────────────────────

    @Test
    void getStats_devuelveConteos() {
        TenantContext.setCurrentTenant(TENANT_SERV);
        when(equipoRepository.countTotal(TENANT_SERV)).thenReturn(5L);
        when(equipoRepository.countActivos(TENANT_SERV)).thenReturn(3L);
        when(equipoRepository.countInactivos(TENANT_SERV)).thenReturn(2L);
        when(equipoRepository.countClientesConEquipos(TENANT_SERV)).thenReturn(2L);

        EquipoStatsDTO stats = service.getStats();

        assertThat(stats.getTotal()).isEqualTo(5);
        assertThat(stats.getActivos()).isEqualTo(3);
        assertThat(stats.getInactivos()).isEqualTo(2);
        assertThat(stats.getClientesConEquipos()).isEqualTo(2);
    }

    // ── listar ────────────────────────────────────────────────────────────────

    @Test
    void listar_estadoActivos_llamaQueryCorrecta() {
        TenantContext.setCurrentTenant(TENANT_SERV);
        Page<EquipoResumenDTO> mockPage = new PageImpl<>(List.of());
        when(equipoRepository.findPaginadoActivos(eq(TENANT_SERV), isNull(), anyString(), any(PageRequest.class)))
                .thenReturn(mockPage);
        when(equipoRepository.countTotal(TENANT_SERV)).thenReturn(0L);
        when(equipoRepository.countActivos(TENANT_SERV)).thenReturn(0L);
        when(equipoRepository.countClientesConEquipos(TENANT_SERV)).thenReturn(0L);

        EquipoPageResponseDTO result = service.listar(0, 20, "ACTIVOS", null, null);

        assertThat(result).isNotNull();
        verify(equipoRepository).findPaginadoActivos(eq(TENANT_SERV), isNull(), eq(""), any(PageRequest.class));
        verify(equipoRepository, never()).findPaginadoInactivos(anyString(), anyLong(), anyString(), any());
    }

    @Test
    void listar_estadoTodos_llamaQueryGeneral() {
        TenantContext.setCurrentTenant(TENANT_SERV);
        Page<EquipoResumenDTO> mockPage = new PageImpl<>(List.of());
        when(equipoRepository.findPaginadoTodos(eq(TENANT_SERV), isNull(), anyString(), any(PageRequest.class)))
                .thenReturn(mockPage);
        when(equipoRepository.countTotal(TENANT_SERV)).thenReturn(0L);
        when(equipoRepository.countActivos(TENANT_SERV)).thenReturn(0L);
        when(equipoRepository.countClientesConEquipos(TENANT_SERV)).thenReturn(0L);

        service.listar(0, 20, null, null, null);

        verify(equipoRepository).findPaginadoTodos(eq(TENANT_SERV), isNull(), eq(""), any(PageRequest.class));
    }

    // ── obtenerPorId ──────────────────────────────────────────────────────────

    @Test
    void obtenerPorId_existente_devuelveDTO() {
        TenantContext.setCurrentTenant(TENANT_SERV);
        when(equipoRepository.findByIdAndTenantId(EQUIPO_10, TENANT_SERV)).thenReturn(Optional.of(equipo));

        EquipoClienteDTO dto = service.obtenerPorId(EQUIPO_10);

        assertThat(dto).isNotNull();
        assertThat(dto.getId()).isEqualTo(EQUIPO_10);
    }

    @Test
    void obtenerPorId_noExiste_lanzaResourceNotFound() {
        TenantContext.setCurrentTenant(TENANT_SERV);
        when(equipoRepository.findByIdAndTenantId(EQUIPO_10, TENANT_SERV)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> service.obtenerPorId(EQUIPO_10))
                .isInstanceOf(ResourceNotFoundException.class);
    }

    // ── crear ─────────────────────────────────────────────────────────────────

    @Test
    void crear_sinDuplicadoSerie_guardaEquipo() {
        TenantContext.setCurrentTenant(TENANT_SERV);
        when(clienteRepository.findByIdAndTenantId(CLIENTE_1, TENANT_SERV)).thenReturn(Optional.of(cliente));
        when(equipoRepository.existsByNumeroSerieAndTenantId("SN-001", TENANT_SERV)).thenReturn(false);
        when(equipoRepository.save(any(EquipoCliente.class))).thenReturn(equipo);

        CrearEquipoRequestDTO req = CrearEquipoRequestDTO.builder()
                .clienteId(CLIENTE_1)
                .nombre("Aire acondicionado")
                .numeroSerie("SN-001")
                .build();

        EquipoClienteDTO dto = service.crear(req);

        assertThat(dto).isNotNull();
        verify(equipoRepository).save(any(EquipoCliente.class));
    }

    @Test
    void crear_serieBlanca_seGuardaComoNull() {
        TenantContext.setCurrentTenant(TENANT_SERV);
        when(clienteRepository.findByIdAndTenantId(CLIENTE_1, TENANT_SERV)).thenReturn(Optional.of(cliente));
        when(equipoRepository.save(any(EquipoCliente.class))).thenReturn(equipo);

        CrearEquipoRequestDTO req = CrearEquipoRequestDTO.builder()
                .clienteId(CLIENTE_1)
                .nombre("Equipo sin serie")
                .numeroSerie("   ")
                .build();

        service.crear(req);

        verify(equipoRepository, never()).existsByNumeroSerieAndTenantId(anyString(), anyString());
        verify(equipoRepository).save(any(EquipoCliente.class));
    }

    @Test
    void crear_serieDuplicada_lanzaConflict() {
        TenantContext.setCurrentTenant(TENANT_SERV);
        when(clienteRepository.findByIdAndTenantId(CLIENTE_1, TENANT_SERV)).thenReturn(Optional.of(cliente));
        when(equipoRepository.existsByNumeroSerieAndTenantId("SN-001", TENANT_SERV)).thenReturn(true);

        CrearEquipoRequestDTO req = CrearEquipoRequestDTO.builder()
                .clienteId(CLIENTE_1)
                .nombre("Otro equipo")
                .numeroSerie("SN-001")
                .build();

        assertThatThrownBy(() -> service.crear(req))
                .isInstanceOf(ConflictException.class)
                .hasMessageContaining("SN-001");
    }

    @Test
    void crear_clienteNoExiste_lanzaResourceNotFound() {
        TenantContext.setCurrentTenant(TENANT_SERV);
        when(clienteRepository.findByIdAndTenantId(CLIENTE_1, TENANT_SERV)).thenReturn(Optional.empty());

        CrearEquipoRequestDTO req = CrearEquipoRequestDTO.builder()
                .clienteId(CLIENTE_1)
                .nombre("Equipo")
                .build();

        assertThatThrownBy(() -> service.crear(req))
                .isInstanceOf(ResourceNotFoundException.class);
    }

    // ── actualizar ────────────────────────────────────────────────────────────

    @Test
    void actualizar_serieDuplicadaEnOtroEquipo_lanzaConflict() {
        TenantContext.setCurrentTenant(TENANT_SERV);
        when(equipoRepository.findByIdAndTenantId(EQUIPO_10, TENANT_SERV)).thenReturn(Optional.of(equipo));
        when(equipoRepository.existsByNumeroSerieAndTenantIdAndIdNot("SN-999", TENANT_SERV, EQUIPO_10)).thenReturn(true);

        ActualizarEquipoRequestDTO req = ActualizarEquipoRequestDTO.builder()
                .nombre("Aire acondicionado")
                .numeroSerie("SN-999")
                .build();

        assertThatThrownBy(() -> service.actualizar(EQUIPO_10, req))
                .isInstanceOf(ConflictException.class);
    }

    @Test
    void actualizar_mismaSeriePropio_noLanzaConflict() {
        TenantContext.setCurrentTenant(TENANT_SERV);
        when(equipoRepository.findByIdAndTenantId(EQUIPO_10, TENANT_SERV)).thenReturn(Optional.of(equipo));
        when(equipoRepository.existsByNumeroSerieAndTenantIdAndIdNot("SN-001", TENANT_SERV, EQUIPO_10)).thenReturn(false);
        when(equipoRepository.save(any())).thenReturn(equipo);

        ActualizarEquipoRequestDTO req = ActualizarEquipoRequestDTO.builder()
                .nombre("Aire acondicionado v2")
                .numeroSerie("SN-001")
                .build();

        EquipoClienteDTO dto = service.actualizar(EQUIPO_10, req);

        assertThat(dto).isNotNull();
        verify(equipoRepository).save(any());
    }

    // ── activar / desactivar ──────────────────────────────────────────────────

    @Test
    void activar_equipo_setActivoTrue() {
        TenantContext.setCurrentTenant(TENANT_SERV);
        equipo.setActivo(false);
        when(equipoRepository.findByIdAndTenantId(EQUIPO_10, TENANT_SERV)).thenReturn(Optional.of(equipo));
        when(equipoRepository.save(any())).thenReturn(equipo);

        service.activar(EQUIPO_10);

        assertThat(equipo.getActivo()).isTrue();
        verify(equipoRepository).save(equipo);
    }

    @Test
    void desactivar_equipo_setActivoFalse() {
        TenantContext.setCurrentTenant(TENANT_SERV);
        when(equipoRepository.findByIdAndTenantId(EQUIPO_10, TENANT_SERV)).thenReturn(Optional.of(equipo));
        when(equipoRepository.save(any())).thenReturn(equipo);

        service.desactivar(EQUIPO_10);

        assertThat(equipo.getActivo()).isFalse();
        verify(equipoRepository).save(equipo);
    }
}
