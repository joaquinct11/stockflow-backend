package com.stockflow.service;

import com.stockflow.util.TenantContext;
import com.stockflow.dto.EquipoClienteDTO;
import com.stockflow.entity.Cliente;
import com.stockflow.entity.EquipoCliente;
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

import java.time.LocalDateTime;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.doNothing;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class EquipoCrossTenantTest {

    private static final String TENANT_A = "tenant-a-11";
    private static final String TENANT_B = "tenant-b-22";
    private static final Long   EQUIPO_5 = 5L;

    @Mock private EquipoClienteRepository equipoRepository;
    @Mock private ClienteRepository clienteRepository;
    @Mock private EquipoClienteMapper mapper;
    @Mock private TenantService tenantService;

    @InjectMocks private EquipoClienteServiceImpl service;

    private EquipoCliente equipoTenantA;

    @BeforeEach
    void setUp() {
        doNothing().when(tenantService).requireRubro(anyString(), anyString());

        Cliente clienteA = Cliente.builder().id(1L).tenantId(TENANT_A).nombre("Cliente A").build();

        equipoTenantA = EquipoCliente.builder()
                .id(EQUIPO_5)
                .tenantId(TENANT_A)
                .cliente(clienteA)
                .nombre("Compresor")
                .activo(true)
                .createdAt(LocalDateTime.now())
                .build();

        EquipoClienteDTO dto = EquipoClienteDTO.builder().id(EQUIPO_5).tenantId(TENANT_A).build();
        when(mapper.toDTO(any(EquipoCliente.class))).thenReturn(dto);
    }

    @AfterEach
    void tearDown() {
        TenantContext.clear();
    }

    @Test
    void obtenerPorId_tenantPropietario_devuelveEquipo() {
        TenantContext.setCurrentTenant(TENANT_A);
        when(equipoRepository.findByIdAndTenantId(EQUIPO_5, TENANT_A)).thenReturn(Optional.of(equipoTenantA));

        EquipoClienteDTO dto = service.obtenerPorId(EQUIPO_5);

        assertThat(dto.getId()).isEqualTo(EQUIPO_5);
    }

    @Test
    void obtenerPorId_otroTenant_noEncuentraEquipo() {
        TenantContext.setCurrentTenant(TENANT_B);
        when(equipoRepository.findByIdAndTenantId(EQUIPO_5, TENANT_B)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> service.obtenerPorId(EQUIPO_5))
                .isInstanceOf(ResourceNotFoundException.class);
    }

    @Test
    void activar_otroTenant_noAfectaEquipoAjeno() {
        TenantContext.setCurrentTenant(TENANT_B);
        when(equipoRepository.findByIdAndTenantId(EQUIPO_5, TENANT_B)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> service.activar(EQUIPO_5))
                .isInstanceOf(ResourceNotFoundException.class);

        assertThat(equipoTenantA.getActivo()).isTrue();
    }

    @Test
    void desactivar_otroTenant_noAfectaEquipoAjeno() {
        TenantContext.setCurrentTenant(TENANT_B);
        when(equipoRepository.findByIdAndTenantId(EQUIPO_5, TENANT_B)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> service.desactivar(EQUIPO_5))
                .isInstanceOf(ResourceNotFoundException.class);

        assertThat(equipoTenantA.getActivo()).isTrue();
    }
}
