package com.stockflow.service;

import com.stockflow.dto.*;
import com.stockflow.entity.Cliente;
import com.stockflow.entity.Mascota;
import com.stockflow.exception.ConflictException;
import com.stockflow.exception.ResourceNotFoundException;
import com.stockflow.mapper.MascotaMapper;
import com.stockflow.repository.ClienteRepository;
import com.stockflow.repository.MascotaRepository;
import com.stockflow.service.impl.MascotaServiceImpl;
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
import org.springframework.data.domain.Sort;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
@DisplayName("MascotaServiceImpl — tenant isolation y reglas de negocio")
class MascotaServiceImplTest {

    private static final String TENANT_VET   = "vet-ca35";
    private static final String TENANT_JUAN  = "juan-sac-95e4";
    private static final Long   CLIENTE_10   = 10L;
    private static final Long   CLIENTE_20   = 20L;
    private static final Long   MASCOTA_50   = 50L;
    private static final String MICROCHIP_A  = "985141000123456";

    @Mock private MascotaRepository  mascotaRepository;
    @Mock private ClienteRepository  clienteRepository;
    @Mock private MascotaMapper      mascotaMapper;
    @Mock private TenantService      tenantService;

    @InjectMocks private MascotaServiceImpl mascotaService;

    private Cliente clienteVet;
    private Cliente clienteJuan;
    private Mascota mascotaVet;

    @BeforeEach
    void setUp() {
        clienteVet  = Cliente.builder().id(CLIENTE_10).nombre("Pedro Pérez").tenantId(TENANT_VET).activo(true).build();
        clienteJuan = Cliente.builder().id(CLIENTE_20).nombre("Ana López").tenantId(TENANT_JUAN).activo(true).build();

        mascotaVet = Mascota.builder()
                .id(MASCOTA_50).tenantId(TENANT_VET).cliente(clienteVet)
                .nombre("Toby").especie("PERRO").raza("Labrador").activo(true)
                .createdAt(LocalDateTime.now()).build();

        doNothing().when(tenantService).requireRubro(anyString(), any(String[].class));

        when(mascotaMapper.toDTO(any(Mascota.class))).thenAnswer(inv -> {
            Mascota m = inv.getArgument(0);
            return MascotaDTO.builder()
                    .id(m.getId())
                    .tenantId(m.getTenantId())
                    .clienteId(m.getCliente() != null ? m.getCliente().getId() : null)
                    .nombre(m.getNombre())
                    .especie(m.getEspecie())
                    .raza(m.getRaza())
                    .sexo(m.getSexo())
                    .pesoKg(m.getPesoKg())
                    .microchip(m.getMicrochip())
                    .esterilizado(m.getEsterilizado())
                    .activo(m.getActivo())
                    .build();
        });
    }

    @AfterEach
    void tearDown() {
        TenantContext.clear();
    }

    // ── A: crear mascota en Vet ───────────────────────────────────────────────

    @Test
    @DisplayName("A — crear mascota en tenant Vet → éxito")
    void crearMascota_enVet_exito() {
        TenantContext.setCurrentTenant(TENANT_VET);
        when(clienteRepository.findByIdAndTenantId(CLIENTE_10, TENANT_VET))
                .thenReturn(Optional.of(clienteVet));
        when(mascotaRepository.save(any(Mascota.class))).thenReturn(mascotaVet);

        CrearMascotaRequestDTO dto = CrearMascotaRequestDTO.builder()
                .clienteId(CLIENTE_10).nombre("Toby").especie("PERRO").build();

        MascotaDTO result = mascotaService.crearMascota(dto);

        assertThat(result.getNombre()).isEqualTo("Toby");
        assertThat(result.getTenantId()).isEqualTo(TENANT_VET);
        verify(mascotaRepository).save(any(Mascota.class));
    }

    // ── B: listar mascotas de Vet ─────────────────────────────────────────────

    @Test
    @DisplayName("B — listar(ACTIVAS) → usa findPaginadoActivas una sola vez")
    void listar_activas_usaFindPaginadoActivas_unaVez() {
        TenantContext.setCurrentTenant(TENANT_VET);
        MascotaResumenDTO resumen = mockResumen(MASCOTA_50, CLIENTE_10, "Pedro Pérez", "Toby", "PERRO");
        when(mascotaRepository.findPaginadoActivas(eq(TENANT_VET), eq(""), any()))
                .thenReturn(mockPage(List.of(resumen)));
        when(mascotaRepository.countResumenTotal(TENANT_VET, "")).thenReturn(1L);
        when(mascotaRepository.countResumenActivas(TENANT_VET, "")).thenReturn(1L);

        MascotaPageResponseDTO result = mascotaService.listar(0, 15, "ACTIVAS", "");

        assertThat(result.getContent()).hasSize(1);
        verify(mascotaRepository, times(1)).findPaginadoActivas(eq(TENANT_VET), eq(""), any());
        verify(mascotaRepository, never()).findPaginadoTodos(any(), any(), any());
        verify(mascotaRepository, never()).findPaginadoInactivas(any(), any(), any());
    }

    // ── C: obtener mascota por ID dentro de Vet ───────────────────────────────

    @Test
    @DisplayName("C — obtener mascota por ID → tenant correcto → éxito")
    void obtenerPorId_tenantCorrecto_exito() {
        TenantContext.setCurrentTenant(TENANT_VET);
        when(mascotaRepository.findByIdAndTenantId(MASCOTA_50, TENANT_VET))
                .thenReturn(Optional.of(mascotaVet));

        MascotaDTO result = mascotaService.obtenerPorId(MASCOTA_50);

        assertThat(result.getId()).isEqualTo(MASCOTA_50);
    }

    // ── D: obtener mascota de otro tenant → no permitir ──────────────────────

    @Test
    @DisplayName("D — obtener mascota de otro tenant → ResourceNotFoundException")
    void obtenerPorId_otroTenant_lanzaNotFound() {
        TenantContext.setCurrentTenant(TENANT_JUAN);
        when(mascotaRepository.findByIdAndTenantId(MASCOTA_50, TENANT_JUAN))
                .thenReturn(Optional.empty());

        assertThatThrownBy(() -> mascotaService.obtenerPorId(MASCOTA_50))
                .isInstanceOf(ResourceNotFoundException.class);
    }

    // ── E: crear mascota con cliente de otro tenant → no permitir ────────────

    @Test
    @DisplayName("E — crear mascota con cliente de otro tenant → ResourceNotFoundException")
    void crearMascota_clienteOtroTenant_lanzaNotFound() {
        TenantContext.setCurrentTenant(TENANT_VET);
        // El cliente 20 pertenece a Juan, no a Vet
        when(clienteRepository.findByIdAndTenantId(CLIENTE_20, TENANT_VET))
                .thenReturn(Optional.empty());

        CrearMascotaRequestDTO dto = CrearMascotaRequestDTO.builder()
                .clienteId(CLIENTE_20).nombre("Luna").especie("GATO").build();

        assertThatThrownBy(() -> mascotaService.crearMascota(dto))
                .isInstanceOf(ResourceNotFoundException.class)
                .hasMessageContaining(String.valueOf(CLIENTE_20));
    }

    // ── F: listar mascotas de un cliente ─────────────────────────────────────

    @Test
    @DisplayName("F — listar mascotas de cliente → solo las de ese cliente en el tenant")
    void listarPorCliente_devuelveListaCorrecta() {
        TenantContext.setCurrentTenant(TENANT_VET);
        when(clienteRepository.findByIdAndTenantId(CLIENTE_10, TENANT_VET))
                .thenReturn(Optional.of(clienteVet));
        MascotaResumenDTO resumen = mockResumen(MASCOTA_50, CLIENTE_10, "Pedro Pérez", "Toby", "PERRO");
        when(mascotaRepository.findResumenByClienteIdAndTenantId(CLIENTE_10, TENANT_VET))
                .thenReturn(List.of(resumen));

        List<MascotaResumenDTO> result = mascotaService.listarPorCliente(CLIENTE_10);

        assertThat(result).hasSize(1);
        assertThat(result.get(0).getClienteId()).isEqualTo(CLIENTE_10);
    }

    // ── G: mismo nombre en diferentes tenants → permitido ────────────────────

    @Test
    @DisplayName("G — mismo nombre de mascota en diferentes tenants → permitido")
    void mismaNombreEnDiferentesTenants_permitido() {
        // Vet crea "Toby"
        TenantContext.setCurrentTenant(TENANT_VET);
        when(clienteRepository.findByIdAndTenantId(CLIENTE_10, TENANT_VET))
                .thenReturn(Optional.of(clienteVet));
        when(mascotaRepository.save(any(Mascota.class))).thenReturn(mascotaVet);
        mascotaService.crearMascota(CrearMascotaRequestDTO.builder()
                .clienteId(CLIENTE_10).nombre("Toby").especie("PERRO").build());

        // Juan crea también "Toby" — no debe haber conflicto
        TenantContext.setCurrentTenant(TENANT_JUAN);
        Mascota mascotaJuan = Mascota.builder().id(99L).tenantId(TENANT_JUAN)
                .cliente(clienteJuan).nombre("Toby").especie("GATO").activo(true)
                .createdAt(LocalDateTime.now()).build();
        when(clienteRepository.findByIdAndTenantId(CLIENTE_20, TENANT_JUAN))
                .thenReturn(Optional.of(clienteJuan));
        when(mascotaRepository.save(any(Mascota.class))).thenReturn(mascotaJuan);

        MascotaDTO result = mascotaService.crearMascota(CrearMascotaRequestDTO.builder()
                .clienteId(CLIENTE_20).nombre("Toby").especie("GATO").build());

        assertThat(result.getNombre()).isEqualTo("Toby");
        assertThat(result.getTenantId()).isEqualTo(TENANT_JUAN);
    }

    // ── H: mismo microchip en dos tenants → permitido ────────────────────────

    @Test
    @DisplayName("H — mismo microchip en dos tenants → permitido")
    void mismoMicrochipEnDiferentesTenants_permitido() {
        // Vet — microchip no existe en Vet
        TenantContext.setCurrentTenant(TENANT_VET);
        when(clienteRepository.findByIdAndTenantId(CLIENTE_10, TENANT_VET))
                .thenReturn(Optional.of(clienteVet));
        when(mascotaRepository.existsByMicrochipAndTenantId(MICROCHIP_A, TENANT_VET))
                .thenReturn(false);
        Mascota mVet = Mascota.builder().id(MASCOTA_50).tenantId(TENANT_VET)
                .cliente(clienteVet).nombre("Max").especie("PERRO").microchip(MICROCHIP_A)
                .activo(true).createdAt(LocalDateTime.now()).build();
        when(mascotaRepository.save(any(Mascota.class))).thenReturn(mVet);

        MascotaDTO r1 = mascotaService.crearMascota(CrearMascotaRequestDTO.builder()
                .clienteId(CLIENTE_10).nombre("Max").especie("PERRO").microchip(MICROCHIP_A).build());
        assertThat(r1.getMicrochip()).isEqualTo(MICROCHIP_A);

        // Juan — el mismo microchip no existe en Juan SAC
        TenantContext.setCurrentTenant(TENANT_JUAN);
        when(clienteRepository.findByIdAndTenantId(CLIENTE_20, TENANT_JUAN))
                .thenReturn(Optional.of(clienteJuan));
        when(mascotaRepository.existsByMicrochipAndTenantId(MICROCHIP_A, TENANT_JUAN))
                .thenReturn(false);
        Mascota mJuan = Mascota.builder().id(99L).tenantId(TENANT_JUAN)
                .cliente(clienteJuan).nombre("Max").especie("PERRO").microchip(MICROCHIP_A)
                .activo(true).createdAt(LocalDateTime.now()).build();
        when(mascotaRepository.save(any(Mascota.class))).thenReturn(mJuan);

        MascotaDTO r2 = mascotaService.crearMascota(CrearMascotaRequestDTO.builder()
                .clienteId(CLIENTE_20).nombre("Max").especie("PERRO").microchip(MICROCHIP_A).build());
        assertThat(r2.getMicrochip()).isEqualTo(MICROCHIP_A);
    }

    // ── I: mismo microchip dentro del mismo tenant → rechazado ───────────────

    @Test
    @DisplayName("I — mismo microchip dentro del mismo tenant → ConflictException")
    void mismoMicrochipMismoTenant_rechazado() {
        TenantContext.setCurrentTenant(TENANT_VET);
        when(clienteRepository.findByIdAndTenantId(CLIENTE_10, TENANT_VET))
                .thenReturn(Optional.of(clienteVet));
        when(mascotaRepository.existsByMicrochipAndTenantId(MICROCHIP_A, TENANT_VET))
                .thenReturn(true); // ya existe

        assertThatThrownBy(() -> mascotaService.crearMascota(CrearMascotaRequestDTO.builder()
                .clienteId(CLIENTE_10).nombre("Luna").especie("GATO").microchip(MICROCHIP_A).build()))
                .isInstanceOf(ConflictException.class)
                .hasMessageContaining(MICROCHIP_A);
    }

    // ── J: desactivar mascota → desaparece del listado activo ────────────────

    @Test
    @DisplayName("J — desactivar mascota → activo=false en DTO")
    void desactivarMascota_activoFalse() {
        TenantContext.setCurrentTenant(TENANT_VET);
        when(mascotaRepository.findByIdAndTenantId(MASCOTA_50, TENANT_VET))
                .thenReturn(Optional.of(mascotaVet));
        Mascota desactivada = Mascota.builder().id(MASCOTA_50).tenantId(TENANT_VET)
                .cliente(clienteVet).nombre("Toby").especie("PERRO").activo(false)
                .createdAt(LocalDateTime.now()).build();
        when(mascotaRepository.save(any(Mascota.class))).thenReturn(desactivada);

        MascotaDTO result = mascotaService.desactivar(MASCOTA_50);

        assertThat(result.getActivo()).isFalse();
        verify(mascotaRepository).save(argThat(m -> !m.getActivo()));
    }

    // ── K: activar mascota → vuelve al listado ────────────────────────────────

    @Test
    @DisplayName("K — activar mascota desactivada → activo=true en DTO")
    void activarMascota_activoTrue() {
        TenantContext.setCurrentTenant(TENANT_VET);
        Mascota inactiva = Mascota.builder().id(MASCOTA_50).tenantId(TENANT_VET)
                .cliente(clienteVet).nombre("Toby").especie("PERRO").activo(false)
                .createdAt(LocalDateTime.now()).build();
        when(mascotaRepository.findByIdAndTenantId(MASCOTA_50, TENANT_VET))
                .thenReturn(Optional.of(inactiva));
        Mascota activada = Mascota.builder().id(MASCOTA_50).tenantId(TENANT_VET)
                .cliente(clienteVet).nombre("Toby").especie("PERRO").activo(true)
                .createdAt(LocalDateTime.now()).build();
        when(mascotaRepository.save(any(Mascota.class))).thenReturn(activada);

        MascotaDTO result = mascotaService.activar(MASCOTA_50);

        assertThat(result.getActivo()).isTrue();
    }

    // ── L: actualizar mascota mantiene tenant ─────────────────────────────────

    @Test
    @DisplayName("L — actualizar mascota → tenantId no cambia")
    void actualizarMascota_tenantIdInmutable() {
        TenantContext.setCurrentTenant(TENANT_VET);
        when(mascotaRepository.findByIdAndTenantId(MASCOTA_50, TENANT_VET))
                .thenReturn(Optional.of(mascotaVet));
        Mascota actualizada = Mascota.builder().id(MASCOTA_50).tenantId(TENANT_VET)
                .cliente(clienteVet).nombre("Tobias").especie("PERRO")
                .activo(true).createdAt(LocalDateTime.now()).build();
        when(mascotaRepository.save(any(Mascota.class))).thenReturn(actualizada);

        ActualizarMascotaRequestDTO dto = ActualizarMascotaRequestDTO.builder()
                .nombre("Tobias").especie("PERRO").build();

        MascotaDTO result = mascotaService.actualizar(MASCOTA_50, dto);

        assertThat(result.getTenantId()).isEqualTo(TENANT_VET);
        assertThat(result.getNombre()).isEqualTo("Tobias");
    }

    // ── M: actualizar mascota de otro tenant → no permitir ───────────────────

    @Test
    @DisplayName("M — actualizar mascota de otro tenant → ResourceNotFoundException")
    void actualizarMascota_otroTenant_lanzaNotFound() {
        TenantContext.setCurrentTenant(TENANT_JUAN);
        when(mascotaRepository.findByIdAndTenantId(MASCOTA_50, TENANT_JUAN))
                .thenReturn(Optional.empty());

        assertThatThrownBy(() -> mascotaService.actualizar(MASCOTA_50,
                ActualizarMascotaRequestDTO.builder().nombre("X").especie("PERRO").build()))
                .isInstanceOf(ResourceNotFoundException.class);
    }

    // ── P: mascota con peso NULL → permitido ─────────────────────────────────

    @Test
    @DisplayName("P — crear mascota con pesoKg null → permitido")
    void crearMascota_pesoNull_permitido() {
        TenantContext.setCurrentTenant(TENANT_VET);
        when(clienteRepository.findByIdAndTenantId(CLIENTE_10, TENANT_VET))
                .thenReturn(Optional.of(clienteVet));
        Mascota sinPeso = Mascota.builder().id(MASCOTA_50).tenantId(TENANT_VET)
                .cliente(clienteVet).nombre("Luna").especie("GATO").pesoKg(null)
                .activo(true).createdAt(LocalDateTime.now()).build();
        when(mascotaRepository.save(any(Mascota.class))).thenReturn(sinPeso);

        CrearMascotaRequestDTO dto = CrearMascotaRequestDTO.builder()
                .clienteId(CLIENTE_10).nombre("Luna").especie("GATO").pesoKg(null).build();

        MascotaDTO result = mascotaService.crearMascota(dto);

        assertThat(result.getPesoKg()).isNull();
    }

    // ── Q: mascota con sexo DESCONOCIDO → permitido ──────────────────────────

    @Test
    @DisplayName("Q — crear mascota con sexo DESCONOCIDO → permitido")
    void crearMascota_sexoDesconocido_permitido() {
        TenantContext.setCurrentTenant(TENANT_VET);
        when(clienteRepository.findByIdAndTenantId(CLIENTE_10, TENANT_VET))
                .thenReturn(Optional.of(clienteVet));
        Mascota m = Mascota.builder().id(MASCOTA_50).tenantId(TENANT_VET)
                .cliente(clienteVet).nombre("Rocky").especie("PERRO").sexo("DESCONOCIDO")
                .activo(true).createdAt(LocalDateTime.now()).build();
        when(mascotaRepository.save(any(Mascota.class))).thenReturn(m);

        CrearMascotaRequestDTO dto = CrearMascotaRequestDTO.builder()
                .clienteId(CLIENTE_10).nombre("Rocky").especie("PERRO").sexo("DESCONOCIDO").build();

        MascotaDTO result = mascotaService.crearMascota(dto);

        assertThat(result.getSexo()).isEqualTo("DESCONOCIDO");
    }

    // ── R: especie libre (IGUANA, CONEJO, etc.) → permitido ──────────────────

    @Test
    @DisplayName("R — especie libre (IGUANA) → permitido sin restricción de enum")
    void crearMascota_especieLibre_permitido() {
        TenantContext.setCurrentTenant(TENANT_VET);
        when(clienteRepository.findByIdAndTenantId(CLIENTE_10, TENANT_VET))
                .thenReturn(Optional.of(clienteVet));
        Mascota iguana = Mascota.builder().id(MASCOTA_50).tenantId(TENANT_VET)
                .cliente(clienteVet).nombre("Verde").especie("IGUANA")
                .activo(true).createdAt(LocalDateTime.now()).build();
        when(mascotaRepository.save(any(Mascota.class))).thenReturn(iguana);

        CrearMascotaRequestDTO dto = CrearMascotaRequestDTO.builder()
                .clienteId(CLIENTE_10).nombre("Verde").especie("IGUANA").build();

        MascotaDTO result = mascotaService.crearMascota(dto);

        assertThat(result.getEspecie()).isEqualTo("IGUANA");
    }

    // ── S: GET /mascotas devuelve clienteNombre ───────────────────────────────

    @Test
    @DisplayName("S — listar(ACTIVAS) devuelve clienteNombre desde la proyección")
    void listar_activas_devuelveClienteNombre() {
        TenantContext.setCurrentTenant(TENANT_VET);
        MascotaResumenDTO resumen = mockResumen(MASCOTA_50, CLIENTE_10, "Pedro Pérez", "Toby", "PERRO");
        when(mascotaRepository.findPaginadoActivas(eq(TENANT_VET), eq(""), any()))
                .thenReturn(mockPage(List.of(resumen)));
        when(mascotaRepository.countResumenTotal(TENANT_VET, "")).thenReturn(1L);
        when(mascotaRepository.countResumenActivas(TENANT_VET, "")).thenReturn(1L);

        MascotaPageResponseDTO result = mascotaService.listar(0, 15, "ACTIVAS", "");

        assertThat(result.getContent().get(0).getClienteNombre()).isEqualTo("Pedro Pérez");
        assertThat(result.getContent().get(0).getClienteId()).isEqualTo(CLIENTE_10);
    }

    // ── T: no hay N+1 en listado ──────────────────────────────────────────────

    @Test
    @DisplayName("T — listar(ACTIVAS) usa findPaginadoActivas (JOIN), no itera clientes")
    void listar_activas_sinNmas1_usaJoinQuery() {
        TenantContext.setCurrentTenant(TENANT_VET);
        when(mascotaRepository.findPaginadoActivas(eq(TENANT_VET), eq(""), any()))
                .thenReturn(mockPage(List.of()));
        when(mascotaRepository.countResumenTotal(TENANT_VET, "")).thenReturn(0L);
        when(mascotaRepository.countResumenActivas(TENANT_VET, "")).thenReturn(0L);

        mascotaService.listar(0, 15, "ACTIVAS", "");

        verify(mascotaRepository, times(1)).findPaginadoActivas(eq(TENANT_VET), eq(""), any());
        verify(clienteRepository, never()).findByIdAndTenantId(any(), any());
        verify(clienteRepository, never()).findById(any());
    }

    // ── U: listar(TODOS) → llama findPaginadoTodos ───────────────────────────

    @Test
    @DisplayName("U — listar(TODOS) llama findPaginadoTodos y devuelve activas + inactivas")
    void listar_todos_llamaFindPaginadoTodos() {
        TenantContext.setCurrentTenant(TENANT_VET);
        MascotaResumenDTO activa   = mockResumen(MASCOTA_50, CLIENTE_10, "Pedro Pérez", "Toby",  "PERRO");
        MascotaResumenDTO inactiva = mockResumenInactiva(99L, CLIENTE_10, "Pedro Pérez", "Luna", "GATO");
        when(mascotaRepository.findPaginadoTodos(eq(TENANT_VET), eq(""), any()))
                .thenReturn(mockPage(List.of(activa, inactiva)));
        when(mascotaRepository.countResumenTotal(TENANT_VET, "")).thenReturn(2L);
        when(mascotaRepository.countResumenActivas(TENANT_VET, "")).thenReturn(1L);

        MascotaPageResponseDTO result = mascotaService.listar(0, 15, "TODOS", "");

        assertThat(result.getContent()).hasSize(2);
        verify(mascotaRepository, times(1)).findPaginadoTodos(eq(TENANT_VET), eq(""), any());
        verify(mascotaRepository, never()).findPaginadoActivas(any(), any(), any());
        verify(mascotaRepository, never()).findPaginadoInactivas(any(), any(), any());
    }

    // ── V: listar(INACTIVAS) → llama findPaginadoInactivas ───────────────────

    @Test
    @DisplayName("V — listar(INACTIVAS) llama findPaginadoInactivas")
    void listar_inactivas_llamaFindPaginadoInactivas() {
        TenantContext.setCurrentTenant(TENANT_VET);
        MascotaResumenDTO inactiva = mockResumenInactiva(99L, CLIENTE_10, "Pedro Pérez", "Luna", "GATO");
        when(mascotaRepository.findPaginadoInactivas(eq(TENANT_VET), eq(""), any()))
                .thenReturn(mockPage(List.of(inactiva)));
        when(mascotaRepository.countResumenTotal(TENANT_VET, "")).thenReturn(2L);
        when(mascotaRepository.countResumenActivas(TENANT_VET, "")).thenReturn(1L);

        MascotaPageResponseDTO result = mascotaService.listar(0, 15, "INACTIVAS", "");

        assertThat(result.getContent()).hasSize(1);
        assertThat(result.getContent().get(0).getActivo()).isFalse();
        verify(mascotaRepository, times(1)).findPaginadoInactivas(eq(TENANT_VET), eq(""), any());
        verify(mascotaRepository, never()).findPaginadoActivas(any(), any(), any());
        verify(mascotaRepository, never()).findPaginadoTodos(any(), any(), any());
    }

    // ── W: listar(TODOS) aislamiento de tenant ───────────────────────────────

    @Test
    @DisplayName("W — listar(TODOS) solo consulta el tenant activo, no otros")
    void listar_todos_tenantIsolation() {
        TenantContext.setCurrentTenant(TENANT_VET);
        MascotaResumenDTO resumenVet = mockResumen(MASCOTA_50, CLIENTE_10, "Pedro Pérez", "Toby", "PERRO");
        when(mascotaRepository.findPaginadoTodos(eq(TENANT_VET), eq(""), any()))
                .thenReturn(mockPage(List.of(resumenVet)));
        when(mascotaRepository.countResumenTotal(TENANT_VET, "")).thenReturn(1L);
        when(mascotaRepository.countResumenActivas(TENANT_VET, "")).thenReturn(1L);

        MascotaPageResponseDTO result = mascotaService.listar(0, 15, "TODOS", "");

        assertThat(result.getContent()).hasSize(1);
        assertThat(result.getContent().get(0).getNombre()).isEqualTo("Toby");
        verify(mascotaRepository, never()).findPaginadoTodos(eq(TENANT_JUAN), any(), any());
    }

    // ── X: sexo y peso en proyección resumen ─────────────────────────────────

    @Test
    @DisplayName("X — listar(ACTIVAS) incluye sexo y pesoKg en la proyección")
    void listar_activas_incluyeSexoYPeso() {
        TenantContext.setCurrentTenant(TENANT_VET);
        MascotaResumenDTO resumen = mockResumenConPeso(MASCOTA_50, CLIENTE_10,
                "Pedro Pérez", "Toby", "PERRO", "MACHO", new java.math.BigDecimal("12.50"));
        when(mascotaRepository.findPaginadoActivas(eq(TENANT_VET), eq(""), any()))
                .thenReturn(mockPage(List.of(resumen)));
        when(mascotaRepository.countResumenTotal(TENANT_VET, "")).thenReturn(1L);
        when(mascotaRepository.countResumenActivas(TENANT_VET, "")).thenReturn(1L);

        MascotaPageResponseDTO result = mascotaService.listar(0, 15, "ACTIVAS", "");

        assertThat(result.getContent().get(0).getSexo()).isEqualTo("MACHO");
        assertThat(result.getContent().get(0).getPesoKg()).isEqualByComparingTo("12.50");
    }

    // ══ Tests de paginación A-R ═══════════════════════════════════════════════

    // ── pag-A: página 0, size 15 ─────────────────────────────────────────────

    @Test
    @DisplayName("pag-A — page=0 size=15 → pageResult.page=0, size=15")
    void pag_pagina0_size15() {
        TenantContext.setCurrentTenant(TENANT_VET);
        when(mascotaRepository.findPaginadoActivas(eq(TENANT_VET), eq(""), any()))
                .thenReturn(mockPage(List.of()));
        when(mascotaRepository.countResumenTotal(TENANT_VET, "")).thenReturn(0L);
        when(mascotaRepository.countResumenActivas(TENANT_VET, "")).thenReturn(0L);

        MascotaPageResponseDTO result = mascotaService.listar(0, 15, "ACTIVAS", "");

        assertThat(result.getPage()).isEqualTo(0);
        assertThat(result.getSize()).isEqualTo(15);
    }

    // ── pag-B: segunda página ─────────────────────────────────────────────────

    @Test
    @DisplayName("pag-B — page=1 → pageResult.page=1")
    void pag_segundaPagina() {
        TenantContext.setCurrentTenant(TENANT_VET);
        when(mascotaRepository.findPaginadoActivas(eq(TENANT_VET), eq(""), any()))
                .thenReturn(mockPage(List.of()));
        when(mascotaRepository.countResumenTotal(TENANT_VET, "")).thenReturn(0L);
        when(mascotaRepository.countResumenActivas(TENANT_VET, "")).thenReturn(0L);

        MascotaPageResponseDTO result = mascotaService.listar(1, 15, "ACTIVAS", "");

        assertThat(result.getPage()).isEqualTo(1);
    }

    // ── pag-C: totalPages correcto ────────────────────────────────────────────

    @Test
    @DisplayName("pag-C — 45 elementos, size 15 → totalPages=3")
    void pag_totalPages_correcto() {
        TenantContext.setCurrentTenant(TENANT_VET);
        PageRequest pr = PageRequest.of(0, 15, Sort.by("nombre"));
        Page<MascotaResumenDTO> page45 = new PageImpl<>(List.of(), pr, 45L);
        when(mascotaRepository.findPaginadoActivas(eq(TENANT_VET), eq(""), any()))
                .thenReturn(page45);
        when(mascotaRepository.countResumenTotal(TENANT_VET, "")).thenReturn(45L);
        when(mascotaRepository.countResumenActivas(TENANT_VET, "")).thenReturn(45L);

        MascotaPageResponseDTO result = mascotaService.listar(0, 15, "ACTIVAS", "");

        assertThat(result.getTotalPages()).isEqualTo(3);
        assertThat(result.getTotalElements()).isEqualTo(45L);
    }

    // ── pag-D: totalElements correcto ─────────────────────────────────────────

    @Test
    @DisplayName("pag-D — totalElements proviene de la query paginada")
    void pag_totalElements_correcto() {
        TenantContext.setCurrentTenant(TENANT_VET);
        Page<MascotaResumenDTO> page100 = new PageImpl<>(List.of(),
                PageRequest.of(0, 15, Sort.by("nombre")), 100L);
        when(mascotaRepository.findPaginadoActivas(eq(TENANT_VET), eq(""), any()))
                .thenReturn(page100);
        when(mascotaRepository.countResumenTotal(TENANT_VET, "")).thenReturn(120L);
        when(mascotaRepository.countResumenActivas(TENANT_VET, "")).thenReturn(100L);

        MascotaPageResponseDTO result = mascotaService.listar(0, 15, "ACTIVAS", "");

        assertThat(result.getTotalElements()).isEqualTo(100L);
    }

    // ── pag-E: estado ACTIVAS ─────────────────────────────────────────────────

    @Test
    @DisplayName("pag-E — estado=ACTIVAS → solo findPaginadoActivas")
    void pag_estado_ACTIVAS() {
        TenantContext.setCurrentTenant(TENANT_VET);
        when(mascotaRepository.findPaginadoActivas(eq(TENANT_VET), eq(""), any()))
                .thenReturn(mockPage(List.of()));
        when(mascotaRepository.countResumenTotal(any(), any())).thenReturn(0L);
        when(mascotaRepository.countResumenActivas(any(), any())).thenReturn(0L);

        mascotaService.listar(0, 15, "ACTIVAS", "");

        verify(mascotaRepository, times(1)).findPaginadoActivas(eq(TENANT_VET), eq(""), any());
        verify(mascotaRepository, never()).findPaginadoTodos(any(), any(), any());
        verify(mascotaRepository, never()).findPaginadoInactivas(any(), any(), any());
    }

    // ── pag-F: estado INACTIVAS ───────────────────────────────────────────────

    @Test
    @DisplayName("pag-F — estado=INACTIVAS → solo findPaginadoInactivas")
    void pag_estado_INACTIVAS() {
        TenantContext.setCurrentTenant(TENANT_VET);
        when(mascotaRepository.findPaginadoInactivas(eq(TENANT_VET), eq(""), any()))
                .thenReturn(mockPage(List.of()));
        when(mascotaRepository.countResumenTotal(any(), any())).thenReturn(0L);
        when(mascotaRepository.countResumenActivas(any(), any())).thenReturn(0L);

        mascotaService.listar(0, 15, "INACTIVAS", "");

        verify(mascotaRepository, times(1)).findPaginadoInactivas(eq(TENANT_VET), eq(""), any());
        verify(mascotaRepository, never()).findPaginadoActivas(any(), any(), any());
        verify(mascotaRepository, never()).findPaginadoTodos(any(), any(), any());
    }

    // ── pag-G: estado TODOS ───────────────────────────────────────────────────

    @Test
    @DisplayName("pag-G — estado=TODOS → solo findPaginadoTodos")
    void pag_estado_TODOS() {
        TenantContext.setCurrentTenant(TENANT_VET);
        when(mascotaRepository.findPaginadoTodos(eq(TENANT_VET), eq(""), any()))
                .thenReturn(mockPage(List.of()));
        when(mascotaRepository.countResumenTotal(any(), any())).thenReturn(0L);
        when(mascotaRepository.countResumenActivas(any(), any())).thenReturn(0L);

        mascotaService.listar(0, 15, "TODOS", "");

        verify(mascotaRepository, times(1)).findPaginadoTodos(eq(TENANT_VET), eq(""), any());
        verify(mascotaRepository, never()).findPaginadoActivas(any(), any(), any());
        verify(mascotaRepository, never()).findPaginadoInactivas(any(), any(), any());
    }

    // ── pag-H: search pasa el término al repositorio ──────────────────────────

    @Test
    @DisplayName("pag-H — search='toby' → repositorio recibe 'toby' en minúsculas")
    void pag_search_pasaTerminoAlRepositorio() {
        TenantContext.setCurrentTenant(TENANT_VET);
        MascotaResumenDTO r = mockResumen(MASCOTA_50, CLIENTE_10, "Pedro", "Toby", "PERRO");
        when(mascotaRepository.findPaginadoActivas(eq(TENANT_VET), eq("toby"), any()))
                .thenReturn(mockPage(List.of(r)));
        when(mascotaRepository.countResumenTotal(TENANT_VET, "toby")).thenReturn(1L);
        when(mascotaRepository.countResumenActivas(TENANT_VET, "toby")).thenReturn(1L);

        MascotaPageResponseDTO result = mascotaService.listar(0, 15, "ACTIVAS", "toby");

        assertThat(result.getContent()).hasSize(1);
        verify(mascotaRepository).findPaginadoActivas(eq(TENANT_VET), eq("toby"), any());
    }

    // ── pag-I: search con mayúsculas se normaliza a minúsculas ───────────────

    @Test
    @DisplayName("pag-I — search='TOBY' → repositorio recibe 'toby' (normalizado)")
    void pag_search_normalizadoAMinusculas() {
        TenantContext.setCurrentTenant(TENANT_VET);
        when(mascotaRepository.findPaginadoActivas(eq(TENANT_VET), eq("toby"), any()))
                .thenReturn(mockPage(List.of()));
        when(mascotaRepository.countResumenTotal(TENANT_VET, "toby")).thenReturn(0L);
        when(mascotaRepository.countResumenActivas(TENANT_VET, "toby")).thenReturn(0L);

        mascotaService.listar(0, 15, "ACTIVAS", "TOBY");

        verify(mascotaRepository).findPaginadoActivas(eq(TENANT_VET), eq("toby"), any());
    }

    // ── pag-J: search vacío pasa string vacío ────────────────────────────────

    @Test
    @DisplayName("pag-J — search=null → repositorio recibe '' (vacío)")
    void pag_searchNull_pasaVacio() {
        TenantContext.setCurrentTenant(TENANT_VET);
        when(mascotaRepository.findPaginadoActivas(eq(TENANT_VET), eq(""), any()))
                .thenReturn(mockPage(List.of()));
        when(mascotaRepository.countResumenTotal(TENANT_VET, "")).thenReturn(0L);
        when(mascotaRepository.countResumenActivas(TENANT_VET, "")).thenReturn(0L);

        mascotaService.listar(0, 15, "ACTIVAS", null);

        verify(mascotaRepository).findPaginadoActivas(eq(TENANT_VET), eq(""), any());
    }

    // ── pag-K: search + estado combinados ────────────────────────────────────

    @Test
    @DisplayName("pag-K — search='luna' + estado=INACTIVAS → findPaginadoInactivas con 'luna'")
    void pag_searchMasEstado() {
        TenantContext.setCurrentTenant(TENANT_VET);
        MascotaResumenDTO inactiva = mockResumenInactiva(99L, CLIENTE_10, "Pedro", "Luna", "GATO");
        when(mascotaRepository.findPaginadoInactivas(eq(TENANT_VET), eq("luna"), any()))
                .thenReturn(mockPage(List.of(inactiva)));
        when(mascotaRepository.countResumenTotal(TENANT_VET, "luna")).thenReturn(2L);
        when(mascotaRepository.countResumenActivas(TENANT_VET, "luna")).thenReturn(1L);

        MascotaPageResponseDTO result = mascotaService.listar(0, 15, "INACTIVAS", "luna");

        assertThat(result.getContent()).hasSize(1);
        assertThat(result.getContent().get(0).getActivo()).isFalse();
        verify(mascotaRepository).findPaginadoInactivas(eq(TENANT_VET), eq("luna"), any());
    }

    // ── pag-L: resumen refleja búsqueda (independiente del estado filtrado) ───

    @Test
    @DisplayName("pag-L — resumen total/activas/inactivas vienen de countResumen*, no de la página")
    void pag_resumen_vieneDeCounts() {
        TenantContext.setCurrentTenant(TENANT_VET);
        when(mascotaRepository.findPaginadoActivas(eq(TENANT_VET), eq(""), any()))
                .thenReturn(mockPage(List.of()));
        when(mascotaRepository.countResumenTotal(TENANT_VET, "")).thenReturn(30L);
        when(mascotaRepository.countResumenActivas(TENANT_VET, "")).thenReturn(20L);

        MascotaPageResponseDTO result = mascotaService.listar(0, 15, "ACTIVAS", "");

        assertThat(result.getResumen().getTotal()).isEqualTo(30L);
        assertThat(result.getResumen().getActivas()).isEqualTo(20L);
        assertThat(result.getResumen().getInactivas()).isEqualTo(10L);
    }

    // ── pag-M: tenant isolation ───────────────────────────────────────────────

    @Test
    @DisplayName("pag-M — listar solo consulta el tenantId activo, no otros tenants")
    void pag_tenantIsolation() {
        TenantContext.setCurrentTenant(TENANT_VET);
        when(mascotaRepository.findPaginadoActivas(eq(TENANT_VET), eq(""), any()))
                .thenReturn(mockPage(List.of()));
        when(mascotaRepository.countResumenTotal(TENANT_VET, "")).thenReturn(0L);
        when(mascotaRepository.countResumenActivas(TENANT_VET, "")).thenReturn(0L);

        mascotaService.listar(0, 15, "ACTIVAS", "");

        verify(mascotaRepository, never()).findPaginadoActivas(eq(TENANT_JUAN), any(), any());
        verify(mascotaRepository, never()).findPaginadoTodos(eq(TENANT_JUAN), any(), any());
        verify(mascotaRepository, never()).countResumenTotal(eq(TENANT_JUAN), any());
    }

    // ── pag-N: tenant sin mascotas → contenido vacío, totales en 0 ───────────

    @Test
    @DisplayName("pag-N — tenant vacío → contenido vacío y resumen en cero")
    void pag_tenantVacio() {
        TenantContext.setCurrentTenant(TENANT_VET);
        when(mascotaRepository.findPaginadoActivas(eq(TENANT_VET), eq(""), any()))
                .thenReturn(mockPage(List.of()));
        when(mascotaRepository.countResumenTotal(TENANT_VET, "")).thenReturn(0L);
        when(mascotaRepository.countResumenActivas(TENANT_VET, "")).thenReturn(0L);

        MascotaPageResponseDTO result = mascotaService.listar(0, 15, "ACTIVAS", "");

        assertThat(result.getContent()).isEmpty();
        assertThat(result.getTotalElements()).isEqualTo(0L);
        assertThat(result.getResumen().getTotal()).isEqualTo(0L);
        assertThat(result.getResumen().getActivas()).isEqualTo(0L);
        assertThat(result.getResumen().getInactivas()).isEqualTo(0L);
    }

    // ── pag-O: mascota inactiva visible en TODOS ──────────────────────────────

    @Test
    @DisplayName("pag-O — TODOS incluye mascotas inactivas en el contenido")
    void pag_todos_incluyeInactivas() {
        TenantContext.setCurrentTenant(TENANT_VET);
        MascotaResumenDTO activa   = mockResumen(MASCOTA_50, CLIENTE_10, "Pedro", "Toby", "PERRO");
        MascotaResumenDTO inactiva = mockResumenInactiva(99L, CLIENTE_10, "Pedro", "Luna", "GATO");
        when(mascotaRepository.findPaginadoTodos(eq(TENANT_VET), eq(""), any()))
                .thenReturn(mockPage(List.of(activa, inactiva)));
        when(mascotaRepository.countResumenTotal(TENANT_VET, "")).thenReturn(2L);
        when(mascotaRepository.countResumenActivas(TENANT_VET, "")).thenReturn(1L);

        MascotaPageResponseDTO result = mascotaService.listar(0, 15, "TODOS", "");

        assertThat(result.getContent()).extracting(MascotaResumenDTO::getActivo)
                .containsExactlyInAnyOrder(true, false);
    }

    // ── pag-P: mascota inactiva visible en INACTIVAS ──────────────────────────

    @Test
    @DisplayName("pag-P — INACTIVAS devuelve solo activo=false")
    void pag_inactivas_soloInactivas() {
        TenantContext.setCurrentTenant(TENANT_VET);
        MascotaResumenDTO inactiva = mockResumenInactiva(99L, CLIENTE_10, "Pedro", "Luna", "GATO");
        when(mascotaRepository.findPaginadoInactivas(eq(TENANT_VET), eq(""), any()))
                .thenReturn(mockPage(List.of(inactiva)));
        when(mascotaRepository.countResumenTotal(TENANT_VET, "")).thenReturn(2L);
        when(mascotaRepository.countResumenActivas(TENANT_VET, "")).thenReturn(1L);

        MascotaPageResponseDTO result = mascotaService.listar(0, 15, "INACTIVAS", "");

        assertThat(result.getContent()).hasSize(1);
        assertThat(result.getContent().get(0).getActivo()).isFalse();
    }

    // ── pag-Q: ACTIVAS no devuelve inactivas ──────────────────────────────────

    @Test
    @DisplayName("pag-Q — ACTIVAS no llama findPaginadoTodos ni findPaginadoInactivas")
    void pag_activas_noLlamaTodosNiInactivas() {
        TenantContext.setCurrentTenant(TENANT_VET);
        when(mascotaRepository.findPaginadoActivas(eq(TENANT_VET), eq(""), any()))
                .thenReturn(mockPage(List.of()));
        when(mascotaRepository.countResumenTotal(TENANT_VET, "")).thenReturn(3L);
        when(mascotaRepository.countResumenActivas(TENANT_VET, "")).thenReturn(2L);

        MascotaPageResponseDTO result = mascotaService.listar(0, 15, "ACTIVAS", "");

        verify(mascotaRepository, never()).findPaginadoTodos(any(), any(), any());
        verify(mascotaRepository, never()).findPaginadoInactivas(any(), any(), any());
        // resumen sigue mostrando inactivas aunque el estado sea ACTIVAS
        assertThat(result.getResumen().getInactivas()).isEqualTo(1L);
    }

    // ── pag-R: no N+1 — clienteRepository no se llama ────────────────────────

    @Test
    @DisplayName("pag-R — listar con 50 mascotas no itera clienteRepository (sin N+1)")
    void pag_sinNmas1_clienteRepositoryNuncaLlamado() {
        TenantContext.setCurrentTenant(TENANT_VET);
        List<MascotaResumenDTO> cincuenta = new java.util.ArrayList<>();
        for (int i = 0; i < 50; i++) {
            int fi = i;
            cincuenta.add(mockResumen((long) i, CLIENTE_10, "Pedro", "Mascota" + fi, "PERRO"));
        }
        when(mascotaRepository.findPaginadoActivas(eq(TENANT_VET), eq(""), any()))
                .thenReturn(mockPage(cincuenta));
        when(mascotaRepository.countResumenTotal(TENANT_VET, "")).thenReturn(50L);
        when(mascotaRepository.countResumenActivas(TENANT_VET, "")).thenReturn(50L);

        mascotaService.listar(0, 15, "ACTIVAS", "");

        verify(clienteRepository, never()).findByIdAndTenantId(any(), any());
        verify(clienteRepository, never()).findById(any());
        verify(clienteRepository, never()).findAll();
    }

    // ── helpers ───────────────────────────────────────────────────────────────

    private Page<MascotaResumenDTO> mockPage(List<MascotaResumenDTO> content) {
        return new PageImpl<>(content, PageRequest.of(0, 15, Sort.by("nombre")), content.size());
    }

    private MascotaResumenDTO mockResumen(Long id, Long clienteId, String clienteNombre,
                                          String nombre, String especie) {
        return new MascotaResumenDTO() {
            public Long            getId()            { return id; }
            public Long            getClienteId()     { return clienteId; }
            public String          getClienteNombre() { return clienteNombre; }
            public String          getNombre()        { return nombre; }
            public String          getEspecie()       { return especie; }
            public String          getRaza()          { return null; }
            public String          getSexo()          { return null; }
            public java.math.BigDecimal getPesoKg()   { return null; }
            public Boolean         getActivo()        { return true; }
        };
    }

    private MascotaResumenDTO mockResumenInactiva(Long id, Long clienteId, String clienteNombre,
                                                  String nombre, String especie) {
        return new MascotaResumenDTO() {
            public Long            getId()            { return id; }
            public Long            getClienteId()     { return clienteId; }
            public String          getClienteNombre() { return clienteNombre; }
            public String          getNombre()        { return nombre; }
            public String          getEspecie()       { return especie; }
            public String          getRaza()          { return null; }
            public String          getSexo()          { return null; }
            public java.math.BigDecimal getPesoKg()   { return null; }
            public Boolean         getActivo()        { return false; }
        };
    }

    private MascotaResumenDTO mockResumenConPeso(Long id, Long clienteId, String clienteNombre,
                                                 String nombre, String especie,
                                                 String sexo, java.math.BigDecimal pesoKg) {
        return new MascotaResumenDTO() {
            public Long            getId()            { return id; }
            public Long            getClienteId()     { return clienteId; }
            public String          getClienteNombre() { return clienteNombre; }
            public String          getNombre()        { return nombre; }
            public String          getEspecie()       { return especie; }
            public String          getRaza()          { return null; }
            public String          getSexo()          { return sexo; }
            public java.math.BigDecimal getPesoKg()   { return pesoKg; }
            public Boolean         getActivo()        { return true; }
        };
    }
}
